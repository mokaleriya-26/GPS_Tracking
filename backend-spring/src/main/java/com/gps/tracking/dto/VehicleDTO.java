package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VehicleDTO {
    private Long id;
    private String code;
    private String imei;
    private String registrationNumber;
    private String vehicleType;
    private String makeModel;
    private String fuelType;
    private BigDecimal tankCapacityLitres;
    private BigDecimal ratedMileageKmpl;
    private BigDecimal odometerKm;
    private Long driverId;
    private String driverName;
    private String driverCode;
    private Boolean active;
    private LocalDateTime createdAt;
    // Live telemetry
    private BigDecimal lastSpeedKmph;
    private Boolean lastIgnition;
    private String lastLocation;
    private LocalDateTime lastSeenAt;
}
