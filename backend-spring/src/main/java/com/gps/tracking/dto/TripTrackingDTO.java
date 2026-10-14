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
public class TripTrackingDTO {
    private Long tripId;
    private String vehicleNumber;
    private String driverName;
    private String tripStartDate;
    private String entryStartTime;
    private String startLocation;
    private String endLocation;
    private String destination;
    private String tripEndDate;
    private String exitEndTime;
    private BigDecimal totalDistanceKm;
    private String totalTripDurationFormatted; // Format: "HHh MMm SSs"
    private Long durationSeconds;
    private BigDecimal maxSpeedKmph;
    private BigDecimal averageSpeedKmph;
    private Integer alertsDuringTrip;
    private String tripStatus;
}
