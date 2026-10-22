package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Structured DTO representing Alert-Based Reports for both Driver and Fleet scopes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertBasedReportDTO {

    private String reportScope; // "DRIVER" or "FLEET"
    private String reportType;  // "ALERT_BASED"
    private String title;
    private String reportingPeriod;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;

    // Applied Filters
    private List<String> selectedAlertTypes;
    private String severityFilter;
    private String statusFilter;
    private Long vehicleIdFilter;
    private String vehicleNumberFilter;

    // Driver-specific section
    private Long driverId;
    private String driverCode;
    private String driverName;
    private String driverPhone;
    private List<DriverVehicleSummaryDTO> vehiclesOperated;
    private SafetyScoreBreakdownDTO safetyScore;
    private List<PenaltyDTO> penalties;
    private List<String> recommendations;

    // Fleet-specific section
    private Integer totalVehiclesCovered;
    private Integer totalDriversCovered;
    private List<VehicleAlertSummaryDTO> vehicleSummaries;
    private List<DriverAlertSummaryDTO> driverSummaries;

    // Shared metrics & details
    private Long totalAlerts;
    private Map<String, Long> alertTypeBreakdown;
    private Map<String, Long> alertSeverityBreakdown;
    private Map<String, Long> alertStatusBreakdown;
    private List<DailyAlertTrendDTO> dailyTrends;
    private List<AlertDetailRowDTO> detailedAlerts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyAlertTrendDTO {
        private String date; // "dd MMM yyyy"
        private Long count;
        private Map<String, Long> byType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertDetailRowDTO {
        private Integer srNo;
        private Long alertId;
        private String date;       // "dd MMM yyyy"
        private String time;       // "HH:mm:ss"
        private String vehicleNumber;
        private String vehicleCode;
        private String driverName;
        private String driverCode;
        private Long driverId;
        private String alertType;
        private String severity;
        private String location;
        private String status;
        private Double speedKmph;
        private Double speedLimitKmph;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleAlertSummaryDTO {
        private Long vehicleId;
        private String vehicleNumber;
        private String vehicleCode;
        private Long totalAlerts;
        private Long overspeedCount;
        private Long harshBrakingCount;
        private Long harshAccelCount;
        private Long gpsDisconnectCount;
        private Long nightDrivingCount;
        private Long ignitionCount;
        private Long otherCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverAlertSummaryDTO {
        private Long driverId;
        private String driverCode;
        private String driverName;
        private String vehiclesOperated; // e.g. "MH03AC4582, MH04EF1234"
        private Long totalAlerts;
        private Long overspeedCount;
        private Long harshBrakingCount;
        private Long harshAccelCount;
        private Long gpsDisconnectCount;
        private Long nightDrivingCount;
        private Long ignitionCount;
        private Long otherCount;
    }
}
