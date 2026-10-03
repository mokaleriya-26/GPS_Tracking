package com.gps.tracking.service;

import com.gps.tracking.entity.MaintenanceRecord;
import com.gps.tracking.repository.MaintenanceRepository;
import com.gps.tracking.repository.VehicleRepository;
import com.gps.tracking.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceService {
    private final MaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;

    public List<MaintenanceRecord> getByVehicle(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) throw new ResourceNotFoundException("Vehicle", vehicleId);
        return maintenanceRepository.findByVehicleIdOrderByServicedOnDesc(vehicleId);
    }

    public List<MaintenanceRecord> getAll() { return maintenanceRepository.findAll(); }

    @Transactional
    public MaintenanceRecord save(MaintenanceRecord record) { return maintenanceRepository.save(record); }
}
