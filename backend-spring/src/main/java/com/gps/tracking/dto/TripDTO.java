package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripDTO {
    private Long id;
    private Long vehicleId;
    private String vehicleCode;
    private String vehicleRegistrationNumber;
    private Long driverId;
    private String driverName;
    private String driverCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private BigDecimal startLatitude;
    private BigDecimal startLongitude;
    private BigDecimal endLatitude;
    private BigDecimal endLongitude;
    private BigDecimal distanceKm;
    private Integer durationSeconds;
    private Integer movingSeconds;
    private Integer idleSeconds;
    private BigDecimal maxSpeedKmph;
    private BigDecimal minSpeedKmph;
    private BigDecimal averageSpeedKmph;
    private Integer overspeedEvents;
    private Integer harshBrakingEvents;
    private Integer harshAccelerationEvents;
    private Integer nightDrivingSeconds;
    private Integer alertCount;
    private String primaryRoad;
    private LocalDateTime createdAt;
}
