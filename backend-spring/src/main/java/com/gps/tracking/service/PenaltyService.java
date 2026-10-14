package com.gps.tracking.service;

import com.gps.tracking.dto.AlertPenaltyConfigDTO;
import com.gps.tracking.dto.PenaltyDTO;
import com.gps.tracking.dto.PenaltyRequestDTO;
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
import java.util.List;
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

    // --- CONFIGURATIONS ---

    @Transactional(readOnly = true)
    public List<AlertPenaltyConfigDTO> getAlertPenaltyConfigs() {
        return configRepo.findAll().stream()
                .map(this::toConfigDTO)
                .collect(Collectors.toList());
    }

    public AlertPenaltyConfigDTO updateAlertPenaltyConfig(Long id, BigDecimal weight, BigDecimal defaultFine, String user) {
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

        config.setUpdatedBy(user != null ? user : "Fleet Manager");
        config.setUpdatedAt(LocalDateTime.now());
        return toConfigDTO(configRepo.save(config));
    }

    // --- DRIVER PENALTIES ---

    @Transactional(readOnly = true)
    public List<PenaltyDTO> getPenaltiesForDriver(Long driverId) {
        if (!driverRepo.existsById(driverId)) {
            throw new ResourceNotFoundException("Driver", driverId);
        }
        return penaltyRepo.findByDriverIdOrderByDateAppliedDesc(driverId).stream()
                .map(this::toPenaltyDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PenaltyDTO> getAllPenalties() {
        return penaltyRepo.findAll().stream()
                .sorted((a, b) -> b.getDateApplied().compareTo(a.getDateApplied()))
                .map(this::toPenaltyDTO)
                .collect(Collectors.toList());
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
            // Check for duplicate deduction if alert is already penalized
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

        DriverPenalty penalty = DriverPenalty.builder()
                .driver(driver)
                .alert(alert)
                .trip(trip)
                .vehicle(vehicle)
                .penaltyType(penaltyType)
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
        penalty.setDeductFromScore(false); // Revoked penalties must no longer deduct points
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
                .description(c.getDescription())
                .isActive(c.getIsActive())
                .updatedBy(c.getUpdatedBy())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private PenaltyDTO toPenaltyDTO(DriverPenalty p) {
        return PenaltyDTO.builder()
                .id(p.getId())
                .driverId(p.getDriver() != null ? p.getDriver().getId() : null)
                .driverCode(p.getDriver() != null ? p.getDriver().getCode() : null)
                .driverName(p.getDriver() != null ? p.getDriver().getName() : null)
                .alertId(p.getAlert() != null ? p.getAlert().getId() : null)
                .alertType(p.getAlert() != null ? p.getAlert().getAlertType() : null)
                .tripId(p.getTrip() != null ? p.getTrip().getId() : null)
                .vehicleId(p.getVehicle() != null ? p.getVehicle().getId() : null)
                .vehicleRegistration(p.getVehicle() != null ? p.getVehicle().getRegistrationNumber() : null)
                .penaltyType(p.getPenaltyType())
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
