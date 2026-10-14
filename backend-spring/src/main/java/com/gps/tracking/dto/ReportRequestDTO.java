package com.gps.tracking.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ReportRequestDTO {
    private String reportType; // DAILY, WEEKLY, MONTHLY, CUSTOM, TRIP
    private Long driverId;
    private String driverCode;
    private Long vehicleId;
    private String vehicleCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private String month;   // e.g. "2026-09" for MONTHLY
    private String week;    // e.g. "2026-W41" for WEEKLY
    private Integer year;   // e.g. 2026
    private Integer month2; // integer month (1–12)
    private String date;    // e.g. "2026-10-12" for DAILY reports
    private Long tripId;
}
