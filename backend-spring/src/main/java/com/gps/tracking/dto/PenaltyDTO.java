package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PenaltyDTO {
    private Long id;
    private Long driverId;
    private String driverCode;
    private String driverName;
    private Long alertId;
    private String alertType;
    private Long tripId;
    private Long vehicleId;
    private String vehicleRegistration;
    private String penaltyType; // MANUAL_SAFETY_DEDUCTION, FINANCIAL_FINE, POLICY_VIOLATION, WARNING
    private String reason;
    private BigDecimal penaltyPoints;
    private BigDecimal monetaryAmount;
    private LocalDateTime dateApplied;
    private String appliedBy;
    private String status; // ACTIVE, REVOKED, WAIVED
    private Boolean deductFromScore;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
