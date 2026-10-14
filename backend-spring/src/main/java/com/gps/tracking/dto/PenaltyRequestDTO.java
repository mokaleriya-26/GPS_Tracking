package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PenaltyRequestDTO {
    private Long driverId;
    private Long alertId;
    private Long tripId;
    private Long vehicleId;
    private String penaltyType; // MANUAL_SAFETY_DEDUCTION, FINANCIAL_FINE, POLICY_VIOLATION, WARNING
    private String reason;
    private BigDecimal penaltyPoints;
    private BigDecimal monetaryAmount;
    private Boolean deductFromScore;
    private String notes;
}
