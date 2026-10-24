package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverDTO {
    private Long id;
    private String code;
    private String name;
    private String phone;
    private String email;
    private String licenseNumber;
    private LocalDate joinedOn;
    private Boolean active;
    private LocalDateTime createdAt;

    // Relational and computed statistics from PostgreSQL
    private Integer totalTrips;
    private Double totalDistanceKm;
    private Double safetyScore;
    private Long totalAlerts;
    private Long totalPenalties; // active penalty records count
    private BigDecimal totalPenaltyAmount; // sum of active monetary fines
    private Long activeWarnings; // warnings count (monetary = 0)
    private Long activeFines; // fines count (monetary > 0)
    private String penaltySummary; // e.g. "1 Warning (₹0)" or "2 Penalties (₹250)"

    @Builder.Default
    private List<String> assignedVehicles = new ArrayList<>();
    private String assignedVehicleSummary;

    @Builder.Default
    private List<String> vehiclesOperated = new ArrayList<>();

    @Builder.Default
    private List<AssignedVehicleDTO> currentVehicles = new ArrayList<>();

    @Builder.Default
    private List<AssignedVehicleDTO> historicalVehicles = new ArrayList<>();

    @Builder.Default
    private List<PenaltyRecordDTO> penaltyRecords = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedVehicleDTO {
        private Long id;
        private String code;
        private String registrationNumber;
        private String vehicleType;
        private String makeModel;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PenaltyRecordDTO {
        private Long id;
        private String penaltyType;
        private String classification; // WARNING or PENALTY
        private String reason;
        private BigDecimal monetaryAmount;
        private Double penaltyPoints;
        private String status; // ACTIVE or REVOKED
        private LocalDateTime dateApplied;
        private Integer occurrenceNumber;
    }
}
