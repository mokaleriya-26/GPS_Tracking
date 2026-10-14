package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyScoreBreakdownDTO {
    private Long driverId;
    private String driverCode;
    private String driverName;
    private Integer totalTrips;
    private BigDecimal totalDistanceKm;
    private List<MetricDetailDTO> metrics;
    private BigDecimal baseScore; // 100.00
    private BigDecimal formulaDeductions;
    private BigDecimal manualPenaltyDeductions;
    private BigDecimal totalWeightedPenalty;
    private BigDecimal finalSafetyScore;
    private Integer rank;
    private String performanceCategory; // "Excellent", "Average", "Needs Improvement"
    private String explanation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricDetailDTO {
        private String metricName;
        private String alertType;
        private Long count;
        private Double normalizedRate;
        private String rateUnit;
        private BigDecimal weight;
        private BigDecimal penalty;
        private Boolean available;
        private String note;
    }
}
