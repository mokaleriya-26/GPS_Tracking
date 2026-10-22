package com.gps.tracking.service;

import com.gps.tracking.dto.DriverDTO;
import com.gps.tracking.dto.DriverRankingDTO;
import com.gps.tracking.dto.SafetyScoreBreakdownDTO;
import com.gps.tracking.entity.Alert;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.DriverDailyStats;
import com.gps.tracking.entity.Trip;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.DriverDailyStatsRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.TripRepository;
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
    private final DriverDailyStatsRepository statsRepository;
    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;
    private final SafetyScoreService safetyScoreService;

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
     * Standardized Driver Ranking using SafetyScoreService.
     * Computes normalized event rates, configurable weights, and fair clamped scores.
     */
    public List<DriverRankingDTO> getDriverRanking(LocalDate from, LocalDate to) {
        List<Driver> drivers = driverRepository.findAll();
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

            // Distinct vehicles operated during this period
            String vehiclesOperated = trips.stream()
                    .map(t -> t.getVehicle() != null ? t.getVehicle().getRegistrationNumber() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (vehiclesOperated.isEmpty()) vehiclesOperated = "—";

            // Count events by type from alerts
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

            // Also aggregate night driving seconds from daily stats if available
            int nightSec = statsList.stream()
                    .mapToInt(s -> s.getNightDrivingSeconds() != null ? s.getNightDrivingSeconds() : 0)
                    .sum();
            double nightHours = nightSec > 0 ? nightSec / 3600.0 : (nightDriving * 1.5);

            // Compute score breakdown using centralized SafetyScoreService
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

        // Sort descending by safety score, secondary by tripCount, tertiary by distance
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
                .code(dto.getCode())
                .name(dto.getName())
                .phone(dto.getPhone())
                .email(dto.getEmail())
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
        if (dto.getEmail() != null) driver.setEmail(dto.getEmail());
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
        dto.setEmail(driver.getEmail());
        dto.setLicenseNumber(driver.getLicenseNumber());
        dto.setJoinedOn(driver.getJoinedOn());
        dto.setActive(driver.getActive());
        dto.setCreatedAt(driver.getCreatedAt());
        return dto;
    }
}
