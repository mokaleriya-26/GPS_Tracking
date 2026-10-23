package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverDashboardDTO {
    private DriverDTO driver;
    @Builder.Default
    private List<VehicleDTO> assignedVehicles = new ArrayList<>();
    private Double safetyScore;
    private long totalTrips;
    private long totalAlerts;
    private String latestTripStatus;
    private long totalPenalties;
    private BigDecimal totalPenaltyAmount;
    @Builder.Default
    private List<TripDTO> recentTrips = new ArrayList<>();
    @Builder.Default
    private List<AlertDTO> recentAlerts = new ArrayList<>();
    @Builder.Default
    private List<PenaltyDTO> recentPenalties = new ArrayList<>();
}
