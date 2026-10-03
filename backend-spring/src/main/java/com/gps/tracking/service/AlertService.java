package com.gps.tracking.service;

import com.gps.tracking.dto.AlertDTO;
import com.gps.tracking.entity.Alert;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.AlertRepository;
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
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
