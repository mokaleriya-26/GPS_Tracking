package com.gps.tracking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.Map;

/**
 * PythonAiService — calls the Python AI/ML microservice for advanced analytics.
 * Falls back gracefully if the AI service is unavailable.
 * Normal fleet management functions are NOT affected by AI service failure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PythonAiService {

    @Qualifier("pythonAiWebClient")
    private final WebClient webClient;

    @Value("${ai.service.enabled:true}")
    private boolean aiEnabled;

    public Map<String, Object> analyzeDriver(Map<String, Object> driverData) {
        if (!aiEnabled) return fallbackAnalysis(driverData);
        try {
            return webClient.post().uri("/analyze-driver")
                .bodyValue(driverData)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        } catch (Exception e) {
            log.warn("AI service unavailable, using fallback: {}", e.getMessage());
            return fallbackAnalysis(driverData);
        }
    }

    public Map<String, Object> detectAnomalies(Map<String, Object> telemetryData) {
        if (!aiEnabled) return Map.of("anomalies", java.util.List.of(), "status", "AI_DISABLED");
        try {
            return webClient.post().uri("/anomaly-detection")
                .bodyValue(telemetryData)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        } catch (Exception e) {
            log.warn("AI service unavailable for anomaly detection: {}", e.getMessage());
            return Map.of("anomalies", java.util.List.of(), "status", "AI_UNAVAILABLE");
        }
    }

    /** Rule-based fallback when AI service is unavailable. */
    private Map<String, Object> fallbackAnalysis(Map<String, Object> data) {
        int overspeed = ((Number) data.getOrDefault("overspeed_events", 0)).intValue();
        int harshBraking = ((Number) data.getOrDefault("harsh_braking_events", 0)).intValue();
        int harshAccel = ((Number) data.getOrDefault("harsh_acceleration_events", 0)).intValue();
        double nightDrivingHrs = ((Number) data.getOrDefault("night_driving_seconds", 0)).doubleValue() / 3600.0;
        int fatigueEvents = ((Number) data.getOrDefault("fatigue_events", 0)).intValue();
        int cleanTrips = ((Number) data.getOrDefault("trips_without_incident", 0)).intValue();

        double score = 100
            - (overspeed * 5)
            - (harshBraking * 4)
            - (harshAccel * 4)
            - (nightDrivingHrs * 3)
            - (fatigueEvents * 8)
            + (cleanTrips * 1);

        score = Math.max(0, Math.min(100, score));

        return Map.of(
            "safety_score", Math.round(score * 100.0) / 100.0,
            "method", "RULE_BASED_FALLBACK",
            "status", "AI_SERVICE_UNAVAILABLE"
        );
    }

    /** Check whether the Python AI service is currently reachable. */
    public boolean isAvailable() {
        if (!aiEnabled) return false;
        try {
            Map<?, ?> result = webClient.get().uri("/health")
                .retrieve().bodyToMono(Map.class).block();
            return result != null && "ok".equals(result.get("status"));
        } catch (Exception e) {
            return false;
        }
    }
}
