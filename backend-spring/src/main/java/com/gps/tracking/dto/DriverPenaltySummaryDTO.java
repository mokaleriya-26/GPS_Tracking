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
public class DriverPenaltySummaryDTO {
    private Long driverId;
    private String driverCode;
    private String driverName;
    private long totalQualifyingAlerts;
    private long warningOnlyCount;
    private long penaltyBearingCount;
    private BigDecimal totalPenaltiesAccumulated;
    private Map<String, BigDecimal> penaltiesByAlertType;
    private Map<String, Long> occurrencesByAlertType;
    private PenaltyDTO mostRecentPenalty;
    private List<PenaltyDTO> penaltyHistory;
}
