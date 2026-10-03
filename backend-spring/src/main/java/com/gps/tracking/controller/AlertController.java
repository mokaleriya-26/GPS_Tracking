package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Fleet alert management with notification tracking")
public class AlertController {
    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "List all alerts (paginated, newest first)")
    public ResponseEntity<ApiResponse<Page<AlertDTO>>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AlertDTO> alerts;
        if (vehicleId != null) alerts = alertService.getByVehicle(vehicleId, pageable);
        else if (driverId != null) alerts = alertService.getByDriver(driverId, pageable);
        else if (status != null) alerts = alertService.getByStatus(status, pageable);
        else alerts = alertService.getAll(pageable);
        return ResponseEntity.ok(ApiResponse.ok(alerts));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get alert by ID")
    public ResponseEntity<ApiResponse<AlertDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.getById(id)));
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "Resolve an alert")
    public ResponseEntity<ApiResponse<AlertDTO>> resolve(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Fleet Admin") String resolvedBy) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.resolveAlert(id, resolvedBy), "Alert resolved"));
    }
}
