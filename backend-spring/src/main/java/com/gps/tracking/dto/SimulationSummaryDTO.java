package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationSummaryDTO {
    private String startingLocation;
    private String destination;
    private BigDecimal totalSimulatedDistanceKm;
    private long tripDurationSeconds;
    private String tripDurationFormatted;
    private BigDecimal maxSpeedKmph;
    private BigDecimal averageSpeedKmph;
    private int totalAlerts;
    @Builder.Default
    private Map<String, Long> alertBreakdownByType = new HashMap<>();
    private BigDecimal totalPenaltyAmount;
    private String tripCompletionStatus;
    private Long tripId;
    private String vehicleRegistrationNumber;
    private String driverName;
}
