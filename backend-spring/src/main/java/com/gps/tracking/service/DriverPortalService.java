package com.gps.tracking.service;

import com.gps.tracking.dto.*;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.entity.Trip;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.*;
import com.gps.tracking.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverPortalService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;
    private final DriverPenaltyRepository driverPenaltyRepository;
    private final DriverService driverService;
    private final TripService tripService;
    private final AlertService alertService;
    private final PenaltyService penaltyService;
    private final ReportService reportService;
    private final SafetyScoreService safetyScoreService;

    @Transactional(readOnly = true)
    public DriverDashboardDTO getDashboard() {
        Long driverId = SecurityUtils.getCurrentDriverId();
        if (driverId == null) {
            throw new ResourceNotFoundException("No driver associated with the current session.");
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));
        DriverDTO driverDTO = driverService.getDriverById(driverId);

        // Currently assigned vehicles
        List<Vehicle> assigned = vehicleRepository.findByDriverId(driverId);
        List<VehicleDTO> assignedDTOs = assigned.stream()
                .map(this::toVehicleDTO)
                .collect(Collectors.toList());

        // Trips statistics
        long totalTrips = tripRepository.countByDriverId(driverId);
        Page<TripDTO> recentTripsPage = tripService.getByDriver(driverId, PageRequest.of(0, 5));
        String latestTripStatus = "NO TRIPS RECORDED";
        if (!recentTripsPage.isEmpty()) {
            latestTripStatus = recentTripsPage.getContent().get(0).getStatus();
        }

        // Alerts statistics
        long totalAlerts = alertRepository.countAlertsByDriverWithFilters(driverId, null, null, null, null);
        Page<AlertDTO> recentAlertsPage = alertService.getByDriver(driverId, PageRequest.of(0, 5));

        // Penalty statistics
        List<PenaltyDTO> allPenalties = penaltyService.getPenaltiesForDriverFiltered(driverId, null, null, null);
        long totalPenalties = allPenalties.size();
        BigDecimal totalPenaltyAmount = driverPenaltyRepository.sumActiveMonetaryAmountByDriverId(driverId);
        if (totalPenaltyAmount == null) totalPenaltyAmount = BigDecimal.ZERO;

        List<PenaltyDTO> recentPenalties = allPenalties.stream()
                .limit(5)
                .collect(Collectors.toList());

        // Safety score (last 6 months or all-time)
        Double safetyScore = calculateCurrentSafetyScore(driver);

        return DriverDashboardDTO.builder()
                .driver(driverDTO)
                .assignedVehicles(assignedDTOs)
                .safetyScore(safetyScore)
                .totalTrips(totalTrips)
                .totalAlerts(totalAlerts)
                .latestTripStatus(latestTripStatus)
                .totalPenalties(totalPenalties)
                .totalPenaltyAmount(totalPenaltyAmount)
                .recentTrips(recentTripsPage.getContent())
                .recentAlerts(recentAlertsPage.getContent())
                .recentPenalties(recentPenalties)
                .build();
    }

    @Transactional(readOnly = true)
    public DriverDTO getProfile() {
        Long driverId = SecurityUtils.getCurrentDriverId();
        if (driverId == null) {
            throw new ResourceNotFoundException("No driver associated with the current session.");
        }
        return driverService.getDriverById(driverId);
    }

    @Transactional(readOnly = true)
    public List<DriverVehicleDTO> getVehicles() {
        Long driverId = SecurityUtils.getCurrentDriverId();
        if (driverId == null) {
            throw new ResourceNotFoundException("No driver associated with the current session.");
        }

        // Current assigned vehicles
        List<Vehicle> currentAssigned = vehicleRepository.findByDriverId(driverId);
        Set<Long> currentVehicleIds = currentAssigned.stream().map(Vehicle::getId).collect(Collectors.toSet());

        // Historical vehicles from trips
        List<Vehicle> historicalVehicles = tripRepository.findDistinctVehiclesByDriverId(driverId);

        Map<Long, DriverVehicleDTO> map = new LinkedHashMap<>();

        // 1. Add current vehicles
        for (Vehicle v : currentAssigned) {
            map.put(v.getId(), toDriverVehicleDTO(v, "CURRENT", driverId));
        }

        // 2. Add historical vehicles (that are not currently assigned)
        for (Vehicle v : historicalVehicles) {
            if (!currentVehicleIds.contains(v.getId())) {
                map.put(v.getId(), toDriverVehicleDTO(v, "HISTORICAL", driverId));
            }
        }

        return new ArrayList<>(map.values());
    }

    @Transactional(readOnly = true)
    public Page<AlertDTO> getAlerts(Pageable pageable) {
        Long driverId = SecurityUtils.getCurrentDriverId();
        if (driverId == null) {
            throw new ResourceNotFoundException("No driver associated with the current session.");
        }
        return alertService.getByDriver(driverId, pageable);
    }

    @Transactional(readOnly = true)
    public List<PenaltyDTO> getPenalties(String alertType, LocalDateTime from, LocalDateTime to) {
        Long driverId = SecurityUtils.getCurrentDriverId();
        if (driverId == null) {
            throw new ResourceNotFoundException("No driver associated with the current session.");
        }
        return penaltyService.getPenaltiesForDriverFiltered(driverId, alertType, from, to);
    }

    @Transactional(readOnly = true)
    public DriverPenaltySummaryDTO getPenaltySummary() {
        Long driverId = SecurityUtils.getCurrentDriverId();
        if (driverId == null) {
            throw new ResourceNotFoundException("No driver associated with the current session.");
        }
        return penaltyService.getDriverPenaltySummary(driverId);
    }

    @Transactional(readOnly = true)
    public Page<TripDTO> getTrips(Pageable pageable) {
        Long driverId = SecurityUtils.getCurrentDriverId();
        if (driverId == null) {
            throw new ResourceNotFoundException("No driver associated with the current session.");
        }
        return tripService.getByDriver(driverId, pageable);
    }

    @Transactional
    public ReportResponseDTO generateReport(ReportRequestDTO req) {
        Long driverId = SecurityUtils.getCurrentDriverId();
        if (driverId == null) {
            throw new ResourceNotFoundException("No driver associated with the current session.");
        }

        // Strictly lock driverId and driverCode to authenticated driver — prevent data leakage!
        req.setDriverId(driverId);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", driverId));
        req.setDriverCode(driver.getCode());

        // Validate vehicle if provided
        if (req.getVehicleId() != null) {
            boolean authorized = vehicleRepository.findByDriverId(driverId).stream().anyMatch(v -> v.getId().equals(req.getVehicleId()))
                    || tripRepository.findDistinctVehiclesByDriverId(driverId).stream().anyMatch(v -> v.getId().equals(req.getVehicleId()));
            if (!authorized) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Data isolation violation: You are not authorized to filter by this vehicle.");
            }
        }

        String type = req.getReportType() != null ? req.getReportType().toUpperCase() : "MONTHLY";
        req.setReportType(type);
        if ("ALERT_BASED".equals(type) || "ALERT".equals(type)) {
            return reportService.generateDriverAlertReport(req);
        } else {
            return reportService.generateDriverReport(req);
        }
    }

    private Double calculateCurrentSafetyScore(Driver driver) {
        try {
            LocalDate start = LocalDate.now().minusMonths(6);
            LocalDate end = LocalDate.now().plusDays(1);
            LocalDateTime dtFrom = start.atStartOfDay();
            LocalDateTime dtTo = end.atStartOfDay();

            List<Trip> trips = tripRepository.findTripsByDriverAndDateRange(driver.getId(), dtFrom, dtTo);
            List<com.gps.tracking.entity.Alert> alerts = alertRepository.findAlertsByDriverAndDateRange(driver.getId(), dtFrom, dtTo);

            int totalTrips = trips.size();
            double totalDistKm = trips.stream()
                    .mapToDouble(t -> t.getDistanceKm() != null ? t.getDistanceKm().doubleValue() : 0.0)
                    .sum();

            long overspeed = alerts.stream().filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("speed")).count();
            long harshBrake = alerts.stream().filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("brak")).count();
            long harshAccel = alerts.stream().filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("accel")).count();
            long gpsDisconnect = alerts.stream().filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("disconnect")).count();

            SafetyScoreBreakdownDTO breakdown = safetyScoreService.calculateDriverScore(
                    driver, totalTrips, totalDistKm,
                    overspeed, harshBrake, harshAccel, gpsDisconnect,
                    0, 0.0, 0, dtFrom, dtTo);

            return breakdown != null && breakdown.getFinalSafetyScore() != null
                    ? breakdown.getFinalSafetyScore().doubleValue()
                    : 85.0;
        } catch (Exception e) {
            log.warn("Could not calculate safety score for driver {}: {}", driver.getId(), e.getMessage());
            return 85.0;
        }
    }

    private DriverVehicleDTO toDriverVehicleDTO(Vehicle v, String assignmentType, Long driverId) {
        long tripsCount = tripRepository.countByVehicleId(v.getId());
        Double dist = tripRepository.sumDistanceByVehicleId(v.getId());

        return DriverVehicleDTO.builder()
                .id(v.getId())
                .code(v.getCode())
                .registrationNumber(v.getRegistrationNumber())
                .vehicleType(v.getVehicleType())
                .makeModel(v.getMakeModel())
                .fuelType(v.getFuelType())
                .odometerKm(v.getOdometerKm() != null ? v.getOdometerKm().doubleValue() : 0.0)
                .assignmentType(assignmentType)
                .tripCount(tripsCount)
                .totalDistanceKm(dist != null ? dist : 0.0)
                .active(v.getActive())
                .build();
    }

    private VehicleDTO toVehicleDTO(Vehicle v) {
        VehicleDTO dto = new VehicleDTO();
        dto.setId(v.getId());
        dto.setCode(v.getCode());
        dto.setRegistrationNumber(v.getRegistrationNumber());
        dto.setVehicleType(v.getVehicleType());
        dto.setMakeModel(v.getMakeModel());
        dto.setFuelType(v.getFuelType());
        dto.setOdometerKm(v.getOdometerKm());
        dto.setActive(v.getActive());
        return dto;
    }
}
