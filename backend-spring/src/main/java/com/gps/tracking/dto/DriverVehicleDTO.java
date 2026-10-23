package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverVehicleDTO {
    private Long id;
    private String code;
    private String registrationNumber;
    private String vehicleType;
    private String makeModel;
    private String fuelType;
    private Double odometerKm;
    private String assignmentType; // "CURRENT" or "HISTORICAL"
    private Long tripCount;
    private Double totalDistanceKm;
    private LocalDateTime lastOperatedAt;
    private Boolean active;
}
