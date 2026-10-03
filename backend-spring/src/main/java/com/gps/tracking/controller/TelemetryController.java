package com.gps.tracking.controller;

import com.gps.tracking.dto.ApiResponse;
import com.gps.tracking.entity.Telemetry;
import com.gps.tracking.service.TelemetryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
@Tag(name = "Telemetry", description = "GPS telemetry data management")
public class TelemetryController {
    private final TelemetryService telemetryService;

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<ApiResponse<Page<Telemetry>>> getByVehicle(
            @PathVariable Long vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            telemetryService.getByVehicle(vehicleId, PageRequest.of(page, size))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Telemetry>> createTelemetry(@RequestBody Telemetry telemetry) {
        return ResponseEntity.ok(ApiResponse.ok(telemetryService.save(telemetry), "Telemetry recorded"));
    }
}
