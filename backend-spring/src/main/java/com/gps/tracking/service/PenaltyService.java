package com.gps.tracking.service;

import com.gps.tracking.dto.*;
import com.gps.tracking.entity.*;
import com.gps.tracking.exception.ValidationException;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PenaltyService {

    private final AlertPenaltyConfigRepository configRepo;
    private final DriverPenaltyRepository penaltyRepo;
    private final PenaltyAuditLogRepository auditRepo;
    private final DriverRepository driverRepo;
    private final AlertRepository alertRepo;
    private final TripRepository tripRepo;
    private final VehicleRepository vehicleRepo;

    // Per-driver synchronization locks to prevent concurrent alerts from getting identical occurrence numbers
    private final ConcurrentHashMap<Long, Object> driverLockMap = new ConcurrentHashMap<>();

    // Default base penalties as specified in requirements
    private static final Map<String, BigDecimal> DEFAULT_BASE_PENALTIES = Map.of(
            "Harsh Braking Alert", BigDecimal.valueOf(50.00),
            "Over-speeding Alert", BigDecimal.valueOf(100.00),
            "Harsh Acceleration Alert", BigDecimal.valueOf(50.00),
            "GPS Disconnect Alert", BigDecimal.valueOf(0.00),
            "Night Driving Alert", BigDecimal.valueOf(100.00),
            "Ignition Alert", BigDecimal.valueOf(0.00),
            "Fatigue Alert", BigDecimal.valueOf(200.00)
    );

    private static final BigDecimal DEFAULT_INCREMENT = BigDecimal.valueOf(30.00);
    private static final int DEFAULT_WARNING_THRESHOLD = 2;

    // --- CONFIGURATIONS ---

    @Transactional(readOnly = true)
    public List<AlertPenaltyConfigDTO> getAlertPenaltyConfigs() {
        return configRepo.findAll().stream()
                .map(this::toConfigDTO)
                .collect(Collectors.toList());
    }

    public AlertPenaltyConfigDTO updateAlertPenaltyConfig(Long id, BigDecimal weight, BigDecimal defaultFine,
                                                          BigDecimal penaltyIncrement, Integer warningThreshold,
                                                          Boolean penaltyEnabled, String user) {
        AlertPenaltyConfig config = configRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AlertPenaltyConfig", id));

        if (weight != null) {
            if (weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ValidationException("Penalty weight must be between 0 and 100");
            }
            config.setWeight(weight.setScale(2, RoundingMode.HALF_UP));
        }

        if (defaultFine != null) {
            if (defaultFine.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Default fine must be non-negative");
            }
            config.setDefaultFineAmount(defaultFine.setScale(2, RoundingMode.HALF_UP));
        }

        if (penaltyIncrement != null) {
            if (penaltyIncrement.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Penalty increment must be non-negative");
            }
            config.setPenaltyIncrement(penaltyIncrement.setScale(2, RoundingMode.HALF_UP));
        }

        if (warningThreshold != null) {
            if (warningThreshold < 0) {
                throw new ValidationException("Warning threshold must be non-negative");
            }
            config.setWarningThreshold(warningThreshold);
        }

        if (penaltyEnabled != null) {
            config.setPenaltyEnabled(penaltyEnabled);
        }

        config.setUpdatedBy(user != null ? user : "Fleet Manager");
        config.setUpdatedAt(LocalDateTime.now());
        return toConfigDTO(configRepo.save(config));
    }

    // Overload for backward compatibility with existing controller
    public AlertPenaltyConfigDTO updateAlertPenaltyConfig(Long id, BigDecimal weight, BigDecimal defaultFine, String user) {
        return updateAlertPenaltyConfig(id, weight, defaultFine, null, null, null, user);
    }

    // --- PROGRESSIVE ALERT PROCESSING ---

    /**
     * Process an alert for progressive penalties idempotently.
     * Evaluates each alert type (handling combined comma-separated types).
     * Calculates occurrences strictly per driver and alert type.
     * Records warning (₹0) for occurrences 1-2, progressive fines for occurrences 3+.
     */
    public List<DriverPenalty> processAlertPenalty(Alert alert) {
        if (alert == null || alert.getId() == null) {
            return Collections.emptyList();
        }

        // Never charge penalties on historical or silenced alerts
        if ("HISTORICAL".equalsIgnoreCase(alert.getStatus()) || "SILENCED".equalsIgnoreCase(alert.getDeliveryStatus())) {
            log.info("Alert {} is historical or silenced. Skipping progressive penalty processing.", alert.getId());
            return Collections.emptyList();
        }

        Driver driver = alert.getDriver();
        if (driver == null && alert.getTrip() != null) {
            driver = alert.getTrip().getDriver();
        }
        if (driver == null) {
            log.info("Alert {} has no assigned driver. Skipping driver progressive penalty processing.", alert.getId());
            return Collections.emptyList();
        }

        List<String> distinctAlertTypes = splitAndNormalizeAlertTypes(alert.getAlertType());
        List<DriverPenalty> processedPenalties = new ArrayList<>();

        Object lock = driverLockMap.computeIfAbsent(driver.getId(), k -> new Object());
        synchronized (lock) {
            for (String alertType : distinctAlertTypes) {
                // Idempotency: Check if already processed for this alert and alert type
                Optional<DriverPenalty> existing = penaltyRepo.findByAlertIdAndAlertType(alert.getId(), alertType);
                if (existing.isPresent()) {
                    log.info("Alert {} with type {} has already been processed for penalty (Penalty ID: {}). Idempotency preserved.",
                            alert.getId(), alertType, existing.get().getId());
                    processedPenalties.add(existing.get());
                    continue;
                }

                // Count prior occurrences for this driver and alert type (excluding REVOKED)
                long priorCount = penaltyRepo.countByDriverIdAndAlertTypeAndStatusNot(driver.getId(), alertType, "REVOKED");
                int occurrenceNumber = (int) priorCount + 1;

                // Retrieve configuration for this alert type or use default
                AlertPenaltyConfig config = resolveConfigForType(alertType);
                BigDecimal basePenalty = config.getDefaultFineAmount().setScale(2, RoundingMode.HALF_UP);
                BigDecimal increment = config.getPenaltyIncrement() != null
                        ? config.getPenaltyIncrement().setScale(2, RoundingMode.HALF_UP)
                        : DEFAULT_INCREMENT.setScale(2, RoundingMode.HALF_UP);
                int warningThreshold = config.getWarningThreshold() != null
                        ? config.getWarningThreshold()
                        : DEFAULT_WARNING_THRESHOLD;
                boolean isEnabled = Boolean.TRUE.equals(config.getPenaltyEnabled());

                // Progressive Formula:
                // If occurrence <= warningThreshold OR basePenalty == 0 OR !isEnabled:
                //   Warning, ₹0 penalty
                // If occurrence > warningThreshold AND basePenalty > 0:
                //   Penalty = basePenalty + (occurrence - warningThreshold - 1) * increment
                String classification;
                BigDecimal fineAmount;
                String penaltyType;

                if (!isEnabled || basePenalty.compareTo(BigDecimal.ZERO) == 0 || occurrenceNumber <= warningThreshold) {
                    classification = "WARNING";
                    fineAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    penaltyType = "WARNING";
                } else {
                    classification = "PENALTY";
                    int penaltyStep = occurrenceNumber - warningThreshold - 1; // e.g. 3rd occurrence -> step 0, 4th -> step 1
                    fineAmount = basePenalty.add(BigDecimal.valueOf(penaltyStep).multiply(increment))
                            .setScale(2, RoundingMode.HALF_UP);
                    penaltyType = "FINANCIAL_FINE";
                }

                String reason = String.format("Progressive %s (Occurrence #%d) for %s [Base: Rs. %.2f, Inc: Rs. %.2f]",
                        classification, occurrenceNumber, alertType, basePenalty.doubleValue(), increment.doubleValue());

                DriverPenalty penalty = DriverPenalty.builder()
                        .driver(driver)
                        .alert(alert)
                        .trip(alert.getTrip())
                        .vehicle(alert.getVehicle())
                        .penaltyType(penaltyType)
                        .alertType(alertType)
                        .occurrenceNumber(occurrenceNumber)
                        .classification(classification)
                        .basePenalty(basePenalty)
                        .penaltyIncrement(increment)
                        .calculationTimestamp(LocalDateTime.now())
                        .processingStatus(Boolean.TRUE.equals(alert.getIsSimulation()) ? "SIMULATED" : "COMPLETED")
                        .notificationStatus(Boolean.TRUE.equals(alert.getIsSimulation()) ? "SIMULATED_BYPASS" : "PENDING")
                        .reason(reason)
                        .penaltyPoints(BigDecimal.ZERO)
                        .monetaryAmount(fineAmount)
                        .dateApplied(alert.getOccurredAt() != null ? alert.getOccurredAt() : LocalDateTime.now())
                        .appliedBy("Automated Progressive Penalty System")
                        .status("ACTIVE")
                        .deductFromScore(false)
                        .isSimulation(Boolean.TRUE.equals(alert.getIsSimulation()))
                        .notes(alert.getMessage())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                DriverPenalty saved = penaltyRepo.save(penalty);
                processedPenalties.add(saved);

                // Audit log
                PenaltyAuditLog audit = PenaltyAuditLog.builder()
                        .penalty(saved)
                        .action("CREATED")
                        .changedBy("Automated Progressive Penalty System")
                        .reason(reason)
                        .oldStatus(null)
                        .newStatus("ACTIVE")
                        .details(String.format("Calculated %s occurrence #%d with fine Rs. %.2f", classification, occurrenceNumber, fineAmount.doubleValue()))
                        .createdAt(LocalDateTime.now())
                        .build();
                auditRepo.save(audit);

                log.info("Processed progressive penalty for Driver {} ({}): Occurrence #{}, Classification={}, Fine=Rs. {}",
                        driver.getCode(), alertType, occurrenceNumber, classification, fineAmount);
            }
        }

        return processedPenalties;
    }

    public AlertPenaltyConfig resolveConfigForType(String alertType) {
        String normalized = normalizeAlertType(alertType);
        return configRepo.findByAlertTypeIgnoreCase(normalized)
                .orElseGet(() -> {
                    BigDecimal defBase = DEFAULT_BASE_PENALTIES.getOrDefault(normalized, BigDecimal.ZERO);
                    return AlertPenaltyConfig.builder()
                            .alertType(normalized)
                            .weight(BigDecimal.valueOf(5.0))
                            .defaultFineAmount(defBase.setScale(2, RoundingMode.HALF_UP))
                            .penaltyIncrement(DEFAULT_INCREMENT.setScale(2, RoundingMode.HALF_UP))
                            .warningThreshold(DEFAULT_WARNING_THRESHOLD)
                            .penaltyEnabled(true)
                            .description("Default progressive penalty configuration for " + normalized)
                            .isActive(true)
                            .updatedBy("SYSTEM")
                            .updatedAt(LocalDateTime.now())
                            .build();
                });
    }

    public List<String> splitAndNormalizeAlertTypes(String rawAlertTypes) {
        if (rawAlertTypes == null || rawAlertTypes.trim().isEmpty()) {
            return List.of("Unknown Alert");
        }
        String[] parts = rawAlertTypes.split(",");
        Set<String> distinct = new LinkedHashSet<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                distinct.add(normalizeAlertType(trimmed));
            }
        }
        return distinct.isEmpty() ? List.of("Unknown Alert") : new ArrayList<>(distinct);
    }

    public String normalizeAlertType(String type) {
        if (type == null || type.isBlank()) return "Unknown Alert";
        String lower = type.trim().toLowerCase();
        if (lower.contains("harsh") && lower.contains("brak")) {
            return "Harsh Braking Alert";
        }
        if (lower.contains("overspeed") || lower.contains("over-speed")) {
            return "Over-speeding Alert";
        }
        if (lower.contains("harsh") && lower.contains("accel")) {
            return "Harsh Acceleration Alert";
        }
        if (lower.contains("gps") && lower.contains("disconnect")) {
            return "GPS Disconnect Alert";
        }
        if (lower.contains("night") && lower.contains("driv")) {
            return "Night Driving Alert";
        }
        if (lower.contains("ignition")) {
            return "Ignition Alert";
        }
        if (lower.contains("fatigue")) {
            return "Fatigue Alert";
        }
        return type.trim();
    }

    // --- DRIVER PENALTY ACCOUNTING & HISTORY ---

    @Transactional(readOnly = true)
    public DriverPenaltySummaryDTO getDriverPenaltySummary(Long driverId) {
        Driver driver = driverRepo.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));

        List<DriverPenalty> allPenalties = penaltyRepo.findByDriverIdOrderByDateAppliedDesc(driverId);

        long warningCount = allPenalties.stream()
                .filter(p -> "WARNING".equalsIgnoreCase(p.getClassification()) && !"REVOKED".equalsIgnoreCase(p.getStatus()))
                .count();

        long penaltyCount = allPenalties.stream()
                .filter(p -> "PENALTY".equalsIgnoreCase(p.getClassification()) && "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .count();

        BigDecimal totalAccumulated = penaltyRepo.sumActiveMonetaryAmountByDriverId(driverId);
        if (totalAccumulated == null) {
            totalAccumulated = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        Map<String, BigDecimal> penaltiesByType = new LinkedHashMap<>();
        for (Object[] row : penaltyRepo.sumMonetaryByDriverGroupedByAlertType(driverId)) {
            String at = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            if (at != null) penaltiesByType.put(at, amt != null ? amt : BigDecimal.ZERO);
        }

        Map<String, Long> occurrencesByType = new LinkedHashMap<>();
        for (Object[] row : penaltyRepo.countOccurrencesByDriverGroupedByAlertType(driverId)) {
            String at = (String) row[0];
            Long cnt = (Long) row[1];
            if (at != null) occurrencesByType.put(at, cnt != null ? cnt : 0L);
        }

        PenaltyDTO mostRecent = allPenalties.isEmpty() ? null : toPenaltyDTO(allPenalties.get(0));

        List<PenaltyDTO> history = allPenalties.stream()
                .map(this::toPenaltyDTO)
                .collect(Collectors.toList());

        return DriverPenaltySummaryDTO.builder()
                .driverId(driver.getId())
                .driverCode(driver.getCode())
                .driverName(driver.getName())
                .totalQualifyingAlerts(allPenalties.size())
                .warningOnlyCount(warningCount)
                .penaltyBearingCount(penaltyCount)
                .totalPenaltiesAccumulated(totalAccumulated)
                .penaltiesByAlertType(penaltiesByType)
                .occurrencesByAlertType(occurrencesByType)
                .mostRecentPenalty(mostRecent)
                .penaltyHistory(history)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PenaltyDTO> getPenaltiesForDriverFiltered(Long driverId, String alertType, LocalDateTime from, LocalDateTime to) {
        if (!driverRepo.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver", driverId);
        }
        List<DriverPenalty> list;
        if (from != null && to != null) {
            list = penaltyRepo.findByDriverIdAndDateAppliedBetweenOrderByDateAppliedDesc(driverId, from, to);
        } else {
            list = penaltyRepo.findByDriverIdOrderByDateAppliedDesc(driverId);
        }

        if (alertType != null && !alertType.isBlank()) {
            String norm = normalizeAlertType(alertType);
            list = list.stream()
                    .filter(p -> p.getAlertType() != null && p.getAlertType().equalsIgnoreCase(norm))
                    .collect(Collectors.toList());
        }

        return list.stream().map(this::toPenaltyDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PenaltyDTO> getPenaltiesForDriver(Long driverId) {
        return getPenaltiesForDriverFiltered(driverId, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<PenaltyDTO> getAllPenalties() {
        return penaltyRepo.findAll().stream()
                .sorted((a, b) -> b.getDateApplied().compareTo(a.getDateApplied()))
                .map(this::toPenaltyDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FleetPenaltySummaryDTO getFleetPenaltySummary() {
        BigDecimal totalPenalties = penaltyRepo.sumTotalActiveMonetaryAmount();
        if (totalPenalties == null) {
            totalPenalties = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        long penaltyBearing = penaltyRepo.countByClassificationAndStatus("PENALTY", "ACTIVE");
        long warningCount = penaltyRepo.countByClassificationAndStatus("WARNING", "ACTIVE")
                + penaltyRepo.countByClassificationAndStatus("WARNING", "COMPLETED");

        List<Object[]> rankings = penaltyRepo.findDriverPenaltyRankings();
        Long highestDriverId = null;
        String highestDriverCode = null;
        String highestDriverName = null;
        BigDecimal highestDriverTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (!rankings.isEmpty()) {
            Object[] top = rankings.get(0);
            highestDriverId = ((Number) top[0]).longValue();
            highestDriverCode = (String) top[1];
            highestDriverName = (String) top[2];
            highestDriverTotal = (BigDecimal) top[3];
        }

        Map<String, BigDecimal> penaltiesByType = new LinkedHashMap<>();
        for (Object[] row : penaltyRepo.sumTotalMonetaryGroupedByAlertType()) {
            String at = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            if (at != null) penaltiesByType.put(at, amt != null ? amt : BigDecimal.ZERO);
        }

        Map<String, Long> alertCountsByType = new LinkedHashMap<>();
        for (Object[] row : penaltyRepo.countTotalAlertsGroupedByAlertType()) {
            String at = (String) row[0];
            Long cnt = (Long) row[1];
            if (at != null) alertCountsByType.put(at, cnt != null ? cnt : 0L);
        }

        List<PenaltyDTO> recent = penaltyRepo.findTop10ByOrderByDateAppliedDesc().stream()
                .map(this::toPenaltyDTO)
                .collect(Collectors.toList());

        return FleetPenaltySummaryDTO.builder()
                .totalFleetPenalties(totalPenalties)
                .totalPenaltyBearingAlerts(penaltyBearing)
                .totalWarningOnlyAlerts(warningCount)
                .highestPenalizedDriverId(highestDriverId)
                .highestPenalizedDriverCode(highestDriverCode)
                .highestPenalizedDriverName(highestDriverName)
                .highestDriverPenaltyTotal(highestDriverTotal)
                .penaltyBreakdownByAlertType(penaltiesByType)
                .alertCountByAlertType(alertCountsByType)
                .recentPenalties(recent)
                .build();
    }

    public PenaltyDTO recordPenalty(PenaltyRequestDTO req, String appliedBy) {
        if (req.getDriverId() == null) {
            throw new ValidationException("Driver ID is required");
        }
        Driver driver = driverRepo.findById(req.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver", req.getDriverId()));

        if (req.getReason() == null || req.getReason().trim().isEmpty()) {
            throw new ValidationException("A reason is mandatory when recording a penalty");
        }

        BigDecimal points = req.getPenaltyPoints() != null ? req.getPenaltyPoints().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal fine = req.getMonetaryAmount() != null ? req.getMonetaryAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        if (points.compareTo(BigDecimal.ZERO) < 0 || points.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ValidationException("Penalty points must be between 0 and 100");
        }
        if (fine.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Monetary fine amount must be non-negative");
        }

        Alert alert = null;
        if (req.getAlertId() != null) {
            alert = alertRepo.findById(req.getAlertId()).orElse(null);
            if (Boolean.TRUE.equals(req.getDeductFromScore())) {
                long activeDeductionsOnAlert = penaltyRepo.countByDriverIdAndAlertIdAndStatus(driver.getId(), alert != null ? alert.getId() : null, "ACTIVE");
                if (activeDeductionsOnAlert > 0) {
                    throw new ValidationException("An active score deduction penalty already exists for this alert (Alert ID: " + req.getAlertId() + "). Duplicate deductions are prohibited.");
                }
            }
        }

        Trip trip = null;
        if (req.getTripId() != null) {
            trip = tripRepo.findById(req.getTripId()).orElse(null);
        }

        Vehicle vehicle = null;
        if (req.getVehicleId() != null) {
            vehicle = vehicleRepo.findById(req.getVehicleId()).orElse(null);
        } else if (alert != null && alert.getVehicle() != null) {
            vehicle = alert.getVehicle();
        } else if (trip != null && trip.getVehicle() != null) {
            vehicle = trip.getVehicle();
        }

        String penaltyType = req.getPenaltyType() != null ? req.getPenaltyType() : "MANUAL_SAFETY_DEDUCTION";
        String classification = fine.compareTo(BigDecimal.ZERO) > 0 ? "PENALTY" : "WARNING";

        DriverPenalty penalty = DriverPenalty.builder()
                .driver(driver)
                .alert(alert)
                .trip(trip)
                .vehicle(vehicle)
                .penaltyType(penaltyType)
                .classification(classification)
                .alertType(alert != null ? alert.getAlertType() : "Manual Violation")
                .occurrenceNumber(1)
                .basePenalty(fine)
                .penaltyIncrement(DEFAULT_INCREMENT)
                .reason(req.getReason().trim())
                .penaltyPoints(points)
                .monetaryAmount(fine)
                .dateApplied(LocalDateTime.now())
                .appliedBy(appliedBy != null ? appliedBy : "Fleet Manager")
                .status("ACTIVE")
                .deductFromScore(Boolean.TRUE.equals(req.getDeductFromScore()))
                .notes(req.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        DriverPenalty saved = penaltyRepo.save(penalty);

        // Record Audit Log
        PenaltyAuditLog audit = PenaltyAuditLog.builder()
                .penalty(saved)
                .action("CREATED")
                .changedBy(appliedBy != null ? appliedBy : "Fleet Manager")
                .reason(req.getReason().trim())
                .oldStatus(null)
                .newStatus("ACTIVE")
                .details(String.format("Created penalty %s with %.2f points and Rs. %.2f fine. Deduct from score: %s",
                        penaltyType, points.doubleValue(), fine.doubleValue(), penalty.getDeductFromScore()))
                .createdAt(LocalDateTime.now())
                .build();
        auditRepo.save(audit);

        return toPenaltyDTO(saved);
    }

    public PenaltyDTO revokePenalty(Long penaltyId, String reason, String revokedBy) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Reason is required when revoking a penalty");
        }

        DriverPenalty penalty = penaltyRepo.findById(penaltyId)
                .orElseThrow(() -> new ResourceNotFoundException("DriverPenalty", penaltyId));

        String oldStatus = penalty.getStatus();
        penalty.setStatus("REVOKED");
        penalty.setDeductFromScore(false);
        penalty.setUpdatedAt(LocalDateTime.now());
        DriverPenalty updated = penaltyRepo.save(penalty);

        // Record Audit Log
        PenaltyAuditLog audit = PenaltyAuditLog.builder()
                .penalty(updated)
                .action("REVOKED")
                .changedBy(revokedBy != null ? revokedBy : "Fleet Manager")
                .reason(reason.trim())
                .oldStatus(oldStatus)
                .newStatus("REVOKED")
                .details("Penalty revoked. Safety deductions deactivated.")
                .createdAt(LocalDateTime.now())
                .build();
        auditRepo.save(audit);

        return toPenaltyDTO(updated);
    }

    private AlertPenaltyConfigDTO toConfigDTO(AlertPenaltyConfig c) {
        return AlertPenaltyConfigDTO.builder()
                .id(c.getId())
                .alertType(c.getAlertType())
                .weight(c.getWeight())
                .defaultFineAmount(c.getDefaultFineAmount())
                .penaltyIncrement(c.getPenaltyIncrement())
                .warningThreshold(c.getWarningThreshold())
                .penaltyEnabled(c.getPenaltyEnabled())
                .description(c.getDescription())
                .isActive(c.getIsActive())
                .updatedBy(c.getUpdatedBy())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    public PenaltyDTO toPenaltyDTO(DriverPenalty p) {
        BigDecimal cumulative = BigDecimal.ZERO;
        if (p.getDriver() != null && p.getDriver().getId() != null) {
            BigDecimal sum = penaltyRepo.sumActiveMonetaryAmountByDriverId(p.getDriver().getId());
            if (sum != null) cumulative = sum;
        }

        return PenaltyDTO.builder()
                .id(p.getId())
                .driverId(p.getDriver() != null ? p.getDriver().getId() : null)
                .driverCode(p.getDriver() != null ? p.getDriver().getCode() : null)
                .driverName(p.getDriver() != null ? p.getDriver().getName() : null)
                .alertId(p.getAlert() != null ? p.getAlert().getId() : null)
                .alertType(p.getAlertType() != null ? p.getAlertType() : (p.getAlert() != null ? p.getAlert().getAlertType() : null))
                .tripId(p.getTrip() != null ? p.getTrip().getId() : null)
                .vehicleId(p.getVehicle() != null ? p.getVehicle().getId() : null)
                .vehicleRegistration(p.getVehicle() != null ? p.getVehicle().getRegistrationNumber() : null)
                .penaltyType(p.getPenaltyType())
                .occurrenceNumber(p.getOccurrenceNumber())
                .classification(p.getClassification())
                .basePenalty(p.getBasePenalty())
                .penaltyIncrement(p.getPenaltyIncrement())
                .calculationTimestamp(p.getCalculationTimestamp())
                .processingStatus(p.getProcessingStatus())
                .notificationStatus(p.getNotificationStatus())
                .driverCumulativePenalties(cumulative)
                .reason(p.getReason())
                .penaltyPoints(p.getPenaltyPoints())
                .monetaryAmount(p.getMonetaryAmount())
                .dateApplied(p.getDateApplied())
                .appliedBy(p.getAppliedBy())
                .status(p.getStatus())
                .deductFromScore(p.getDeductFromScore())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
