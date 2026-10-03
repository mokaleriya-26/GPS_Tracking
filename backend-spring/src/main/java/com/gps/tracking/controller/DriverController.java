package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.service.DriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Drivers", description = "Driver management and performance analytics")
public class DriverController {

    private final DriverService driverService;

    @GetMapping
    @Operation(summary = "List all drivers")
    public ResponseEntity<ApiResponse<List<DriverDTO>>> getAllDrivers(
            @RequestParam(required = false) Boolean active) {
        List<DriverDTO> drivers = active != null && active ? driverService.getActiveDrivers() : driverService.getAllDrivers();
        return ResponseEntity.ok(ApiResponse.ok(drivers));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get driver by ID")
    public ResponseEntity<ApiResponse<DriverDTO>> getDriverById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.getDriverById(id)));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get driver by code (e.g. DRV001)")
    public ResponseEntity<ApiResponse<DriverDTO>> getDriverByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.getDriverByCode(code)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search drivers by name")
    public ResponseEntity<ApiResponse<List<DriverDTO>>> searchDrivers(@RequestParam String name) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.searchByName(name)));
    }

    @GetMapping("/ranking")
    @Operation(summary = "Get driver safety ranking")
    public ResponseEntity<ApiResponse<List<DriverRankingDTO>>> getDriverRanking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // Default: past 6 months so the seeded demo dataset (Aug–Oct 2026) is always included
        LocalDate startDate = from != null ? from : LocalDate.now().minusMonths(6);
        LocalDate endDate = to != null ? to : LocalDate.now().plusDays(1);
        return ResponseEntity.ok(ApiResponse.ok(driverService.getDriverRanking(startDate, endDate)));
    }

    @PostMapping
    @Operation(summary = "Create a new driver")
    public ResponseEntity<ApiResponse<DriverDTO>> createDriver(@RequestBody DriverDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.createDriver(dto), "Driver created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update driver")
    public ResponseEntity<ApiResponse<DriverDTO>> updateDriver(@PathVariable Long id, @RequestBody DriverDTO dto) {
        return ResponseEntity.ok(ApiResponse.ok(driverService.updateDriver(id, dto), "Driver updated"));
    }
}
