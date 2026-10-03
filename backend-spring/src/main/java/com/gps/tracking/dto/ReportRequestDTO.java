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
    private String month;   // e.g. "2026-09" for MONTHLY (alternative to year+month ints)
    private Integer year;   // e.g. 2026 — used to derive startDate for MONTHLY/WEEKLY
    private Integer month2; // integer month (1–12) — used with year to derive startDate
    private String date;    // e.g. "2026-09-15" for DAILY reports
    private Long tripId;
}
