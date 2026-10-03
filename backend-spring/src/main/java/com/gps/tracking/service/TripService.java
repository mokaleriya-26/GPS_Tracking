package com.gps.tracking.service;

import com.gps.tracking.dto.TripDTO;
import com.gps.tracking.entity.Trip;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.TripRepository;
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
public class TripService {
    private final TripRepository tripRepository;

    public Page<TripDTO> getAll(Pageable pageable) {
        return tripRepository.findAll(pageable).map(this::toDTO);
    }

    public TripDTO getById(Long id) {
        return toDTO(tripRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Trip", id)));
    }

    public Page<TripDTO> getByVehicle(Long vehicleId, Pageable pageable) {
        return tripRepository.findByVehicleIdOrderByStartTimeDesc(vehicleId, pageable).map(this::toDTO);
    }

    public Page<TripDTO> getByDriver(Long driverId, Pageable pageable) {
        return tripRepository.findByDriverIdOrderByStartTimeDesc(driverId, pageable).map(this::toDTO);
    }

    public List<TripDTO> getByDriverAndDateRange(Long driverId, LocalDateTime from, LocalDateTime to) {
        return tripRepository.findByDriverIdAndStartTimeBetweenOrderByStartTimeDesc(driverId, from, to)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    private TripDTO toDTO(Trip t) {
        TripDTO dto = new TripDTO();
        dto.setId(t.getId());
        if (t.getVehicle() != null) { dto.setVehicleId(t.getVehicle().getId()); dto.setVehicleCode(t.getVehicle().getCode()); dto.setVehicleRegistrationNumber(t.getVehicle().getRegistrationNumber()); }
        if (t.getDriver() != null) { dto.setDriverId(t.getDriver().getId()); dto.setDriverName(t.getDriver().getName()); dto.setDriverCode(t.getDriver().getCode()); }
        dto.setStartTime(t.getStartTime()); dto.setEndTime(t.getEndTime()); dto.setStatus(t.getStatus());
        dto.setStartLatitude(t.getStartLatitude()); dto.setStartLongitude(t.getStartLongitude());
        dto.setEndLatitude(t.getEndLatitude()); dto.setEndLongitude(t.getEndLongitude());
        dto.setDistanceKm(t.getDistanceKm()); dto.setDurationSeconds(t.getDurationSeconds());
        dto.setMovingSeconds(t.getMovingSeconds()); dto.setIdleSeconds(t.getIdleSeconds());
        dto.setMaxSpeedKmph(t.getMaxSpeedKmph()); dto.setMinSpeedKmph(t.getMinSpeedKmph());
        dto.setAverageSpeedKmph(t.getAverageSpeedKmph());
        dto.setOverspeedEvents(t.getOverspeedEvents()); dto.setHarshBrakingEvents(t.getHarshBrakingEvents());
        dto.setHarshAccelerationEvents(t.getHarshAccelerationEvents());
        dto.setNightDrivingSeconds(t.getNightDrivingSeconds()); dto.setAlertCount(t.getAlertCount());
        dto.setPrimaryRoad(t.getPrimaryRoad()); dto.setCreatedAt(t.getCreatedAt());
        return dto;
    }
}
