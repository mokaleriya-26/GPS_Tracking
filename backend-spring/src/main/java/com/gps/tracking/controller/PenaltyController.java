package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.security.SecurityUtils;
import com.gps.tracking.service.PenaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/penalties")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Penalties", description = "Driver penalty configuration and management")
public class PenaltyController {

    private final PenaltyService penaltyService;

    @GetMapping("/configs")
    @Operation(summary = "Get configured alert penalty weights and fines (Admin only)")
    public ResponseEntity<ApiResponse<List<AlertPenaltyConfigDTO>>> getConfigs() {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getAlertPenaltyConfigs()));
    }

    @PutMapping("/configs/{id}")
    @Operation(summary = "Update alert penalty weight, default fine, increment, threshold (Admin only)")
    public ResponseEntity<ApiResponse<AlertPenaltyConfigDTO>> updateConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "MANAGER") String role,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Fleet Manager") String user) {
        SecurityUtils.requireAdmin();

        BigDecimal weight = body.containsKey("weight") && body.get("weight") != null
                ? new BigDecimal(body.get("weight").toString()) : null;
        BigDecimal fine = body.containsKey("defaultFineAmount") && body.get("defaultFineAmount") != null
                ? new BigDecimal(body.get("defaultFineAmount").toString()) : null;
        BigDecimal increment = body.containsKey("penaltyIncrement") && body.get("penaltyIncrement") != null
                ? new BigDecimal(body.get("penaltyIncrement").toString()) : null;
        Integer threshold = body.containsKey("warningThreshold") && body.get("warningThreshold") != null
                ? Integer.valueOf(body.get("warningThreshold").toString()) : null;
        Boolean enabled = body.containsKey("penaltyEnabled") && body.get("penaltyEnabled") != null
                ? Boolean.valueOf(body.get("penaltyEnabled").toString()) : null;

        return ResponseEntity.ok(ApiResponse.ok(
                penaltyService.updateAlertPenaltyConfig(id, weight, fine, increment, threshold, enabled, user)));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get fleet-level progressive penalty analytics summary (Admin only)")
    public ResponseEntity<ApiResponse<FleetPenaltySummaryDTO>> getFleetSummary() {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getFleetPenaltySummary()));
    }

    @GetMapping
    @Operation(summary = "Get all penalties (Admin: fleet-wide; Driver: own penalties)")
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getAllPenalties() {
        if (SecurityUtils.isDriver()) {
            Long driverId = SecurityUtils.getCurrentDriverId();
            return ResponseEntity.ok(ApiResponse.ok(penaltyService.getPenaltiesForDriverFiltered(driverId, null, null, null)));
        }
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getAllPenalties()));
    }

    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get penalty history for a driver with optional filters (Data isolation enforced)")
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getDriverPenalties(
            @PathVariable Long driverId,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime to) {
        SecurityUtils.validateDriverAccess(driverId);
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getPenaltiesForDriverFiltered(driverId, alertType, from, to)));
    }

    @GetMapping("/driver/{driverId}/summary")
    @Operation(summary = "Get comprehensive penalty accounting summary for a driver (Data isolation enforced)")
    public ResponseEntity<ApiResponse<DriverPenaltySummaryDTO>> getDriverPenaltySummary(@PathVariable Long driverId) {
        SecurityUtils.validateDriverAccess(driverId);
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getDriverPenaltySummary(driverId)));
    }

    @PostMapping
    @Operation(summary = "Record a new driver penalty (Admin only)")
    public ResponseEntity<ApiResponse<PenaltyDTO>> recordPenalty(
            @RequestBody PenaltyRequestDTO req,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "MANAGER") String role,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Fleet Manager") String user) {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.recordPenalty(req, user)));
    }

    @PutMapping("/{id}/revoke")
    @Operation(summary = "Revoke an existing penalty with reason (Admin only)")
    public ResponseEntity<ApiResponse<PenaltyDTO>> revokePenalty(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "MANAGER") String role,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Fleet Manager") String user) {
        SecurityUtils.requireAdmin();
        String reason = body.getOrDefault("reason", "Revoked by Fleet Manager");
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.revokePenalty(id, reason, user)));
    }
}
