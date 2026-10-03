package com.gps.tracking.service;

import com.gps.tracking.entity.Telemetry;
import com.gps.tracking.repository.TelemetryRepository;
import com.gps.tracking.repository.VehicleRepository;
import com.gps.tracking.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelemetryService {
    private final TelemetryRepository telemetryRepository;
    private final VehicleRepository vehicleRepository;

    public Page<Telemetry> getByVehicle(Long vehicleId, Pageable pageable) {
        if (!vehicleRepository.existsById(vehicleId))
            throw new ResourceNotFoundException("Vehicle", vehicleId);
        return telemetryRepository.findByVehicleIdOrderByRecordedAtDesc(vehicleId, pageable);
    }

    public List<Telemetry> getByVehicleAndRange(Long vehicleId, LocalDateTime from, LocalDateTime to) {
        return telemetryRepository.findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtAsc(vehicleId, from, to);
    }

    @Transactional
    public Telemetry save(Telemetry telemetry) {
        return telemetryRepository.save(telemetry);
    }
}
