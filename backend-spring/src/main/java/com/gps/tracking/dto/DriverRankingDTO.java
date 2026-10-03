package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverRankingDTO {
    private Integer rank;
    private Long driverId;
    private String driverCode;
    private String driverName;
    private BigDecimal safetyScore;
    private Integer tripCount;
    private BigDecimal totalDistanceKm;
    private Integer totalAlerts;
    private Integer overspeedEvents;
    private Integer harshBrakingEvents;
    private Integer harshAccelerationEvents;
    private Integer fatigueEvents;
    private Integer nightDrivingSeconds;
    private String scoreExplanation;
}
