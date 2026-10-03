package com.gps.tracking.controller;

import com.gps.tracking.dto.ApiResponse;
import com.gps.tracking.entity.MaintenanceRecord;
import com.gps.tracking.service.MaintenanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
@Tag(name = "Maintenance", description = "Vehicle maintenance and service records")
public class MaintenanceController {
    private final MaintenanceService maintenanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaintenanceRecord>>> getAll(
            @RequestParam(required = false) Long vehicleId) {
        if (vehicleId != null) return ResponseEntity.ok(ApiResponse.ok(maintenanceService.getByVehicle(vehicleId)));
        return ResponseEntity.ok(ApiResponse.ok(maintenanceService.getAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MaintenanceRecord>> create(@RequestBody MaintenanceRecord record) {
        return ResponseEntity.ok(ApiResponse.ok(maintenanceService.save(record), "Maintenance record created"));
    }
}
