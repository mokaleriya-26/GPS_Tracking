package com.gps.tracking.service;

import com.gps.tracking.dto.DriverDTO;
import com.gps.tracking.dto.DriverRankingDTO;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.DriverDailyStats;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.DriverDailyStatsRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverService {

    private final DriverRepository driverRepository;
    private final DriverDailyStatsRepository statsRepository;
    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;

    public List<DriverDTO> getAllDrivers() {
        return driverRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<DriverDTO> getActiveDrivers() {
        return driverRepository.findByActiveTrue().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public DriverDTO getDriverById(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", id));
        return toDTO(driver);
    }

    public DriverDTO getDriverByCode(String code) {
        Driver driver = driverRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "code", code));
        return toDTO(driver);
    }

    public List<DriverDTO> searchByName(String name) {
        return driverRepository.searchByName(name).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get driver ranking based on safety score and performance metrics.
     *
     * Safety score formula (from MIGRATION_PLAN.md Section 10.2):
     *   safety_score = 100
     *     - (overspeed_events * 5)
     *     - (harsh_braking_events * 4)
     *     - (harsh_acceleration_events * 4)
     *     - (night_driving_seconds / 3600 * 3)
     *     - (fatigue_events * 8)
     *     + (trips_without_incident * 1)
     *   Clamped between 0 and 100.
     */
    public List<DriverRankingDTO> getDriverRanking(LocalDate from, LocalDate to) {
        List<Object[]> rankingData = statsRepository.getDriverRanking(from, to);
        List<DriverRankingDTO> rankings = new ArrayList<>();

        int rank = 1;
        for (Object[] row : rankingData) {
            Long driverId = (Long) row[0];
            Double avgScore = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;

            Driver driver = driverRepository.findById(driverId).orElse(null);
            if (driver == null) continue;

            List<DriverDailyStats> statsList = statsRepository
                    .findByDriverIdAndStatDateBetweenOrderByStatDateAsc(driverId, from, to);

            int totalTrips = statsList.stream().mapToInt(s -> s.getTripCount() != null ? s.getTripCount() : 0).sum();
            double totalDistance = statsList.stream().mapToDouble(s -> s.getDistanceKm() != null ? s.getDistanceKm().doubleValue() : 0).sum();
            int overspeed = statsList.stream().mapToInt(s -> s.getOverspeedEvents() != null ? s.getOverspeedEvents() : 0).sum();
            int harshBraking = statsList.stream().mapToInt(s -> s.getHarshBrakingEvents() != null ? s.getHarshBrakingEvents() : 0).sum();
            int harshAccel = statsList.stream().mapToInt(s -> s.getHarshAccelerationEvents() != null ? s.getHarshAccelerationEvents() : 0).sum();
            int fatigue = statsList.stream().mapToInt(s -> s.getFatigueEvents() != null ? s.getFatigueEvents() : 0).sum();
            int nightDriving = statsList.stream().mapToInt(s -> s.getNightDrivingSeconds() != null ? s.getNightDrivingSeconds() : 0).sum();

            long totalAlerts = alertRepository.countByDriverAndDateRange(driverId,
                    from.atStartOfDay(), to.plusDays(1).atStartOfDay());

            String explanation = buildScoreExplanation(avgScore, overspeed, harshBraking, harshAccel, fatigue, nightDriving, totalTrips);

            rankings.add(DriverRankingDTO.builder()
                    .rank(rank++)
                    .driverId(driverId)
                    .driverCode(driver.getCode())
                    .driverName(driver.getName())
                    .safetyScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP))
                    .tripCount(totalTrips)
                    .totalDistanceKm(BigDecimal.valueOf(totalDistance).setScale(2, RoundingMode.HALF_UP))
                    .totalAlerts((int) totalAlerts)
                    .overspeedEvents(overspeed)
                    .harshBrakingEvents(harshBraking)
                    .harshAccelerationEvents(harshAccel)
                    .fatigueEvents(fatigue)
                    .nightDrivingSeconds(nightDriving)
                    .scoreExplanation(explanation)
                    .build());
        }
        // Fallback: if driver_daily_stats is empty, compute ranking directly from alerts + trips
        if (rankings.isEmpty()) {
            rankings = computeRankingFromAlerts(from, to);
        }
        return rankings;
    }

    /**
     * Fallback ranking computed directly from alerts + trips when driver_daily_stats has no data.
     * Formula: score = 100 - (overspeed×5) - (braking×4) - (nightHrs×3) + (cleanTrips×1).
     */
    private List<DriverRankingDTO> computeRankingFromAlerts(LocalDate from, LocalDate to) {
        List<Driver> allDrivers = driverRepository.findAll();
        List<DriverRankingDTO> result = new ArrayList<>();

        LocalDateTime dtFrom = (from != null ? from : LocalDate.of(2020, 1, 1)).atStartOfDay();
        LocalDateTime dtTo   = (to   != null ? to.plusDays(1) : LocalDate.now().plusDays(1)).atStartOfDay();

        for (Driver driver : allDrivers) {
            Long driverId = driver.getId();

            long totalTrips = tripRepository.countByDriverId(driverId);
            long totalAlerts = alertRepository.countLiveByDriverAndDateRange(driverId, dtFrom, dtTo);

            // Break down alert counts by type using existing repo method
            List<Object[]> typeCounts = alertRepository.countByTypeForDriver(driverId, dtFrom, dtTo);
            int overspeed = 0, braking = 0, nightHrs = 0;
            for (Object[] row : typeCounts) {
                String type = String.valueOf(row[0]).toLowerCase();
                long cnt = ((Number) row[1]).longValue();
                if (type.contains("overspeed"))     overspeed = (int) Math.min(cnt, 20);
                if (type.contains("harsh braking")) braking   = (int) Math.min(cnt, 20);
                if (type.contains("night driving")) nightHrs  = (int) Math.min(cnt * 2, 10);
            }

            int cleanTrips = Math.max(0, (int) totalTrips - (int) totalAlerts);
            double score = Math.max(0, Math.min(100,
                    100.0 - (overspeed * 5) - (braking * 4) - (nightHrs * 3) + cleanTrips));

            result.add(DriverRankingDTO.builder()
                    .driverId(driverId)
                    .driverCode(driver.getCode())
                    .driverName(driver.getName())
                    .safetyScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP))
                    .tripCount((int) totalTrips)
                    .totalDistanceKm(BigDecimal.ZERO)
                    .totalAlerts((int) totalAlerts)
                    .overspeedEvents(overspeed)
                    .harshBrakingEvents(braking)
                    .harshAccelerationEvents(0)
                    .fatigueEvents(0)
                    .nightDrivingSeconds(nightHrs * 3600)
                    .scoreExplanation(String.format(
                        "Score %.1f/100 (alerts: overspeed=%d ×5, braking=%d ×4, night≈%d hrs ×3, clean trips=%d +1)",
                        score, overspeed, braking, nightHrs, cleanTrips))
                    .build());
        }

        result.sort((a, b) -> b.getSafetyScore().compareTo(a.getSafetyScore()));
        int r = 1;
        for (DriverRankingDTO dto : result) dto.setRank(r++);
        return result;
    }


    @Transactional
    public DriverDTO createDriver(DriverDTO dto) {
        Driver driver = Driver.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .phone(dto.getPhone())
                .licenseNumber(dto.getLicenseNumber())
                .joinedOn(dto.getJoinedOn())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();
        return toDTO(driverRepository.save(driver));
    }

    @Transactional
    public DriverDTO updateDriver(Long id, DriverDTO dto) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", id));
        if (dto.getName() != null) driver.setName(dto.getName());
        if (dto.getPhone() != null) driver.setPhone(dto.getPhone());
        if (dto.getLicenseNumber() != null) driver.setLicenseNumber(dto.getLicenseNumber());
        if (dto.getJoinedOn() != null) driver.setJoinedOn(dto.getJoinedOn());
        if (dto.getActive() != null) driver.setActive(dto.getActive());
        return toDTO(driverRepository.save(driver));
    }

    private DriverDTO toDTO(Driver driver) {
        DriverDTO dto = new DriverDTO();
        dto.setId(driver.getId());
        dto.setCode(driver.getCode());
        dto.setName(driver.getName());
        dto.setPhone(driver.getPhone());
        dto.setLicenseNumber(driver.getLicenseNumber());
        dto.setJoinedOn(driver.getJoinedOn());
        dto.setActive(driver.getActive());
        dto.setCreatedAt(driver.getCreatedAt());
        return dto;
    }

    private String buildScoreExplanation(double score, int overspeed, int harshBraking,
                                          int harshAccel, int fatigue, int nightDrivingSec, int trips) {
        return String.format(
            "Safety score %.1f/100. Deductions: Overspeed -%d (×5), Harsh Braking -%d (×4), " +
            "Harsh Acceleration -%d (×4), Night Driving -%.1f hrs (×3/hr), Fatigue -%d (×8). " +
            "Total trips: %d.",
            score, overspeed * 5, harshBraking * 4, harshAccel * 4,
            nightDrivingSec / 3600.0, fatigue * 8, trips
        );
    }
}
