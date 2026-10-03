package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverDTO {
    private Long id;
    private String code;
    private String name;
    private String phone;
    private String licenseNumber;
    private LocalDate joinedOn;
    private Boolean active;
    private LocalDateTime createdAt;
    // Computed fields
    private Integer totalTrips;
    private Double totalDistanceKm;
    private Double safetyScore;
}
