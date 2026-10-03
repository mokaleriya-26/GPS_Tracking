package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Vehicle management and tracking")
public class VehicleController {
    private final VehicleService vehicleService;

    @GetMapping
    @Operation(summary = "List all vehicles (with live telemetry)")
    public ResponseEntity<ApiResponse<List<VehicleDTO>>> getAllVehicles(
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.getAllVehicles()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.getById(id)));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<VehicleDTO>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.getByCode(code)));
    }

    @GetMapping("/registration/{reg}")
    public ResponseEntity<ApiResponse<VehicleDTO>> getByRegistration(@PathVariable String reg) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.getByRegistration(reg)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleDTO>> createVehicle(@RequestBody VehicleDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.createVehicle(dto), "Vehicle created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleDTO>> updateVehicle(@PathVariable Long id, @RequestBody VehicleDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.updateVehicle(id, dto), "Vehicle updated"));
    }
}
