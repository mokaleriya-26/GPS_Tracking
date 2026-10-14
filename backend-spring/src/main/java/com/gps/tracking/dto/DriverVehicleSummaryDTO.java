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
public class DriverVehicleSummaryDTO {
    private Long driverId;
    private String driverCode;
    private String driverName;
    private Long vehicleId;
    private String vehicleNumber;
    private Long trips;
    private BigDecimal distanceKm;
    private Long durationSeconds;
    private String formattedDuration; // Format: "HHh MMm SSs"
    private Long alerts;
    private Long overspeedCount;
    private Long harshBrakingCount;
    private Long otherAlertCount;
}

