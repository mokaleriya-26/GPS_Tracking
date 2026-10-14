package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverRankingDTO {
    private Integer rank;
    private Long driverId;
    private String driverCode;
    private String driverName;
    private BigDecimal safetyScore;
    private String performanceCategory;
    private String vehiclesOperated;
    private Integer tripCount;
    private BigDecimal totalDistanceKm;
    private Integer totalAlerts;
    private Integer overspeedEvents;
    private Integer harshBrakingEvents;
    private Integer harshAccelerationEvents;
    private Integer fatigueEvents;
    private Integer nightDrivingSeconds;
    private String scoreExplanation;
    private SafetyScoreBreakdownDTO breakdown;
}
