package com.gps.tracking.service;

import com.gps.tracking.dto.VehicleDTO;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.exception.ResourceNotFoundException;
import com.gps.tracking.repository.VehicleRepository;
import com.gps.tracking.repository.DriverRepository;
import com.gps.tracking.repository.TelemetryRepository;
import com.gps.tracking.entity.Telemetry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TelemetryRepository telemetryRepository;

    public List<VehicleDTO> getAllVehicles() {
        return vehicleRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public VehicleDTO getById(Long id) {
        return toDTO(vehicleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id)));
    }

    public VehicleDTO getByCode(String code) {
        return toDTO(vehicleRepository.findByCode(code)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "code", code)));
    }

    public VehicleDTO getByRegistration(String reg) {
        return toDTO(vehicleRepository.findByRegistrationNumber(reg)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "registration", reg)));
    }

    @Transactional
    public VehicleDTO createVehicle(VehicleDTO dto) {
        Driver driver = dto.getDriverId() != null ?
            driverRepository.findById(dto.getDriverId()).orElse(null) : null;
        Vehicle v = Vehicle.builder()
            .code(dto.getCode()).imei(dto.getImei())
            .registrationNumber(dto.getRegistrationNumber())
            .vehicleType(dto.getVehicleType()).makeModel(dto.getMakeModel())
            .fuelType(dto.getFuelType()).tankCapacityLitres(dto.getTankCapacityLitres())
            .ratedMileageKmpl(dto.getRatedMileageKmpl()).odometerKm(dto.getOdometerKm())
            .driver(driver).active(dto.getActive() != null ? dto.getActive() : true)
            .build();
        return toDTO(vehicleRepository.save(v));
    }

    @Transactional
    public VehicleDTO updateVehicle(Long id, VehicleDTO dto) {
        Vehicle v = vehicleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
        if (dto.getVehicleType() != null) v.setVehicleType(dto.getVehicleType());
        if (dto.getMakeModel() != null) v.setMakeModel(dto.getMakeModel());
        if (dto.getActive() != null) v.setActive(dto.getActive());
        if (dto.getDriverId() != null) {
            Driver drv = driverRepository.findById(dto.getDriverId()).orElse(null);
            v.setDriver(drv);
        }
        return toDTO(vehicleRepository.save(v));
    }

    private VehicleDTO toDTO(Vehicle v) {
        VehicleDTO dto = new VehicleDTO();
        dto.setId(v.getId()); dto.setCode(v.getCode()); dto.setImei(v.getImei());
        dto.setRegistrationNumber(v.getRegistrationNumber());
        dto.setVehicleType(v.getVehicleType()); dto.setMakeModel(v.getMakeModel());
        dto.setFuelType(v.getFuelType()); dto.setTankCapacityLitres(v.getTankCapacityLitres());
        dto.setRatedMileageKmpl(v.getRatedMileageKmpl()); dto.setOdometerKm(v.getOdometerKm());
        dto.setActive(v.getActive()); dto.setCreatedAt(v.getCreatedAt());
        if (v.getDriver() != null) {
            dto.setDriverId(v.getDriver().getId());
            dto.setDriverName(v.getDriver().getName());
            dto.setDriverCode(v.getDriver().getCode());
        }
        // Attach latest telemetry
        telemetryRepository.findTopByVehicleIdOrderByRecordedAtDesc(v.getId()).ifPresent(t -> {
            dto.setLastSpeedKmph(t.getSpeedKmph());
            dto.setLastIgnition(t.getIgnition());
            dto.setLastSeenAt(t.getRecordedAt());
            if (t.getRoadName() != null) dto.setLastLocation(t.getRoadName());
        });
        return dto;
    }
}
