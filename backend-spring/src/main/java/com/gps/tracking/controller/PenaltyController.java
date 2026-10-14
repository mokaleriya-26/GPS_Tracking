package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
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
    @Operation(summary = "Get configured alert penalty weights and fines")
    public ResponseEntity<ApiResponse<List<AlertPenaltyConfigDTO>>> getConfigs() {
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getAlertPenaltyConfigs()));
    }

    @PutMapping("/configs/{id}")
    @Operation(summary = "Update alert penalty weight and default fine (Manager only)")
    public ResponseEntity<ApiResponse<AlertPenaltyConfigDTO>> updateConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "MANAGER") String role,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Fleet Manager") String user) {

        BigDecimal weight = body.containsKey("weight") && body.get("weight") != null
                ? new BigDecimal(body.get("weight").toString()) : null;
        BigDecimal fine = body.containsKey("defaultFineAmount") && body.get("defaultFineAmount") != null
                ? new BigDecimal(body.get("defaultFineAmount").toString()) : null;

        return ResponseEntity.ok(ApiResponse.ok(penaltyService.updateAlertPenaltyConfig(id, weight, fine, user)));
    }

    @GetMapping
    @Operation(summary = "Get all driver penalties")
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getAllPenalties() {
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getAllPenalties()));
    }

    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get penalty history for a driver")
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getDriverPenalties(@PathVariable Long driverId) {
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getPenaltiesForDriver(driverId)));
    }

    @PostMapping
    @Operation(summary = "Record a new driver penalty (Manager only)")
    public ResponseEntity<ApiResponse<PenaltyDTO>> recordPenalty(
            @RequestBody PenaltyRequestDTO req,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "MANAGER") String role,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Fleet Manager") String user) {

        return ResponseEntity.ok(ApiResponse.ok(penaltyService.recordPenalty(req, user)));
    }

    @PutMapping("/{id}/revoke")
    @Operation(summary = "Revoke an existing penalty with reason (Manager only)")
    public ResponseEntity<ApiResponse<PenaltyDTO>> revokePenalty(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "MANAGER") String role,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Fleet Manager") String user) {

        String reason = body.getOrDefault("reason", "Revoked by Fleet Manager");
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.revokePenalty(id, reason, user)));
    }
}
