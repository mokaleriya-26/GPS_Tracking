package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.security.SecurityUtils;
import com.gps.tracking.service.DriverService;
import com.gps.tracking.service.PenaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Drivers", description = "Driver management and performance analytics")
public class DriverController {

    private final DriverService driverService;
    private final PenaltyService penaltyService;

    @GetMapping
    @Operation(summary = "List all drivers (Admin: all drivers; Driver: only authenticated driver)")
    public ResponseEntity<ApiResponse<List<DriverDTO>>> getAllDrivers(
            @RequestParam(required = false) Boolean active) {
        if (SecurityUtils.isDriver()) {
            Long driverId = SecurityUtils.getCurrentDriverId();
            DriverDTO self = driverService.getDriverById(driverId);
            return ResponseEntity.ok(ApiResponse.ok(Collections.singletonList(self)));
        }
        List<DriverDTO> drivers = active != null && active ? driverService.getActiveDrivers() : driverService.getAllDrivers();
        return ResponseEntity.ok(ApiResponse.ok(drivers));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get driver by ID (Data isolation enforced)")
    public ResponseEntity<ApiResponse<DriverDTO>> getDriverById(@PathVariable Long id) {
        SecurityUtils.validateDriverAccess(id);
        return ResponseEntity.ok(ApiResponse.ok(driverService.getDriverById(id)));
    }

    @GetMapping("/{id}/penalties")
    @Operation(summary = "Get penalties for a specific driver (Data isolation enforced)")
    public ResponseEntity<ApiResponse<List<PenaltyDTO>>> getDriverPenalties(
            @PathVariable Long id,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime to) {
        SecurityUtils.validateDriverAccess(id);
        return ResponseEntity.ok(ApiResponse.ok(penaltyService.getPenaltiesForDriverFiltered(id, alertType, from, to)));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get driver by code (e.g. DRV001)")
    public ResponseEntity<ApiResponse<DriverDTO>> getDriverByCode(@PathVariable String code) {
        DriverDTO driver = driverService.getDriverByCode(code);
        SecurityUtils.validateDriverAccess(driver.getId());
        return ResponseEntity.ok(ApiResponse.ok(driver));
    }

    @GetMapping("/search")
    @Operation(summary = "Search drivers by name (Admin only)")
    public ResponseEntity<ApiResponse<List<DriverDTO>>> searchDrivers(@RequestParam String name) {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(driverService.searchByName(name)));
    }

    @GetMapping("/ranking")
    @Operation(summary = "Get driver safety ranking (Admin only)")
    public ResponseEntity<ApiResponse<List<DriverRankingDTO>>> getDriverRanking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SecurityUtils.requireAdmin();
        LocalDate startDate = from != null ? from : LocalDate.now().minusMonths(6);
        LocalDate endDate = to != null ? to : LocalDate.now().plusDays(1);
        return ResponseEntity.ok(ApiResponse.ok(driverService.getDriverRanking(startDate, endDate)));
    }

    @PostMapping
    @Operation(summary = "Create a new driver (Admin only)")
    public ResponseEntity<ApiResponse<DriverDTO>> createDriver(@RequestBody DriverDTO dto) {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(driverService.createDriver(dto), "Driver created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update driver (Admin only)")
    public ResponseEntity<ApiResponse<DriverDTO>> updateDriver(@PathVariable Long id, @RequestBody DriverDTO dto) {
        SecurityUtils.requireAdmin();
        return ResponseEntity.ok(ApiResponse.ok(driverService.updateDriver(id, dto), "Driver updated"));
    }
}
