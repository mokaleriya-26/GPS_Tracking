package com.gps.tracking.service;

import com.gps.tracking.dto.ReportRequestDTO;
import com.gps.tracking.dto.ReportResponseDTO;
import com.gps.tracking.entity.*;
import com.gps.tracking.repository.*;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;
    private final DriverDailyStatsRepository statsRepository;

    @Value("${reports.output-dir:./reports/generated}")
    private String reportsOutputDir;

    private static final DeviceRgb PRIMARY_BLUE = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb HEADER_DARK  = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb LIGHT_GRAY   = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb TEXT_MUTED   = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb SUCCESS_GREEN= new DeviceRgb(22, 163, 74);
    private static final DeviceRgb DANGER_RED   = new DeviceRgb(220, 38, 38);
    private static final DeviceRgb WARNING_AMBER= new DeviceRgb(217, 119, 6);

    public ReportResponseDTO generateDriverReport(ReportRequestDTO req) {
        Driver driver = req.getDriverId() != null
            ? driverRepository.findById(req.getDriverId()).orElseThrow()
            : driverRepository.findByCode(req.getDriverCode()).orElseThrow();

        LocalDate startDate = resolveStartDate(req);
        LocalDate endDate   = resolveEndDate(req, startDate);

        String reportId = "DRVRPT_" + driver.getCode() + "_" + startDate + "_" + UUID.randomUUID().toString().substring(0, 6);
        String fileName = reportId + ".pdf";

        try {
            Files.createDirectories(Paths.get(reportsOutputDir));
            String filePath = reportsOutputDir + File.separator + fileName;

            generateDriverPdf(filePath, driver, startDate, endDate, req.getReportType());

            ReportResponseDTO resp = new ReportResponseDTO();
            resp.setReportId(reportId);
            resp.setReportType(req.getReportType() != null ? req.getReportType() : "MONTHLY");
            resp.setStatus("READY");
            resp.setPdfUrl("/api/reports/" + reportId + "/pdf");
            resp.setDownloadUrl("/api/reports/" + reportId + "/download");
            resp.setGeneratedAt(LocalDateTime.now());
            resp.setMessage("Report generated successfully");
            resp.setReportData(Map.of("driverName", driver.getName(), "driverCode", driver.getCode(),
                "startDate", startDate.toString(), "endDate", endDate.toString()));
            return resp;

        } catch (Exception e) {
            log.error("Error generating driver report for {}", driver.getCode(), e);
            ReportResponseDTO resp = new ReportResponseDTO();
            resp.setStatus("FAILED");
            resp.setMessage("Error: " + e.getMessage());
            return resp;
        }
    }

    public ReportResponseDTO generateFleetReport(ReportRequestDTO req) {
        List<Driver> drivers = driverRepository.findByActiveTrue();
        LocalDate startDate = resolveStartDate(req);
        LocalDate endDate   = resolveEndDate(req, startDate);

        String reportId = "FLTRPT_" + startDate + "_" + endDate + "_" + UUID.randomUUID().toString().substring(0, 6);
        String fileName = reportId + ".pdf";

        try {
            Files.createDirectories(Paths.get(reportsOutputDir));
            String filePath = reportsOutputDir + File.separator + fileName;

            generateFleetPdf(filePath, drivers, startDate, endDate);

            ReportResponseDTO resp = new ReportResponseDTO();
            resp.setReportId(reportId);
            resp.setReportType("FLEET_MONTHLY");
            resp.setStatus("READY");
            resp.setPdfUrl("/api/reports/" + reportId + "/pdf");
            resp.setDownloadUrl("/api/reports/" + reportId + "/download");
            resp.setGeneratedAt(LocalDateTime.now());
            resp.setMessage("Fleet report generated successfully");
            return resp;

        } catch (Exception e) {
            log.error("Error generating fleet report", e);
            ReportResponseDTO resp = new ReportResponseDTO();
            resp.setStatus("FAILED");
            resp.setMessage("Error: " + e.getMessage());
            return resp;
        }
    }

    // ----------------------------------------------------------------
    // OVERALL FLEET REPORT  (daily / weekly / monthly / custom)
    // ----------------------------------------------------------------
    public com.gps.tracking.dto.OverallReportDTO generateOverallReport(ReportRequestDTO req) {
        LocalDate startDate = resolveStartDate(req);
        LocalDate endDate   = resolveEndDate(req, startDate);
        LocalDateTime from  = startDate.atStartOfDay();
        LocalDateTime to    = endDate.plusDays(1).atStartOfDay();

        // 1. Vehicle entry / exit counts
        long vehiclesEntered  = tripRepository.countDistinctVehiclesWithTripStarted(from, to);
        long vehiclesExited   = tripRepository.countDistinctVehiclesWithTripCompleted(from, to);

        // 2. Total alerts
        long totalAlerts = alertRepository.countInDateRange(from, to);

        // 3. Alert type summary
        List<Object[]> typeRows = alertRepository.countByTypeInDateRange(from, to);
        Map<String, Long> alertTypeSummary = new LinkedHashMap<>();
        for (Object[] row : typeRows) {
            alertTypeSummary.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        // 4. Per-driver alert breakdown
        List<Object[]> driverRows = alertRepository.driverAlertBreakdownInDateRange(from, to);
        // Group by driverId: key = driverId, value = DriverAlertRow builder
        Map<Long, com.gps.tracking.dto.OverallReportDTO.DriverAlertRow.DriverAlertRowBuilder> builderMap = new LinkedHashMap<>();
        for (Object[] row : driverRows) {
            Long   driverId   = ((Number) row[0]).longValue();
            String driverCode = String.valueOf(row[1]);
            String driverName = String.valueOf(row[2]);
            String alertType  = String.valueOf(row[3]).toLowerCase();
            long   count      = ((Number) row[4]).longValue();

            var b = builderMap.computeIfAbsent(driverId, id ->
                com.gps.tracking.dto.OverallReportDTO.DriverAlertRow.builder()
                    .driverId(id).driverCode(driverCode).driverName(driverName)
                    .vehicles("—")
            );
            if (alertType.contains("overspeed"))             b.overspeed(count);
            else if (alertType.contains("harsh braking") || alertType.contains("harsh_braking")) b.harshBraking(count);
            else if (alertType.contains("harsh acc") || alertType.contains("harsh_acc"))         b.harshAcceleration(count);
            else if (alertType.contains("gps"))              b.gpsDisconnect(count);
            else if (alertType.contains("night"))            b.nightDriving(count);
            else if (alertType.contains("ignition"))         b.ignition(count);
            else if (alertType.contains("fatigue"))          b.fatigue(count);
            else {
                // accumulate in 'other'
                var existing = builderMap.get(driverId);
                // We can't easily mutate 'other' without building — track via totalAlerts for now
            }
        }

        // Populate vehicle registrations per driver from DB
        Map<Long, String> driverVehicles = new HashMap<>();
        vehicleRepository.findAll().forEach(v -> {
            if (v.getDriver() != null) {
                driverVehicles.merge(v.getDriver().getId(),
                    v.getRegistrationNumber(), (a, b) -> a + ", " + b);
            }
        });

        // Build row list with totalAlerts sum
        List<com.gps.tracking.dto.OverallReportDTO.DriverAlertRow> driverDetails = new ArrayList<>();
        for (var entry : builderMap.entrySet()) {
            Long driverId = entry.getKey();
            var b = entry.getValue();
            b.vehicles(driverVehicles.getOrDefault(driverId, "—"));
            var built = b.build();
            long rowTotal = built.getOverspeed() + built.getHarshBraking() + built.getHarshAcceleration()
                + built.getGpsDisconnect() + built.getNightDriving() + built.getIgnition()
                + built.getFatigue() + built.getOther();
            // Also sum 'other' from raw rows
            long otherSum = driverRows.stream()
                .filter(r -> ((Number) r[0]).longValue() == driverId)
                .mapToLong(r -> {
                    String t = String.valueOf(r[3]).toLowerCase();
                    if (t.contains("overspeed") || t.contains("harsh") || t.contains("gps")
                        || t.contains("night") || t.contains("ignition") || t.contains("fatigue")) return 0;
                    return ((Number) r[4]).longValue();
                }).sum();
            driverDetails.add(b.other(otherSum).totalAlerts(rowTotal + otherSum).build());
        }

        // 5. Generate PDF
        String rtype = req.getReportType() != null ? req.getReportType().toUpperCase() : "OVERALL";
        String reportId = "OVRRPT_" + rtype + "_" + startDate + "_" + UUID.randomUUID().toString().substring(0, 6);
        String fileName = reportId + ".pdf";
        String pdfUrl = null, downloadUrl = null;

        try {
            Files.createDirectories(Paths.get(reportsOutputDir));
            String filePath = reportsOutputDir + File.separator + fileName;
            generateOverallPdf(filePath, rtype, startDate, endDate,
                vehiclesEntered, vehiclesExited, totalAlerts, alertTypeSummary, driverDetails);
            pdfUrl = "/api/reports/" + reportId + "/pdf";
            downloadUrl = "/api/reports/" + reportId + "/download";
        } catch (Exception e) {
            log.error("Error generating overall PDF", e);
        }

        return com.gps.tracking.dto.OverallReportDTO.builder()
            .reportType(rtype)
            .startDate(startDate)
            .endDate(endDate)
            .generatedAt(LocalDateTime.now())
            .totalVehiclesEntered(vehiclesEntered)
            .totalVehiclesExited(vehiclesExited)
            .totalAlerts(totalAlerts)
            .alertTypeSummary(alertTypeSummary)
            .driverAlertDetails(driverDetails)
            .reportId(reportId)
            .pdfUrl(pdfUrl)
            .downloadUrl(downloadUrl)
            .message("Overall " + rtype.toLowerCase() + " report generated successfully")
            .build();
    }

    private void generateOverallPdf(String filePath, String rtype,
            LocalDate startDate, LocalDate endDate,
            long vehiclesEntered, long vehiclesExited, long totalAlerts,
            Map<String, Long> alertTypeSummary,
            List<com.gps.tracking.dto.OverallReportDTO.DriverAlertRow> driverDetails) throws Exception {

        DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter tsFmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(filePath));
             Document document = new Document(pdfDoc, PageSize.A4.rotate())) {

            document.setMargins(30, 30, 30, 30);

            // Header
            Paragraph title = new Paragraph("Overall Fleet Report — " + rtype)
                .setFontSize(22).setBold().setFontColor(PRIMARY_BLUE);
            Paragraph sub = new Paragraph(
                startDate.format(dFmt) + " to " + endDate.format(dFmt) +
                "   ·   Generated: " + LocalDateTime.now().format(tsFmt))
                .setFontSize(10).setFontColor(TEXT_MUTED);
            document.add(title);
            document.add(sub);
            document.add(new Paragraph("\n").setFontSize(4));

            // Summary metrics table
            Table metrics = new Table(new float[]{1, 1, 1}).setWidth(UnitValue.createPercentValue(60));
            for (String[] cell : new String[][]{
                    {"Vehicles Entered", String.valueOf(vehiclesEntered), "blue"},
                    {"Vehicles Exited",  String.valueOf(vehiclesExited), "green"},
                    {"Total Alerts",     String.valueOf(totalAlerts),    "red"}
            }) {
                DeviceRgb colour = cell[2].equals("blue") ? PRIMARY_BLUE
                    : cell[2].equals("green") ? SUCCESS_GREEN : DANGER_RED;
                metrics.addCell(new Cell().add(new Paragraph(cell[0]).setFontSize(9).setFontColor(TEXT_MUTED))
                    .add(new Paragraph(cell[1]).setFontSize(20).setBold().setFontColor(colour))
                    .setBorder(null).setBackgroundColor(LIGHT_GRAY).setPadding(10));
            }
            document.add(metrics);
            document.add(new Paragraph("\n").setFontSize(6));

            // Alert type summary
            document.add(new Paragraph("Alert Type Summary").setFontSize(13).setBold().setFontColor(HEADER_DARK));
            Table atTable = new Table(new float[]{3, 1}).setWidth(UnitValue.createPercentValue(50));
            atTable.addHeaderCell(headerCell("Alert Type"));
            atTable.addHeaderCell(headerCell("Count"));
            for (var entry : alertTypeSummary.entrySet()) {
                atTable.addCell(dataCell(entry.getKey()));
                atTable.addCell(dataCell(String.valueOf(entry.getValue())));
            }
            if (alertTypeSummary.isEmpty()) {
                atTable.addCell(new Cell(1, 2).add(new Paragraph("No alerts in this period").setFontSize(9).setFontColor(TEXT_MUTED)).setBorder(null));
            }
            document.add(atTable);
            document.add(new Paragraph("\n").setFontSize(6));

            // Driver alert details
            document.add(new Paragraph("Driver Alert Details").setFontSize(13).setBold().setFontColor(HEADER_DARK));
            Table dTable = new Table(new float[]{2, 1.2f, 2, 1, 1, 1, 1, 1, 1, 1})
                .setWidth(UnitValue.createPercentValue(100));
            for (String h : new String[]{"Driver", "ID", "Vehicle(s)", "Total", "Overspeed",
                    "Harsh Braking", "GPS Disc.", "Night", "Ignition", "Other"}) {
                dTable.addHeaderCell(headerCell(h));
            }
            for (var row : driverDetails) {
                dTable.addCell(dataCell(row.getDriverName()));
                dTable.addCell(dataCell(row.getDriverCode()));
                dTable.addCell(dataCell(row.getVehicles()));
                dTable.addCell(dataCell(String.valueOf(row.getTotalAlerts())));
                dTable.addCell(dataCell(String.valueOf(row.getOverspeed())));
                dTable.addCell(dataCell(String.valueOf(row.getHarshBraking())));
                dTable.addCell(dataCell(String.valueOf(row.getGpsDisconnect())));
                dTable.addCell(dataCell(String.valueOf(row.getNightDriving())));
                dTable.addCell(dataCell(String.valueOf(row.getIgnition())));
                dTable.addCell(dataCell(String.valueOf(row.getOther())));
            }
            if (driverDetails.isEmpty()) {
                dTable.addCell(new Cell(1, 10).add(new Paragraph("No driver alert data for this period").setFontSize(9)).setBorder(null));
            }
            document.add(dTable);
        }
    }

    private Cell headerCell(String text) {
        return new Cell().add(new Paragraph(text).setFontSize(9).setBold().setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(PRIMARY_BLUE).setBorder(null).setPadding(5);
    }

    private Cell dataCell(String text) {
        return new Cell().add(new Paragraph(text != null ? text : "—").setFontSize(8))
            .setBorder(null).setBackgroundColor(LIGHT_GRAY).setPadding(4);
    }


    // ------------------------------------------------------------------
    // Date range resolution — handles all formats sent from the frontend:
    //   1. Explicit startDate / endDate LocalDate fields
    //   2. year + month integers  (month is 1-12)
    //   3. month string  "YYYY-MM"
    //   4. date string   "YYYY-MM-DD"  (used for DAILY reports)
    //   5. Report-type-based default (current month)
    // ------------------------------------------------------------------
    private LocalDate resolveStartDate(ReportRequestDTO req) {
        if (req.getStartDate() != null) return req.getStartDate();

        // year + month2 int fields (e.g. year=2026, month2=9)
        if (req.getYear() != null && req.getMonth2() != null) {
            return LocalDate.of(req.getYear(), req.getMonth2(), 1);
        }

        // year + month as numeric string (JSON int deserialized to String: "9")
        if (req.getYear() != null && req.getMonth() != null) {
            try {
                int m = Integer.parseInt(req.getMonth().trim());
                return LocalDate.of(req.getYear(), m, 1);
            } catch (NumberFormatException ignored) {}
        }

        // year only — default to January
        if (req.getYear() != null) {
            return LocalDate.of(req.getYear(), 1, 1);
        }

        // month string "YYYY-MM"
        if (req.getMonth() != null && req.getMonth().contains("-")) {
            try {
                String[] parts = req.getMonth().split("-");
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
            } catch (Exception ignored) {}
        }

        // date string "YYYY-MM-DD"
        if (req.getDate() != null) {
            try { return LocalDate.parse(req.getDate()); } catch (Exception ignored) {}
        }

        // Default: first day of last month (so demo data is always in range)
        return LocalDate.now().minusMonths(1).withDayOfMonth(1);
    }

    private LocalDate resolveEndDate(ReportRequestDTO req, LocalDate startDate) {
        if (req.getEndDate() != null) return req.getEndDate();

        String rtype = req.getReportType() != null ? req.getReportType().toUpperCase() : "MONTHLY";
        return switch (rtype) {
            case "DAILY"   -> startDate;                        // same day
            case "WEEKLY"  -> startDate.plusDays(6);           // 7-day window
            case "MONTHLY" -> startDate.withDayOfMonth(startDate.lengthOfMonth()); // full month
            default        -> LocalDate.now();                  // CUSTOM or TRIP: up to today
        };
    }

    // ------------------------------------------------------------------

    private void generateDriverPdf(String filePath, Driver driver, LocalDate startDate, LocalDate endDate, String reportType) throws Exception {
        DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter tFmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to   = endDate.plusDays(1).atStartOfDay();

        // Get data
        List<DriverDailyStats> statsList = statsRepository.findByDriverIdAndStatDateBetweenOrderByStatDateAsc(driver.getId(), startDate, endDate);
        List<Alert> alerts = alertRepository.findByDriverIdAndOccurredAtBetween(driver.getId(), from, to);
        List<Trip> trips = tripRepository.findByDriverIdAndStartTimeBetweenOrderByStartTimeDesc(driver.getId(), from, to);

        // Aggregate
        int totalTrips = trips.size();
        double totalDistKm = trips.stream().mapToDouble(t -> t.getDistanceKm() != null ? t.getDistanceKm().doubleValue() : 0).sum();
        int overspeed = statsList.stream().mapToInt(s -> s.getOverspeedEvents() != null ? s.getOverspeedEvents() : 0).sum();
        int harshBraking = statsList.stream().mapToInt(s -> s.getHarshBrakingEvents() != null ? s.getHarshBrakingEvents() : 0).sum();
        int harshAccel = statsList.stream().mapToInt(s -> s.getHarshAccelerationEvents() != null ? s.getHarshAccelerationEvents() : 0).sum();
        int nightDrivingSeconds = statsList.stream().mapToInt(s -> s.getNightDrivingSeconds() != null ? s.getNightDrivingSeconds() : 0).sum();
        int fatigueEvents = statsList.stream().mapToInt(s -> s.getFatigueEvents() != null ? s.getFatigueEvents() : 0).sum();
        OptionalDouble avgSafetyScore = statsList.stream().filter(s -> s.getSafetyScore() != null)
            .mapToDouble(s -> s.getSafetyScore().doubleValue()).average();
        double safetyScore = avgSafetyScore.isPresent() ? avgSafetyScore.getAsDouble() : 0;

        // Alert type breakdown
        Map<String, Long> alertsByType = alerts.stream()
            .collect(Collectors.groupingBy(Alert::getAlertType, Collectors.counting()));

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(filePath));
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            // ---- HEADER BANNER ----
            Table header = new Table(UnitValue.createPercentArray(new float[]{70, 30})).useAllAvailableWidth();
            Cell titleCell = new Cell().add(new Paragraph("FLEET MANAGEMENT SYSTEM")
                .setFontSize(9).setFontColor(new DeviceRgb(148, 163, 184)))
                .add(new Paragraph("Driver Performance Report")
                .setFontSize(22).setBold().setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(driver.getName() + " (" + driver.getCode() + ")")
                .setFontSize(13).setFontColor(new DeviceRgb(147, 197, 253)))
                .setBackgroundColor(HEADER_DARK).setPadding(16).setBorder(null);
            header.addCell(titleCell);

            Cell dateCell = new Cell()
                .add(new Paragraph("Report Period").setFontSize(9).setFontColor(new DeviceRgb(148, 163, 184)))
                .add(new Paragraph(startDate.format(dFmt) + "\nto\n" + endDate.format(dFmt))
                    .setFontSize(11).setBold().setFontColor(ColorConstants.WHITE))
                .add(new Paragraph("Generated: " + LocalDateTime.now().format(tFmt))
                    .setFontSize(8).setFontColor(new DeviceRgb(148, 163, 184)))
                .setBackgroundColor(HEADER_DARK).setPadding(16).setTextAlignment(TextAlignment.RIGHT).setBorder(null);
            header.addCell(dateCell);
            doc.add(header);

            // ---- DRIVER INFO ----
            addSectionTitle(doc, "Driver Information");
            Table driverInfo = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25})).useAllAvailableWidth();
            addInfoCard(driverInfo, "Driver Code", driver.getCode());
            addInfoCard(driverInfo, "Full Name", driver.getName());
            addInfoCard(driverInfo, "Phone", driver.getPhone() != null ? driver.getPhone() : "N/A");
            addInfoCard(driverInfo, "License", driver.getLicenseNumber() != null ? driver.getLicenseNumber() : "N/A");
            doc.add(driverInfo);

            // ---- PERFORMANCE SUMMARY ----
            addSectionTitle(doc, "Performance Summary");
            Table perf = new Table(UnitValue.createPercentArray(new float[]{16, 16, 16, 16, 16, 16})).useAllAvailableWidth();
            addMetricCard(perf, "Safety Score", String.format("%.1f/100", safetyScore), safetyScore >= 80 ? SUCCESS_GREEN : safetyScore >= 60 ? WARNING_AMBER : DANGER_RED);
            addMetricCard(perf, "Total Trips", String.valueOf(totalTrips), PRIMARY_BLUE);
            addMetricCard(perf, "Distance", String.format("%.1f km", totalDistKm), PRIMARY_BLUE);
            addMetricCard(perf, "Overspeed", String.valueOf(overspeed), overspeed == 0 ? SUCCESS_GREEN : DANGER_RED);
            addMetricCard(perf, "Harsh Braking", String.valueOf(harshBraking), harshBraking == 0 ? SUCCESS_GREEN : WARNING_AMBER);
            addMetricCard(perf, "Night Driving", String.format("%.1f hrs", nightDrivingSeconds / 3600.0), nightDrivingSeconds == 0 ? SUCCESS_GREEN : WARNING_AMBER);
            doc.add(perf);

            // ---- SAFETY SCORE EXPLANATION ----
            addSectionTitle(doc, "Safety Score Breakdown");
            Paragraph scoreExplanation = new Paragraph()
                .add("Safety Score Formula: 100")
                .add(" - (Overspeed×5) - (Harsh Braking×4) - (Harsh Acceleration×4)")
                .add(" - (Night Driving hrs×3) - (Fatigue×8) + (Clean Trips×1)")
                .add("\nDeductions this period: ")
                .add(String.format("Overspeed: -%d | Harsh Braking: -%d | Harsh Acceleration: -%d | Night Driving: -%.1f | Fatigue: -%d",
                    overspeed * 5, harshBraking * 4, harshAccel * 4, nightDrivingSeconds / 3600.0 * 3, fatigueEvents * 8))
                .setFontSize(10).setFontColor(TEXT_MUTED)
                .setBackgroundColor(LIGHT_GRAY).setPadding(8).setMarginBottom(10);
            doc.add(scoreExplanation);

            // ---- ALERT SUMMARY ----
            addSectionTitle(doc, "Alert Summary");
            Table alertTable = new Table(UnitValue.createPercentArray(new float[]{60, 20, 20})).useAllAvailableWidth();
            addTableHeader(alertTable, "Alert Type", "Count", "");
            for (Map.Entry<String, Long> entry : alertsByType.entrySet()) {
                alertTable.addCell(new Cell().add(new Paragraph(entry.getKey()).setFontSize(10)));
                alertTable.addCell(new Cell().add(new Paragraph(String.valueOf(entry.getValue())).setFontSize(10).setBold()));
                alertTable.addCell(new Cell().add(new Paragraph("").setFontSize(10)));
            }
            if (alertsByType.isEmpty()) {
                Cell noAlerts = new Cell(1, 3).add(new Paragraph("No alerts in this period — excellent driving!").setFontSize(10).setFontColor(SUCCESS_GREEN));
                alertTable.addCell(noAlerts);
            }
            doc.add(alertTable);

            // ---- ENTRY/EXIT TRACKING (Trips Table) ----
            addSectionTitle(doc, "Trip Entry/Exit Tracking");
            Table tripTable = new Table(UnitValue.createPercentArray(new float[]{15, 20, 20, 15, 15, 15})).useAllAvailableWidth();
            addTableHeader(tripTable, "Trip", "Entry (Start)", "Exit (End)", "Distance", "Duration", "Max Speed");
            int idx = 1;
            for (Trip t : trips.stream().limit(20).collect(Collectors.toList())) {
                tripTable.addCell(new Cell().add(new Paragraph(String.valueOf(idx++)).setFontSize(9)));
                tripTable.addCell(new Cell().add(new Paragraph(t.getStartTime() != null ? t.getStartTime().format(tFmt) : "N/A").setFontSize(9)));
                tripTable.addCell(new Cell().add(new Paragraph(t.getEndTime() != null ? t.getEndTime().format(tFmt) : "Active").setFontSize(9)));
                tripTable.addCell(new Cell().add(new Paragraph(t.getDistanceKm() != null ? t.getDistanceKm().setScale(1, RoundingMode.HALF_UP) + " km" : "N/A").setFontSize(9)));
                tripTable.addCell(new Cell().add(new Paragraph(t.getDurationSeconds() != null ? (t.getDurationSeconds() / 60) + " min" : "N/A").setFontSize(9)));
                tripTable.addCell(new Cell().add(new Paragraph(t.getMaxSpeedKmph() != null ? t.getMaxSpeedKmph().setScale(0, RoundingMode.HALF_UP) + " km/h" : "N/A").setFontSize(9)));
            }
            if (trips.isEmpty()) {
                Cell noTrips = new Cell(1, 6).add(new Paragraph("No trips recorded in this period.").setFontSize(10).setFontColor(TEXT_MUTED));
                tripTable.addCell(noTrips);
            } else if (trips.size() > 20) {
                Cell more = new Cell(1, 6).add(new Paragraph("... and " + (trips.size() - 20) + " more trips. See full list in the app.").setFontSize(9).setFontColor(TEXT_MUTED));
                tripTable.addCell(more);
            }
            doc.add(tripTable);

            // ---- FOOTER ----
            doc.add(new Paragraph("\n"));
            Paragraph footer = new Paragraph("GPS Fleet Tracking System | Report generated on " + LocalDateTime.now().format(tFmt) +
                " | This report is system-generated. For queries, contact fleet@trackfleet.com")
                .setFontSize(8).setFontColor(TEXT_MUTED).setTextAlignment(TextAlignment.CENTER);
            doc.add(footer);
        }
    }

    // ----------------------------------------------------------------
    // FLEET PDF GENERATION
    // ----------------------------------------------------------------
    private void generateFleetPdf(String filePath, List<Driver> drivers, LocalDate startDate, LocalDate endDate) throws Exception {
        DateTimeFormatter dFmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter tFmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(filePath));
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(36, 36, 36, 36);

            Table header = new Table(UnitValue.createPercentArray(new float[]{70, 30})).useAllAvailableWidth();
            header.addCell(new Cell().add(new Paragraph("FLEET MANAGEMENT SYSTEM").setFontSize(9).setFontColor(new DeviceRgb(148, 163, 184)))
                .add(new Paragraph("Fleet Monthly Report").setFontSize(22).setBold().setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(startDate.format(dFmt) + " to " + endDate.format(dFmt)).setFontSize(12).setFontColor(new DeviceRgb(147, 197, 253)))
                .setBackgroundColor(HEADER_DARK).setPadding(16).setBorder(null));
            header.addCell(new Cell().add(new Paragraph("Generated").setFontSize(9).setFontColor(new DeviceRgb(148, 163, 184)))
                .add(new Paragraph(LocalDateTime.now().format(tFmt)).setFontSize(11).setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(drivers.size() + " Active Drivers").setFontSize(10).setFontColor(new DeviceRgb(147, 197, 253)))
                .setBackgroundColor(HEADER_DARK).setPadding(16).setTextAlignment(TextAlignment.RIGHT).setBorder(null));
            doc.add(header);

            addSectionTitle(doc, "Driver Performance Ranking");
            Table rankTable = new Table(UnitValue.createPercentArray(new float[]{8, 20, 15, 12, 12, 12, 11, 10})).useAllAvailableWidth();
            addTableHeader(rankTable, "Rank", "Driver", "Code", "Trips", "Distance", "Safety Score", "Alerts", "Overspeed");

            int rank = 1;
            List<DriverDailyStats> allStats = new ArrayList<>();
            for (Driver d : drivers) {
                List<DriverDailyStats> dStats = statsRepository.findByDriverIdAndStatDateBetweenOrderByStatDateAsc(d.getId(), startDate, endDate);
                allStats.addAll(dStats);

                int trips = dStats.stream().mapToInt(s -> s.getTripCount() != null ? s.getTripCount() : 0).sum();
                double dist = dStats.stream().mapToDouble(s -> s.getDistanceKm() != null ? s.getDistanceKm().doubleValue() : 0).sum();
                OptionalDouble scoreOpt = dStats.stream().filter(s -> s.getSafetyScore() != null).mapToDouble(s -> s.getSafetyScore().doubleValue()).average();
                double score = scoreOpt.isPresent() ? scoreOpt.getAsDouble() : 0;
                long alertCount = alertRepository.countByDriverAndDateRange(d.getId(), from, to);
                int overspeedEvts = dStats.stream().mapToInt(s -> s.getOverspeedEvents() != null ? s.getOverspeedEvents() : 0).sum();

                DeviceRgb scoreColor = score >= 80 ? SUCCESS_GREEN : score >= 60 ? WARNING_AMBER : DANGER_RED;

                rankTable.addCell(new Cell().add(new Paragraph("#" + rank++).setFontSize(9).setBold()));
                rankTable.addCell(new Cell().add(new Paragraph(d.getName()).setFontSize(9)));
                rankTable.addCell(new Cell().add(new Paragraph(d.getCode()).setFontSize(9)));
                rankTable.addCell(new Cell().add(new Paragraph(String.valueOf(trips)).setFontSize(9)));
                rankTable.addCell(new Cell().add(new Paragraph(String.format("%.1f km", dist)).setFontSize(9)));
                rankTable.addCell(new Cell().add(new Paragraph(String.format("%.1f", score)).setFontSize(9).setBold().setFontColor(scoreColor)));
                rankTable.addCell(new Cell().add(new Paragraph(String.valueOf(alertCount)).setFontSize(9)));
                rankTable.addCell(new Cell().add(new Paragraph(String.valueOf(overspeedEvts)).setFontSize(9)));
            }
            doc.add(rankTable);

            doc.add(new Paragraph("\n"));
            doc.add(new Paragraph("GPS Fleet Tracking System | Fleet Report | " + LocalDateTime.now().format(tFmt))
                .setFontSize(8).setFontColor(TEXT_MUTED).setTextAlignment(TextAlignment.CENTER));
        }
    }

    // ---- Helpers ----
    private void addSectionTitle(Document doc, String title) {
        doc.add(new Paragraph(title)
            .setFontSize(13).setBold().setFontColor(HEADER_DARK)
            .setMarginTop(16).setMarginBottom(6)
            .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(PRIMARY_BLUE, 1.5f)));
    }

    private void addInfoCard(Table table, String label, String value) {
        Cell cell = new Cell()
            .add(new Paragraph(label).setFontSize(8).setFontColor(TEXT_MUTED))
            .add(new Paragraph(value != null ? value : "N/A").setFontSize(11).setBold())
            .setBackgroundColor(LIGHT_GRAY).setPadding(8).setBorder(null).setMargin(2);
        table.addCell(cell);
    }

    private void addMetricCard(Table table, String label, String value, DeviceRgb color) {
        Cell cell = new Cell()
            .add(new Paragraph(label).setFontSize(8).setFontColor(TEXT_MUTED))
            .add(new Paragraph(value).setFontSize(14).setBold().setFontColor(color))
            .setBackgroundColor(LIGHT_GRAY).setPadding(8).setBorder(null).setMargin(2)
            .setTextAlignment(TextAlignment.CENTER);
        table.addCell(cell);
    }

    private void addTableHeader(Table table, String... headers) {
        for (String h : headers) {
            table.addHeaderCell(new Cell().add(new Paragraph(h)
                .setFontSize(9).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(PRIMARY_BLUE).setPadding(6).setBorder(null));
        }
    }

    public String getReportFilePath(String reportId) {
        return reportsOutputDir + File.separator + reportId + ".pdf";
    }
}
