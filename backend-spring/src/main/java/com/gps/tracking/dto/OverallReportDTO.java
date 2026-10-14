package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for the Overall Fleet Report — covers a date range and contains
 * vehicle entry/exit counts, alert summary by type, driver summary,
 * trip tracking, and driver-vehicle associations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    /** Total number of trips in the period */
    private long totalTrips;

    /** Total distance travelled across all trips in km */
    private BigDecimal totalDistanceKm;

    /** Total duration in seconds across all trips */
    private long totalDurationSeconds;

    /** Total duration formatted as "HHh MMm SSs" */
    private String totalDurationFormatted;

    /** Total number of alerts generated in the period */
    private long totalAlerts;

    /** Average safety score across active drivers */
    private BigDecimal averageSafetyScore;

    /** Overall fleet performance category ("Excellent", "Average", "Needs Improvement") */
    private String performanceCategory;

    /**
     * Alert counts by type, e.g.:
     * { "Overspeed Alert": 342, "Harsh Braking Alert": 215, ... }
     */
    private Map<String, Long> alertTypeSummary;

    /** Alert counts by severity (CRITICAL, WARNING, INFO) */
    private Map<String, Long> alertSeveritySummary;

    /** Alert counts by status (OPEN, RESOLVED, ACKNOWLEDGED) */
    private Map<String, Long> alertStatusSummary;

    /** Alert counts by vehicle registration */
    private Map<String, Long> vehicleAlertSummary;

    /**
     * Per-driver alert and safety breakdown rows.
     */
    private List<DriverAlertRow> driverAlertDetails;

    /**
     * Driver-vehicle detailed summary table.
     */
    private List<DriverVehicleSummaryDTO> driverVehicleDetails;

    /**
     * Trip tracking records.
     */
    private List<TripTrackingDTO> tripDetails;

    /**
     * Alert individual event details (Section 6A).
     */
    private List<AlertReportItemDTO> alertEvents;

    /**
     * Alert grouped summary rows by vehicle & alert type (Section 6B).
     */
    private List<AlertReportItemDTO> alertSummaries;

    /** Flat PDF report ID + download URL */
    private String reportId;
    private String pdfUrl;
    private String downloadUrl;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DriverAlertRow {
        private Long driverId;
        private String driverCode;
        private String driverName;
        private String vehicles;           // comma-separated registration numbers
        private long tripCount;
        private BigDecimal totalDistanceKm;
        private String formattedDuration;  // "HHh MMm SSs"
        private BigDecimal safetyScore;
        private String performanceCategory;
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
