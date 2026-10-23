package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.security.SecurityUtils;
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
    @Operation(summary = "List all alerts (paginated, newest first; filtered by driver if role is USER)")
    public ResponseEntity<ApiResponse<Page<AlertDTO>>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size);

        if (SecurityUtils.isDriver()) {
            Long currentDriverId = SecurityUtils.getCurrentDriverId();
            if (driverId != null) {
                SecurityUtils.validateDriverAccess(driverId);
            }
            Page<AlertDTO> driverAlerts = alertService.getByDriver(currentDriverId, pageable);
            return ResponseEntity.ok(ApiResponse.ok(driverAlerts));
        }

        Page<AlertDTO> alerts;
        if (vehicleId != null) alerts = alertService.getByVehicle(vehicleId, pageable);
        else if (driverId != null) alerts = alertService.getByDriver(driverId, pageable);
        else if (status != null) alerts = alertService.getByStatus(status, pageable);
        else alerts = alertService.getAll(pageable);
        return ResponseEntity.ok(ApiResponse.ok(alerts));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get overall alert count statistics")
    public ResponseEntity<ApiResponse<AlertSummaryDTO>> getSummary() {
        if (SecurityUtils.isDriver()) {
            SecurityUtils.requireAdmin();
        }
        return ResponseEntity.ok(ApiResponse.ok(alertService.getAlertSummary()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get alert by ID")
    public ResponseEntity<ApiResponse<AlertDTO>> getById(@PathVariable Long id) {
        AlertDTO alert = alertService.getById(id);
        if (SecurityUtils.isDriver() && alert.getDriverId() != null) {
            SecurityUtils.validateDriverAccess(alert.getDriverId());
        }
        return ResponseEntity.ok(ApiResponse.ok(alert));
    }

    @PostMapping
    @Operation(summary = "Create a new alert and trigger notifications (Admin only)")
    public ResponseEntity<ApiResponse<AlertDTO>> createAlert(@RequestBody AlertDTO alertDTO) {
        SecurityUtils.requireAdmin();
        AlertDTO created = alertService.createFromDTO(alertDTO);
        return ResponseEntity.ok(ApiResponse.ok(created, "Alert created and notifications processed"));
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "Resolve an alert (Admin only)")
    public ResponseEntity<ApiResponse<AlertDTO>> resolve(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Fleet Admin") String resolvedBy) {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(alertService.resolveAlert(id, resolvedBy), "Alert resolved"));
    }
}
