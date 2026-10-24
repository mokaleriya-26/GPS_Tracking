package com.gps.tracking.service;

import com.gps.tracking.dto.DriverDTO;
import com.gps.tracking.dto.DriverRankingDTO;
import com.gps.tracking.dto.SafetyScoreBreakdownDTO;
import com.gps.tracking.entity.*;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DriverService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverDailyStatsRepository statsRepository;
    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;
    private final DriverPenaltyRepository driverPenaltyRepository;
    private final SafetyScoreService safetyScoreService;

    public List<DriverDTO> getAllDrivers() {
        return getAllDrivers(null, false);
    }

    public List<DriverDTO> getActiveDrivers() {
        return getAllDrivers(true, false);
    }

    public List<DriverDTO> getAllDrivers(Boolean active, Boolean includeTests) {
        List<Driver> drivers;
        boolean incTests = Boolean.TRUE.equals(includeTests);

        if (incTests) {
            drivers = (active != null && active)
                    ? driverRepository.findByActiveTrueOrderByIdAsc()
                    : driverRepository.findAllByOrderByIdAsc();
        } else {
            drivers = (active != null && active)
                    ? driverRepository.findActiveFleetDriversOrderByIdAsc()
                    : driverRepository.findFleetDriversOrderByIdAsc();
        }

        return populateDriverDTOList(drivers);
    }

    public DriverDTO getDriverById(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", id));
        return toDetailedDTO(driver);
    }

    public DriverDTO getDriverByCode(String code) {
        Driver driver = driverRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "code", code));
        return toDetailedDTO(driver);
    }

    public List<DriverDTO> searchByName(String name) {
        List<Driver> drivers = driverRepository.searchByName(name);
        return populateDriverDTOList(drivers);
    }

    /**
     * Efficiently enriches a list of drivers with their assigned vehicles, trip counts,
     * alert counts, penalty summaries, and safety scores in batch queries.
     */
    private List<DriverDTO> populateDriverDTOList(List<Driver> drivers) {
        if (drivers == null || drivers.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Fetch currently assigned vehicles
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        Map<Long, List<String>> assignedVehiclesMap = new HashMap<>();
        Map<Long, List<DriverDTO.AssignedVehicleDTO>> currentVehiclesMap = new HashMap<>();
        for (Vehicle v : allVehicles) {
            if (v.getDriver() != null && v.getDriver().getId() != null) {
                Long dId = v.getDriver().getId();
                String label = v.getCode() + (v.getRegistrationNumber() != null ? " (" + v.getRegistrationNumber() + ")" : "");
                assignedVehiclesMap.computeIfAbsent(dId, k -> new ArrayList<>()).add(label);
                currentVehiclesMap.computeIfAbsent(dId, k -> new ArrayList<>()).add(
                    DriverDTO.AssignedVehicleDTO.builder()
                        .id(v.getId())
                        .code(v.getCode())
                        .registrationNumber(v.getRegistrationNumber())
                        .vehicleType(v.getVehicleType())
                        .makeModel(v.getMakeModel())
                        .active(v.getActive())
                        .build()
                );
            }
        }

        // 2. Fetch trip aggregates (count & distance)
        Map<Long, Integer> tripCountMap = new HashMap<>();
        Map<Long, Double> distanceMap = new HashMap<>();
        try {
            List<Object[]> tripAggs = tripRepository.getAllDriverTripAggregates();
            for (Object[] row : tripAggs) {
                if (row[0] != null) {
                    Long dId = (Long) row[0];
                    tripCountMap.put(dId, ((Number) row[1]).intValue());
                    distanceMap.put(dId, ((Number) row[2]).doubleValue());
                }
            }
        } catch (Exception e) {
            log.warn("Could not load trip aggregates: {}", e.getMessage());
        }

        // 3. Fetch alert counts
        Map<Long, Long> alertCountMap = new HashMap<>();
        try {
            List<Object[]> alertCounts = alertRepository.getAllDriverAlertCounts();
            for (Object[] row : alertCounts) {
                if (row[0] != null) {
                    alertCountMap.put((Long) row[0], ((Number) row[1]).longValue());
                }
            }
        } catch (Exception e) {
            log.warn("Could not load alert counts: {}", e.getMessage());
        }

        // 4. Fetch penalty aggregates (active count, active monetary sum, active warnings, active fines)
        Map<Long, Long> penaltyCountMap = new HashMap<>();
        Map<Long, BigDecimal> penaltyAmountMap = new HashMap<>();
        Map<Long, Long> warningCountMap = new HashMap<>();
        Map<Long, Long> fineCountMap = new HashMap<>();
        try {
            List<Object[]> penaltyAggs = driverPenaltyRepository.getAllDriverPenaltyAggregates();
            for (Object[] row : penaltyAggs) {
                if (row[0] != null) {
                    Long dId = (Long) row[0];
                    penaltyCountMap.put(dId, ((Number) row[1]).longValue());
                    penaltyAmountMap.put(dId, (BigDecimal) row[2]);
                    if (row.length > 3 && row[3] != null) {
                        warningCountMap.put(dId, ((Number) row[3]).longValue());
                    }
                    if (row.length > 4 && row[4] != null) {
                        fineCountMap.put(dId, ((Number) row[4]).longValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load penalty aggregates: {}", e.getMessage());
        }

        // 5. Fetch safety scores from daily stats
        Map<Long, Double> safetyScoreMap = new HashMap<>();
        try {
            List<Object[]> scoreRows = statsRepository.getAllDriverAverageScores();
            for (Object[] row : scoreRows) {
                if (row[0] != null && row[1] != null) {
                    double s = ((Number) row[1]).doubleValue();
                    safetyScoreMap.put((Long) row[0], Math.round(s * 10.0) / 10.0);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load driver average scores: {}", e.getMessage());
        }

        // 6. Map each driver to DTO
        return drivers.stream().map(driver -> {
            Long dId = driver.getId();
            List<String> assigned = assignedVehiclesMap.getOrDefault(dId, Collections.emptyList());
            List<DriverDTO.AssignedVehicleDTO> currentVehs = currentVehiclesMap.getOrDefault(dId, Collections.emptyList());
            String summary = !assigned.isEmpty() ? String.join(", ", assigned) : "None";

            int trips = tripCountMap.getOrDefault(dId, 0);
            double dist = distanceMap.getOrDefault(dId, 0.0);
            long alerts = alertCountMap.getOrDefault(dId, 0L);
            long penalties = penaltyCountMap.getOrDefault(dId, 0L);
            BigDecimal penaltyAmt = penaltyAmountMap.getOrDefault(dId, BigDecimal.ZERO);
            long warnings = warningCountMap.getOrDefault(dId, 0L);
            long fines = fineCountMap.getOrDefault(dId, 0L);

            String pSummary;
            if (penalties == 0) {
                pSummary = "0 Penalties (₹0)";
            } else if (fines > 0) {
                pSummary = penalties + (penalties == 1 ? " Penalty" : " Penalties") + " (₹" + penaltyAmt.setScale(0, RoundingMode.HALF_UP) + ")";
            } else {
                pSummary = warnings + (warnings == 1 ? " Warning" : " Warnings") + " (₹0)";
            }

            Double score = safetyScoreMap.get(dId);
            if (score == null) {
                score = (trips > 0) ? 85.0 : 100.0;
            }

            return DriverDTO.builder()
                    .id(driver.getId())
                    .code(driver.getCode())
                    .name(driver.getName())
                    .phone(driver.getPhone())
                    .email(driver.getEmail())
                    .licenseNumber(driver.getLicenseNumber())
                    .joinedOn(driver.getJoinedOn())
                    .active(driver.getActive())
                    .createdAt(driver.getCreatedAt())
                    .assignedVehicles(assigned)
                    .assignedVehicleSummary(summary)
                    .currentVehicles(currentVehs)
                    .totalTrips(trips)
                    .totalDistanceKm(dist)
                    .totalAlerts(alerts)
                    .totalPenalties(penalties)
                    .totalPenaltyAmount(penaltyAmt)
                    .activeWarnings(warnings)
                    .activeFines(fines)
                    .penaltySummary(pSummary)
                    .safetyScore(score)
                    .build();
        }).collect(Collectors.toList());
    }

    private DriverDTO toDetailedDTO(Driver driver) {
        Long dId = driver.getId();

        // 1. Current Assigned vehicles
        List<Vehicle> assigned = vehicleRepository.findByDriverId(dId);
        List<String> assignedList = new ArrayList<>();
        List<DriverDTO.AssignedVehicleDTO> currentVehiclesList = new ArrayList<>();
        Set<Long> currentVehicleIds = new HashSet<>();
        for (Vehicle v : assigned) {
            currentVehicleIds.add(v.getId());
            String label = v.getCode() + (v.getRegistrationNumber() != null ? " (" + v.getRegistrationNumber() + ")" : "");
            assignedList.add(label);
            currentVehiclesList.add(DriverDTO.AssignedVehicleDTO.builder()
                    .id(v.getId())
                    .code(v.getCode())
                    .registrationNumber(v.getRegistrationNumber())
                    .vehicleType(v.getVehicleType())
                    .makeModel(v.getMakeModel())
                    .active(v.getActive())
                    .build());
        }
        String assignedSummary = !assignedList.isEmpty() ? String.join(", ", assignedList) : "None";

        // 2. Operated vehicles from trips (excluding current ones)
        List<Vehicle> operated = tripRepository.findDistinctVehiclesByDriverId(dId);
        List<String> operatedList = new ArrayList<>();
        List<DriverDTO.AssignedVehicleDTO> historicalVehiclesList = new ArrayList<>();
        for (Vehicle v : operated) {
            if (!currentVehicleIds.contains(v.getId())) {
                String label = v.getCode() + (v.getRegistrationNumber() != null ? " (" + v.getRegistrationNumber() + ")" : "");
                operatedList.add(label);
                historicalVehiclesList.add(DriverDTO.AssignedVehicleDTO.builder()
                        .id(v.getId())
                        .code(v.getCode())
                        .registrationNumber(v.getRegistrationNumber())
                        .vehicleType(v.getVehicleType())
                        .makeModel(v.getMakeModel())
                        .active(v.getActive())
                        .build());
            }
        }

        // 3. Trip stats
        long totalTrips = tripRepository.countByDriverId(dId);
        Double dist = tripRepository.sumDistanceByDriverId(dId);

        // 4. Alert stats
        long totalAlerts = alertRepository.countByDriverId(dId);

        // 5. Penalty stats (consistent with batch queries)
        List<DriverPenalty> allPenalties = driverPenaltyRepository.findByDriverIdOrderByDateAppliedDesc(dId);
        long activePenalties = allPenalties.stream()
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .count();
        long activeWarnings = allPenalties.stream()
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()) && ("WARNING".equalsIgnoreCase(p.getClassification()) || p.getMonetaryAmount() == null || p.getMonetaryAmount().compareTo(BigDecimal.ZERO) == 0))
                .count();
        long activeFines = allPenalties.stream()
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()) && "PENALTY".equalsIgnoreCase(p.getClassification()) && p.getMonetaryAmount() != null && p.getMonetaryAmount().compareTo(BigDecimal.ZERO) > 0)
                .count();
        BigDecimal penaltyAmount = allPenalties.stream()
                .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                .map(p -> p.getMonetaryAmount() != null ? p.getMonetaryAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DriverDTO.PenaltyRecordDTO> penaltyRecords = allPenalties.stream().map(p -> DriverDTO.PenaltyRecordDTO.builder()
                .id(p.getId())
                .penaltyType(p.getPenaltyType())
                .classification(p.getClassification())
                .reason(p.getReason())
                .monetaryAmount(p.getMonetaryAmount() != null ? p.getMonetaryAmount() : BigDecimal.ZERO)
                .penaltyPoints(p.getPenaltyPoints() != null ? p.getPenaltyPoints().doubleValue() : 0.0)
                .status(p.getStatus())
                .dateApplied(p.getDateApplied())
                .occurrenceNumber(p.getOccurrenceNumber())
                .build()
        ).collect(Collectors.toList());

        String pSummary;
        if (activePenalties == 0) {
            pSummary = "0 Penalties (₹0)";
        } else if (activeFines > 0) {
            pSummary = activePenalties + (activePenalties == 1 ? " Penalty" : " Penalties") + " (₹" + penaltyAmount.setScale(0, RoundingMode.HALF_UP) + ")";
        } else {
            pSummary = activeWarnings + (activeWarnings == 1 ? " Warning" : " Warnings") + " (₹0)";
        }

        // 6. Safety score
        Double score = null;
        try {
            List<DriverDailyStats> stats = statsRepository.findByDriverIdAndStatDateBetweenOrderByStatDateAsc(
                    dId, LocalDate.now().minusMonths(6), LocalDate.now().plusDays(1));
            if (!stats.isEmpty()) {
                double avg = stats.stream().mapToDouble(s -> s.getSafetyScore() != null ? s.getSafetyScore().doubleValue() : 85.0).average().orElse(85.0);
                score = Math.round(avg * 10.0) / 10.0;
            } else {
                score = (totalTrips > 0) ? 85.0 : 100.0;
            }
        } catch (Exception e) {
            score = 100.0;
        }

        return DriverDTO.builder()
                .id(driver.getId())
                .code(driver.getCode())
                .name(driver.getName())
                .phone(driver.getPhone())
                .email(driver.getEmail())
                .licenseNumber(driver.getLicenseNumber())
                .joinedOn(driver.getJoinedOn())
                .active(driver.getActive())
                .createdAt(driver.getCreatedAt())
                .assignedVehicles(assignedList)
                .assignedVehicleSummary(assignedSummary)
                .currentVehicles(currentVehiclesList)
                .vehiclesOperated(operatedList)
                .historicalVehicles(historicalVehiclesList)
                .totalTrips((int) totalTrips)
                .totalDistanceKm(dist != null ? dist : 0.0)
                .totalAlerts(totalAlerts)
                .totalPenalties(activePenalties)
                .totalPenaltyAmount(penaltyAmount)
                .activeWarnings(activeWarnings)
                .activeFines(activeFines)
                .penaltySummary(pSummary)
                .penaltyRecords(penaltyRecords)
                .safetyScore(score)
                .build();
    }

    /**
     * Standardized Driver Ranking using SafetyScoreService.
     */
    public List<DriverRankingDTO> getDriverRanking(LocalDate from, LocalDate to) {
        List<Driver> drivers = driverRepository.findFleetDriversOrderByIdAsc();
        List<DriverRankingDTO> rankings = new ArrayList<>();

        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDate end   = to != null ? to : LocalDate.now();

        LocalDateTime dtFrom = start.atStartOfDay();
        LocalDateTime dtTo   = end.plusDays(1).atStartOfDay();

        for (Driver driver : drivers) {
            Long driverId = driver.getId();

            List<Trip> trips = tripRepository.findTripsByDriverAndDateRange(driverId, dtFrom, dtTo);
            List<Alert> alerts = alertRepository.findAlertsByDriverAndDateRange(driverId, dtFrom, dtTo);
            List<DriverDailyStats> statsList = statsRepository
                    .findByDriverIdAndStatDateBetweenOrderByStatDateAsc(driverId, start, end);

            int totalTrips = trips.size();
            double totalDistKm = trips.stream()
                    .mapToDouble(t -> t.getDistanceKm() != null ? t.getDistanceKm().doubleValue() : 0.0)
                    .sum();

            String vehiclesOperated = trips.stream()
                    .map(t -> t.getVehicle() != null ? t.getVehicle().getRegistrationNumber() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (vehiclesOperated.isEmpty()) vehiclesOperated = "—";

            long overspeed = alerts.stream()
                    .filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("overspeed"))
                    .count();
            long harshBraking = alerts.stream()
                    .filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("braking"))
                    .count();
            long harshAccel = alerts.stream()
                    .filter(a -> a.getAlertType() != null && (a.getAlertType().toLowerCase().contains("accel") || a.getAlertType().toLowerCase().contains("acceleration")))
                    .count();
            long gpsDisc = alerts.stream()
                    .filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("gps"))
                    .count();
            long nightDriving = alerts.stream()
                    .filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("night"))
                    .count();
            long fatigue = alerts.stream()
                    .filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("fatigue"))
                    .count();

            int nightSec = statsList.stream()
                    .mapToInt(s -> s.getNightDrivingSeconds() != null ? s.getNightDrivingSeconds() : 0)
                    .sum();
            double nightHours = nightSec > 0 ? nightSec / 3600.0 : (nightDriving * 1.5);

            SafetyScoreBreakdownDTO breakdown = safetyScoreService.calculateDriverScore(
                    driver,
                    totalTrips,
                    totalDistKm,
                    overspeed,
                    harshBraking,
                    harshAccel,
                    gpsDisc,
                    nightDriving,
                    nightHours,
                    fatigue,
                    dtFrom,
                    dtTo
            );

            rankings.add(DriverRankingDTO.builder()
                    .driverId(driverId)
                    .driverCode(driver.getCode())
                    .driverName(driver.getName())
                    .safetyScore(breakdown.getFinalSafetyScore())
                    .performanceCategory(breakdown.getPerformanceCategory())
                    .vehiclesOperated(vehiclesOperated)
                    .tripCount(totalTrips)
                    .totalDistanceKm(BigDecimal.valueOf(totalDistKm).setScale(2, RoundingMode.HALF_UP))
                    .totalAlerts(alerts.size())
                    .overspeedEvents((int) overspeed)
                    .harshBrakingEvents((int) harshBraking)
                    .harshAccelerationEvents((int) harshAccel)
                    .fatigueEvents((int) fatigue)
                    .nightDrivingSeconds(nightSec)
                    .scoreExplanation(breakdown.getExplanation())
                    .breakdown(breakdown)
                    .build());
        }

        rankings.sort((a, b) -> {
            int cmp = b.getSafetyScore().compareTo(a.getSafetyScore());
            if (cmp != 0) return cmp;
            int tripCmp = Integer.compare(b.getTripCount(), a.getTripCount());
            if (tripCmp != 0) return tripCmp;
            return b.getTotalDistanceKm().compareTo(a.getTotalDistanceKm());
        });

        int r = 1;
        for (DriverRankingDTO dto : rankings) {
            dto.setRank(r++);
            if (dto.getBreakdown() != null) {
                dto.getBreakdown().setRank(dto.getRank());
            }
        }

        return rankings;
    }

    @Transactional
    public DriverDTO createDriver(DriverDTO dto) {
        Driver driver = Driver.builder()
                .code(dto.getCode() != null ? dto.getCode().trim() : "DRV" + System.currentTimeMillis() % 100000)
                .name(dto.getName() != null ? dto.getName().trim() : "New Driver")
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .licenseNumber(dto.getLicenseNumber())
                .joinedOn(dto.getJoinedOn() != null ? dto.getJoinedOn() : LocalDate.now())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();
        Driver saved = driverRepository.save(driver);
        return toDetailedDTO(saved);
    }

    @Transactional
    public DriverDTO updateDriver(Long id, DriverDTO dto) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", id));
        if (dto.getName() != null) driver.setName(dto.getName().trim());
        if (dto.getCode() != null) driver.setCode(dto.getCode().trim());
        if (dto.getPhone() != null) driver.setPhone(dto.getPhone());
        if (dto.getEmail() != null) driver.setEmail(dto.getEmail());
        if (dto.getLicenseNumber() != null) driver.setLicenseNumber(dto.getLicenseNumber());
        if (dto.getJoinedOn() != null) driver.setJoinedOn(dto.getJoinedOn());
        if (dto.getActive() != null) driver.setActive(dto.getActive());
        Driver saved = driverRepository.save(driver);
        return toDetailedDTO(saved);
    }

    @Transactional
    public void deleteDriver(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", id));
        
        // Disassociate any vehicles assigned to this driver before deletion
        List<Vehicle> assignedVehicles = vehicleRepository.findByDriverId(id);
        for (Vehicle v : assignedVehicles) {
            v.setDriver(null);
            vehicleRepository.save(v);
        }

        driverRepository.delete(driver);
        log.info("Driver ID {} ({}) deleted successfully", id, driver.getName());
    }
}
