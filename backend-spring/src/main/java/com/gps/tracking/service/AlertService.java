package com.gps.tracking.service;

import com.gps.tracking.dto.AlertDTO;
import com.gps.tracking.entity.Alert;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.DriverPenaltyRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.TripRepository;
import com.gps.tracking.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {
    private final AlertRepository alertRepository;
    private final NotificationService notificationService;
    private final PenaltyService penaltyService;
    private final DriverPenaltyRepository driverPenaltyRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;

    public Page<AlertDTO> getAll(Pageable pageable) {
        return alertRepository.findByOrderByOccurredAtDesc(pageable).map(this::toDTO);
    }

    public Page<AlertDTO> getByVehicle(Long vehicleId, Pageable pageable) {
        return alertRepository.findByVehicleIdOrderByOccurredAtDesc(vehicleId, pageable).map(this::toDTO);
    }

    public Page<AlertDTO> getByDriver(Long driverId, Pageable pageable) {
        return alertRepository.findByDriverIdOrderByOccurredAtDesc(driverId, pageable).map(this::toDTO);
    }

    public Page<AlertDTO> getByStatus(String status, Pageable pageable) {
        return alertRepository.findByStatusOrderByOccurredAtDesc(status, pageable).map(this::toDTO);
    }

    public List<AlertDTO> getByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to) {
        return alertRepository.findByDriverIdAndOccurredAtBetween(driverId, from, to)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public AlertDTO getById(Long id) {
        return toDTO(alertRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Alert", id)));
    }

    @Transactional
    public AlertDTO resolveAlert(Long id, String resolvedBy) {
        Alert alert = alertRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Alert", id));
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(resolvedBy);
        return toDTO(alertRepository.save(alert));
    }

    @Transactional
    public Alert createAlert(Alert alert) {
        if (alert.getOccurredAt() == null) {
            alert.setOccurredAt(LocalDateTime.now());
        }
        if (alert.getStatus() == null) {
            alert.setStatus("OPEN");
        }
        if (alert.getDeliveryStatus() == null) {
            alert.setDeliveryStatus("PENDING");
        }
        Alert saved = alertRepository.save(alert);

        // Only genuinely NEW and non-historical alerts trigger notifications and progressive penalties
        if (!"HISTORICAL".equalsIgnoreCase(saved.getStatus()) && !"SILENCED".equalsIgnoreCase(saved.getDeliveryStatus())) {
            penaltyService.processAlertPenalty(saved);
            notificationService.processAlert(saved, List.of(saved.getAlertType()), false);
        }
        return saved;
    }

    @Transactional
    public AlertDTO createFromDTO(AlertDTO dto) {
        Alert.AlertBuilder b = Alert.builder()
                .reference(dto.getReference() != null ? dto.getReference() : "ALERT_" + System.currentTimeMillis())
                .alertType(dto.getAlertType())
                .severity(dto.getSeverity() != null ? dto.getSeverity() : "WARNING")
                .status(dto.getStatus() != null ? dto.getStatus() : "OPEN")
                .occurredAt(dto.getOccurredAt() != null ? dto.getOccurredAt() : LocalDateTime.now())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .speedKmph(dto.getSpeedKmph())
                .speedLimitKmph(dto.getSpeedLimitKmph())
                .roadName(dto.getRoadName())
                .accelerationMps2(dto.getAccelerationMps2())
                .message(dto.getMessage())
                .isSimulation(Boolean.TRUE.equals(dto.getIsSimulation()))
                .deliveryStatus(dto.getDeliveryStatus() != null ? dto.getDeliveryStatus() : "PENDING");

        if (dto.getVehicleId() != null) {
            vehicleRepository.findById(dto.getVehicleId()).ifPresent(b::vehicle);
        }
        if (dto.getDriverId() != null) {
            driverRepository.findById(dto.getDriverId()).ifPresent(b::driver);
        }
        if (dto.getTripId() != null) {
            tripRepository.findById(dto.getTripId()).ifPresent(b::trip);
        }

        Alert saved = createAlert(b.build());
        return toDTO(saved);
    }

    @Transactional
    public Alert saveAlert(Alert alert) {
        return createAlert(alert);
    }

    public long countOpenAlerts() { return alertRepository.countOpenAlerts(); }

    private AlertDTO toDTO(Alert a) {
        AlertDTO dto = new AlertDTO();
        dto.setId(a.getId()); dto.setReference(a.getReference());
        dto.setAlertType(a.getAlertType()); dto.setSeverity(a.getSeverity());
        if (a.getVehicle() != null) {
            dto.setVehicleId(a.getVehicle().getId());
            dto.setVehicleCode(a.getVehicle().getCode());
            dto.setVehicleRegistrationNumber(a.getVehicle().getRegistrationNumber());
        }
        if (a.getDriver() != null) {
            dto.setDriverId(a.getDriver().getId());
            dto.setDriverName(a.getDriver().getName());
            dto.setDriverCode(a.getDriver().getCode());
            dto.setDriverPhone(a.getDriver().getPhone());
            dto.setDriverEmail(a.getDriver().getEmail());
        }
        if (Boolean.TRUE.equals(a.getIsSimulation())) {
            dto.setDriverName(NotificationService.SIMULATION_DRIVER_NAME);
            dto.setDriverEmail(NotificationService.SIMULATION_DRIVER_EMAIL);
        }
        if (a.getTrip() != null) dto.setTripId(a.getTrip().getId());
        dto.setOccurredAt(a.getOccurredAt());
        dto.setLatitude(a.getLatitude()); dto.setLongitude(a.getLongitude());
        dto.setSpeedKmph(a.getSpeedKmph()); dto.setSpeedLimitKmph(a.getSpeedLimitKmph());
        dto.setRoadName(a.getRoadName()); dto.setAccelerationMps2(a.getAccelerationMps2());
        dto.setMessage(a.getMessage()); dto.setStatus(a.getStatus());
        dto.setResolvedAt(a.getResolvedAt()); dto.setResolvedBy(a.getResolvedBy());
        // Trip context (preserved from old system)
        dto.setTripStartTime(a.getTripStartTime()); dto.setTripEndTime(a.getTripEndTime());
        dto.setTripDistanceKm(a.getTripDistanceKm()); dto.setTripDurationMin(a.getTripDurationMin());
        dto.setMinSpeedKmph(a.getMinSpeedKmph()); dto.setMaxSpeedKmph(a.getMaxSpeedKmph());
        // Notification delivery (from old NotificationLog)
        dto.setDeliveryStatus(a.getDeliveryStatus()); dto.setDeliveryAttempts(a.getDeliveryAttempts());
        dto.setEmailSent(a.getEmailSent()); dto.setFleetEmailSent(a.getFleetEmailSent());
        dto.setManagerEmailSent(a.getManagerEmailSent()); dto.setDriverEmailSent(a.getDriverEmailSent());
        dto.setEmailError(a.getEmailError());
        dto.setSmsSent(a.getSmsSent()); dto.setFleetSmsSent(a.getFleetSmsSent());
        dto.setFleetSmsRequestId(a.getFleetSmsRequestId());
        dto.setManagerSmsSent(a.getManagerSmsSent()); dto.setManagerSmsRequestId(a.getManagerSmsRequestId());
        dto.setDriverSmsSent(a.getDriverSmsSent()); dto.setDriverSmsRequestId(a.getDriverSmsRequestId());
        dto.setSmsError(a.getSmsError());
        // Map HISTORICAL status to isSilenced=true (backwards compat with old frontend)
        dto.setIsSilenced("HISTORICAL".equals(a.getStatus()));
        dto.setIsSimulation(a.getIsSimulation());
        dto.setCreatedAt(a.getCreatedAt());

        // Attach progressive warning and penalty information if exists
        if (a.getId() != null) {
            List<com.gps.tracking.entity.DriverPenalty> penalties = driverPenaltyRepository.findByAlertId(a.getId());
            if (!penalties.isEmpty()) {
                com.gps.tracking.entity.DriverPenalty p = penalties.get(0);
                dto.setOccurrenceNumber(p.getOccurrenceNumber());
                dto.setPenaltyAmount(p.getMonetaryAmount());
                dto.setPenaltyClassification(p.getClassification());
                if (p.getDriver() != null && p.getDriver().getId() != null) {
                    dto.setDriverCumulativePenalties(driverPenaltyRepository.sumActiveMonetaryAmountByDriverId(p.getDriver().getId()));
                }
            }
        }

        return dto;
    }

    public com.gps.tracking.dto.AlertSummaryDTO getAlertSummary() {
        long total = alertRepository.count();
        long open = alertRepository.countOpenAlerts();
        long historical = alertRepository.countHistoricalAlerts();
        long critical = alertRepository.countBySeverity("CRITICAL");
        long warning = alertRepository.countBySeverity("WARNING");
        return com.gps.tracking.dto.AlertSummaryDTO.builder()
                .totalAlerts(total)
                .openAlerts(open)
                .historicalAlerts(historical)
                .criticalAlerts(critical)
                .warningAlerts(warning)
                .build();
    }
}
