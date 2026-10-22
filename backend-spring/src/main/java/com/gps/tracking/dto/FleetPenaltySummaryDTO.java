package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetPenaltySummaryDTO {
    private BigDecimal totalFleetPenalties;
    private long totalPenaltyBearingAlerts;
    private long totalWarningOnlyAlerts;
    private Long highestPenalizedDriverId;
    private String highestPenalizedDriverCode;
    private String highestPenalizedDriverName;
    private BigDecimal highestDriverPenaltyTotal;
    private Map<String, BigDecimal> penaltyBreakdownByAlertType;
    private Map<String, Long> alertCountByAlertType;
    private List<PenaltyDTO> recentPenalties;
}
