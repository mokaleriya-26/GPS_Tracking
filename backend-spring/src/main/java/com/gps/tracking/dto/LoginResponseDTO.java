package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    private String role; // "ADMIN" | "USER"
    private String username;
    private Long driverId;
    private String driverCode;
    private String driverName;
    private Long vehicleId;
    private String vehicleCode;
    private String vehicleRegistrationNumber;
}
