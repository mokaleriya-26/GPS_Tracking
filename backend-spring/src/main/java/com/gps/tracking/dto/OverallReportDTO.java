package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for the Overall Fleet Report — covers a date range and contains
 * vehicle entry/exit counts, alert summary by type, and per-driver alert breakdown.
 * All counts come from the PostgreSQL database; nothing is fabricated.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OverallReportDTO {

    /** "DAILY" | "WEEKLY" | "MONTHLY" | "CUSTOM" */
    private String reportType;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;

    /** Unique vehicles that started at least one trip (entry events) */
    private long totalVehiclesEntered;

    /** Unique vehicles that completed at least one trip (exit events) */
    private long totalVehiclesExited;

    /** Total number of alerts generated in the period */
    private long totalAlerts;

    /**
     * Alert counts by type, e.g.:
     * { "Overspeed Alert": 342, "Harsh Braking Alert": 215, ... }
     */
    private Map<String, Long> alertTypeSummary;

    /**
     * Per-driver alert detail rows.
     */
    private List<DriverAlertRow> driverAlertDetails;

    /** Flat PDF report ID + download URL (populated when pdfMode=true) */
    private String reportId;
    private String pdfUrl;
    private String downloadUrl;
    private String message;

    @Data
    @Builder
    public static class DriverAlertRow {
        private Long driverId;
        private String driverCode;
        private String driverName;
        private String vehicles;           // comma-separated registration numbers
        private long totalAlerts;
        private long overspeed;
        private long harshBraking;
        private long harshAcceleration;
        private long gpsDisconnect;
        private long nightDriving;
        private long ignition;
        private long fatigue;
        private long other;
    }
}
