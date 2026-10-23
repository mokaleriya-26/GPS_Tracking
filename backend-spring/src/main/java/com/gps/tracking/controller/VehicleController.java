package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.security.SecurityUtils;
import com.gps.tracking.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Vehicle management and tracking")
public class VehicleController {
    private final VehicleService vehicleService;

    @GetMapping
    @Operation(summary = "List all vehicles (with live telemetry; filtered by driver if USER role)")
    public ResponseEntity<ApiResponse<List<VehicleDTO>>> getAllVehicles(
            @RequestParam(required = false) Boolean active) {
        List<VehicleDTO> all = vehicleService.getAllVehicles();
        if (SecurityUtils.isDriver()) {
            Long driverId = SecurityUtils.getCurrentDriverId();
            List<VehicleDTO> driverVehicles = all.stream()
                    .filter(v -> driverId != null && driverId.equals(v.getDriverId()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.ok(driverVehicles));
        }
        return ResponseEntity.ok(ApiResponse.ok(all));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDTO>> getById(@PathVariable Long id) {
        VehicleDTO v = vehicleService.getById(id);
        if (SecurityUtils.isDriver() && v.getDriverId() != null) {
            SecurityUtils.validateDriverAccess(v.getDriverId());
        }
        return ResponseEntity.ok(ApiResponse.ok(v));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<VehicleDTO>> getByCode(@PathVariable String code) {
        VehicleDTO v = vehicleService.getByCode(code);
        if (SecurityUtils.isDriver() && v.getDriverId() != null) {
            SecurityUtils.validateDriverAccess(v.getDriverId());
        }
        return ResponseEntity.ok(ApiResponse.ok(v));
    }

    @GetMapping("/registration/{reg}")
    public ResponseEntity<ApiResponse<VehicleDTO>> getByRegistration(@PathVariable String reg) {
        VehicleDTO v = vehicleService.getByRegistration(reg);
        if (SecurityUtils.isDriver() && v.getDriverId() != null) {
            SecurityUtils.validateDriverAccess(v.getDriverId());
        }
        return ResponseEntity.ok(ApiResponse.ok(v));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleDTO>> createVehicle(@RequestBody VehicleDTO dto) {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.createVehicle(dto), "Vehicle created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDTO>> updateVehicle(@PathVariable Long id, @RequestBody VehicleDTO dto) {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.updateVehicle(id, dto), "Vehicle updated"));
    }
}
