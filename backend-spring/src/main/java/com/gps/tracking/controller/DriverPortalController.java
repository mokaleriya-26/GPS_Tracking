package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.service.DriverPortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/driver-portal")
@RequiredArgsConstructor
@Tag(name = "Driver Portal", description = "Personal portal endpoints exclusively for authenticated drivers")
public class DriverPortalController {

    private final DriverPortalService driverPortalService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get personal dashboard overview for the authenticated driver")
    public ResponseEntity<ApiResponse<DriverDashboardDTO>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(driverPortalService.getDashboard()));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get personal driver profile for the authenticated driver")
    public ResponseEntity<ApiResponse<DriverDTO>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok(driverPortalService.getProfile()));
    }

    @GetMapping("/vehicles")
    @Operation(summary = "Get vehicles assigned or historically operated by the authenticated driver")
    public ResponseEntity<ApiResponse<List<DriverVehicleDTO>>> getVehicles() {
        return ResponseEntity.ok(ApiResponse.ok(driverPortalService.getVehicles()));
    }

    @GetMapping("/alerts")
    @Operation(summary = "Get paginated alerts for the authenticated driver")
    public ResponseEntity<ApiResponse<Page<AlertDTO>>> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(driverPortalService.getAlerts(PageRequest.of(page, size))));
    }

    @GetMapping("/penalties")
    @Operation(summary = "Get personal penalty history for the authenticated driver")
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getPenalties(
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.ok(driverPortalService.getPenalties(alertType, from, to)));
    }

    @GetMapping("/penalties/summary")
    @Operation(summary = "Get personal penalty accounting summary for the authenticated driver")
    public ResponseEntity<ApiResponse<DriverPenaltySummaryDTO>> getPenaltySummary() {
        return ResponseEntity.ok(ApiResponse.ok(driverPortalService.getPenaltySummary()));
    }

    @GetMapping("/trips")
    @Operation(summary = "Get paginated trip history for the authenticated driver")
    public ResponseEntity<ApiResponse<Page<TripDTO>>> getTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(driverPortalService.getTrips(PageRequest.of(page, size))));
    }

    @PostMapping("/reports/generate")
    @Operation(summary = "Generate a driver performance or alert report exclusively for the authenticated driver")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generateReport(@RequestBody ReportRequestDTO req) {
        return ResponseEntity.ok(ApiResponse.ok(driverPortalService.generateReport(req)));
    }
}
