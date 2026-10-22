package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertReportItemDTO {
    private Long alertId;
    private String vehicleNumber;
    private String date;
    private String time;
    private String alertType;
    private String severity;
    private Long count;
    private String driverName;
    private String status;
    private Integer occurrenceNumber;
    private String classification;
    private java.math.BigDecimal penaltyAmount;
    private java.math.BigDecimal cumulativePenalties;
}
