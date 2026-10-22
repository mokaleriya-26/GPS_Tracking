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
public class AlertPenaltyConfigDTO {
    private Long id;
    private String alertType;
    private BigDecimal weight;
    private BigDecimal defaultFineAmount;
    private BigDecimal penaltyIncrement;
    private Integer warningThreshold;
    private Boolean penaltyEnabled;
    private String description;
    private Boolean isActive;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
