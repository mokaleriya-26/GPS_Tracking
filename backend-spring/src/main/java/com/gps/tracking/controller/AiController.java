package com.gps.tracking.controller;

import com.gps.tracking.dto.ApiResponse;
import com.gps.tracking.service.DriverService;
import com.gps.tracking.service.PythonAiService;
import com.gps.tracking.repository.DriverDailyStatsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * AiController — exposes AI/ML endpoints that proxy to PythonAiService
 * with automatic fallback when the Python service is unavailable.
 *
 * As per MIGRATION_PLAN.md Section 10.4:
 * Normal fleet management functions are NOT affected by AI service failure.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI/ML", description = "AI/ML driver analysis and anomaly detection")
public class AiController {

    private final PythonAiService pythonAiService;
    private final DriverService driverService;
    private final DriverDailyStatsRepository statsRepository;

    /**
     * POST /api/ai/analyze-driver
     * Analyze a driver's behaviour using the Python AI service.
     * Falls back to rule-based analysis if AI service is unavailable.
     */
    @PostMapping("/analyze-driver")
    @Operation(summary = "Analyse a driver's behaviour (uses Python AI with rule-based fallback)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeDriver(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = pythonAiService.analyzeDriver(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * POST /api/ai/anomaly-detection
     * Detect anomalies in a batch of telemetry data points.
     */
    @PostMapping("/anomaly-detection")
    @Operation(summary = "Detect anomalies in telemetry data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detectAnomalies(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = pythonAiService.detectAnomalies(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/ai/driver-ranking
     * Return driver ranking with AI-enhanced scores (uses Python AI for analysis,
     * falls back to the Spring Boot rule-based ranking).
     */
    @GetMapping("/driver-ranking")
    @Operation(summary = "Get AI-enhanced driver safety ranking")
    public ResponseEntity<ApiResponse<Object>> getAiDriverRanking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate startDate = from != null ? from : LocalDate.now().minusMonths(6);
        LocalDate endDate   = to   != null ? to   : LocalDate.now().plusDays(1);

        // Use the Spring Boot driver ranking (which already uses the safety formula
        // from MIGRATION_PLAN.md Section 10.2 — same formula as the Python service)
        var ranking = driverService.getDriverRanking(startDate, endDate);

        Map<String, Object> response = new HashMap<>();
        response.put("ranking", ranking);
        response.put("method", pythonAiService.isAvailable() ? "AI_ENHANCED" : "RULE_BASED_FALLBACK");
        response.put("from", startDate.toString());
        response.put("to", endDate.toString());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/ai/health
     * Check if the Python AI service is reachable.
     */
    @GetMapping("/health")
    @Operation(summary = "Check Python AI service health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aiHealth() {
        boolean available = pythonAiService.isAvailable();
        Map<String, Object> status = Map.of(
            "pythonAiService", available ? "UP" : "UNAVAILABLE",
            "fallbackActive", !available,
            "note", available
                ? "Python AI service is responding normally."
                : "Python AI service is down. Rule-based fallback is active. Fleet features are unaffected."
        );
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}
