package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.service.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Trip management (entry/exit tracking)")
public class TripController {
    private final TripService tripService;

    @GetMapping
    @Operation(summary = "List all trips (entry/exit events)")
    public ResponseEntity<ApiResponse<Page<TripDTO>>> getTrips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getAll(PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getById(id)));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<ApiResponse<Page<TripDTO>>> getByVehicle(@PathVariable Long vehicleId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getByVehicle(vehicleId, PageRequest.of(page, size))));
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<ApiResponse<Page<TripDTO>>> getByDriver(@PathVariable Long driverId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.getByDriver(driverId, PageRequest.of(page, size))));
    }
}
