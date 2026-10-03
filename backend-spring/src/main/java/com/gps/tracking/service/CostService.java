package com.gps.tracking.service;

import com.gps.tracking.entity.CostRecord;
import com.gps.tracking.entity.Vehicle;
import com.gps.tracking.repository.CostRepository;
import com.gps.tracking.repository.VehicleRepository;
import com.gps.tracking.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CostService {
    private final CostRepository costRepository;
    private final VehicleRepository vehicleRepository;

    public List<CostRecord> getByVehicle(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) throw new ResourceNotFoundException("Vehicle", vehicleId);
        return costRepository.findByVehicleIdOrderByIncurredOnDesc(vehicleId);
    }

    public List<CostRecord> getAll() { return costRepository.findAll(); }

    @Transactional
    public CostRecord save(CostRecord record) { return costRepository.save(record); }
}
