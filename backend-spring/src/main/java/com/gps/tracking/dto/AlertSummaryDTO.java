package com.gps.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertSummaryDTO {
    private long totalAlerts;
    private long openAlerts;
    private long historicalAlerts;
    private long criticalAlerts;
    private long warningAlerts;
}
