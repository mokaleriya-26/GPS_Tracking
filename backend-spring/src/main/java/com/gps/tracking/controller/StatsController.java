package com.gps.tracking.controller;

import com.gps.tracking.dto.ApiResponse;
import com.gps.tracking.entity.DriverDailyStats;
import com.gps.tracking.service.StatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Pre-calculated driver daily statistics")
public class StatsController {
    private final StatsService statsService;

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<ApiResponse<List<DriverDailyStats>>> getDriverStats(
            @PathVariable Long driverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate startDate = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = to != null ? to : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(statsService.getDriverStats(driverId, startDate, endDate)));
    }

    @GetMapping("/driver/{driverId}/date/{date}")
    public ResponseEntity<ApiResponse<?>> getDriverStatsByDate(
            @PathVariable Long driverId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(statsService.getDriverStatsByDate(driverId, date).orElse(null)));
    }
}
