package com.gps.tracking.service;

import com.gps.tracking.dto.*;
import com.gps.tracking.entity.*;
import com.gps.tracking.repository.*;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    public static final ZoneId ASIA_KOLKATA = ZoneId.of("Asia/Kolkata");

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;
    private final DriverDailyStatsRepository statsRepository;
    private final SafetyScoreService safetyScoreService;

    @Value("${reports.output-dir:./reports/generated}")
    private String reportsOutputDir;

    // Corporate Color Palette
    private static final DeviceRgb PRIMARY_BLUE = new DeviceRgb(30, 64, 175);   // #1E40AF Royal Blue
    private static final DeviceRgb HEADER_DARK  = new DeviceRgb(15, 23, 42);   // #0F172A Slate-900
    private static final DeviceRgb ACCENT_BLUE  = new DeviceRgb(37, 99, 235);  // #2563EB Blue
    private static final DeviceRgb TABLE_HEADER = new DeviceRgb(30, 58, 138);  // #1E3A8A Dark Navy
    private static final DeviceRgb ROW_EVEN     = new DeviceRgb(248, 250, 252);// #F8FAFC
    private static final DeviceRgb ROW_ODD      = new DeviceRgb(255, 255, 255);// #FFFFFF
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(226, 232, 240);// #E2E8F0
    private static final DeviceRgb TEXT_PRIMARY = new DeviceRgb(15, 23, 42);   // #0F172A
    private static final DeviceRgb TEXT_MUTED   = new DeviceRgb(100, 116, 139);// #64748B
    private static final DeviceRgb SUCCESS_GREEN= new DeviceRgb(22, 163, 74);  // #16A34A
    private static final DeviceRgb DANGER_RED   = new DeviceRgb(220, 38, 38);  // #DC2626
    private static final DeviceRgb WARNING_AMBER= new DeviceRgb(217, 119, 6);  // #D97706

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter GEN_FMT  = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    // ==================================================================
    // 1. DRIVER REPORT GENERATION (All 4 Combinations)
    // ==================================================================
    public ReportResponseDTO generateDriverReport(ReportRequestDTO req) {
        Driver driver = req.getDriverId() != null
                ? driverRepository.findById(req.getDriverId()).orElseThrow()
                : driverRepository.findByCode(req.getDriverCode()).orElseThrow();

        LocalDate startDate = resolveStartDate(req);
        LocalDate endDate   = resolveEndDate(req, startDate);

        // Half-open interval in Asia/Kolkata: [from, to)
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to   = endDate.plusDays(1).atStartOfDay();

        String rType = req.getReportType() != null ? req.getReportType().toUpperCase() : "MONTHLY";

        // 1. Retrieve all trips and alerts for this driver in the half-open date range
        List<Trip> trips = tripRepository.findTripsByDriverAndDateRange(driver.getId(), from, to);
        List<Alert> alerts = alertRepository.findAlertsByDriverAndDateRange(driver.getId(), from, to);
        List<DriverDailyStats> statsList = statsRepository
                .findByDriverIdAndStatDateBetweenOrderByStatDateAsc(driver.getId(), startDate, endDate);

        int totalTrips = trips.size();
        double totalDistKm = trips.stream()
                .mapToDouble(t -> t.getDistanceKm() != null ? t.getDistanceKm().doubleValue() : 0.0)
                .sum();
        long totalDurationSec = trips.stream()
                .mapToLong(this::calculateTripDurationSeconds)
                .sum();
        String totalDurationFormatted = formatDuration(totalDurationSec);

        long vehiclesEntered = trips.stream()
                .map(t -> t.getVehicle() != null ? t.getVehicle().getId() : null)
                .filter(Objects::nonNull).distinct().count();
        long vehiclesExited = trips.stream()
                .filter(t -> t.getEndTime() != null)
                .map(t -> t.getVehicle() != null ? t.getVehicle().getId() : null)
                .filter(Objects::nonNull).distinct().count();

        // Alert category breakdown
        long overspeed = alerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("overspeed")).count();
        long harshBraking = alerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("braking")).count();
        long harshAccel = alerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("accel")).count();
        long gpsDisc = alerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("gps")).count();
        long nightDriving = alerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("night")).count();
        long fatigue = alerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("fatigue")).count();

        int nightSec = statsList.stream()
                .mapToInt(s -> s.getNightDrivingSeconds() != null ? s.getNightDrivingSeconds() : 0)
                .sum();
        double nightHours = nightSec > 0 ? nightSec / 3600.0 : (nightDriving * 1.5);

        // Standardized Safety Score calculation
        SafetyScoreBreakdownDTO breakdown = safetyScoreService.calculateDriverScore(
                driver, totalTrips, totalDistKm,
                overspeed, harshBraking, harshAccel, gpsDisc, nightDriving, nightHours, fatigue,
                from, to);

        // SECTION B: Vehicles Operated by Driver in this period (Historical association)
        Map<String, DriverVehicleSummaryDTO> vehicleSummaryMap = new LinkedHashMap<>();
        for (Trip t : trips) {
            String vNum = t.getVehicle() != null ? t.getVehicle().getRegistrationNumber() : "Unknown";
            Long vId = t.getVehicle() != null ? t.getVehicle().getId() : null;
            long durSec = calculateTripDurationSeconds(t);

            DriverVehicleSummaryDTO summary = vehicleSummaryMap.computeIfAbsent(vNum, vn -> DriverVehicleSummaryDTO.builder()
                    .driverId(driver.getId())
                    .driverCode(driver.getCode())
                    .driverName(driver.getName())
                    .vehicleId(vId)
                    .vehicleNumber(vn)
                    .trips(0L)
                    .distanceKm(BigDecimal.ZERO)
                    .durationSeconds(0L)
                    .formattedDuration("00h 00m 00s")
                    .alerts(0L)
                    .overspeedCount(0L)
                    .harshBrakingCount(0L)
                    .otherAlertCount(0L)
                    .build());
            summary.setTrips(summary.getTrips() + 1);
            if (t.getDistanceKm() != null) {
                summary.setDistanceKm(summary.getDistanceKm().add(t.getDistanceKm()).setScale(2, RoundingMode.HALF_UP));
            }
            summary.setDurationSeconds(summary.getDurationSeconds() + durSec);
            summary.setFormattedDuration(formatDuration(summary.getDurationSeconds()));
        }

        for (Alert a : alerts) {
            String vNum = a.getVehicle() != null ? a.getVehicle().getRegistrationNumber() : "Unknown";
            DriverVehicleSummaryDTO summary = vehicleSummaryMap.get(vNum);
            if (summary != null) {
                summary.setAlerts(summary.getAlerts() + 1);
                String at = a.getAlertType() != null ? a.getAlertType().toLowerCase() : "";
                if (at.contains("overspeed")) {
                    summary.setOverspeedCount(summary.getOverspeedCount() + 1);
                } else if (at.contains("braking")) {
                    summary.setHarshBrakingCount(summary.getHarshBrakingCount() + 1);
                } else {
                    summary.setOtherAlertCount(summary.getOtherAlertCount() + 1);
                }
            }
        }
        List<DriverVehicleSummaryDTO> vehiclesOperatedList = new ArrayList<>(vehicleSummaryMap.values());
        vehiclesOperatedList.sort((a, b) -> Long.compare(b.getTrips(), a.getTrips()));

        // SECTION C: Detailed Trip Tracking rows (16 columns)
        List<TripTrackingDTO> tripTrackingList = new ArrayList<>();
        for (Trip t : trips) {
            long durSec = calculateTripDurationSeconds(t);
            tripTrackingList.add(TripTrackingDTO.builder()
                    .tripId(t.getId())
                    .vehicleNumber(t.getVehicle() != null ? t.getVehicle().getRegistrationNumber() : "N/A")
                    .driverName(driver.getName())
                    .tripStartDate(t.getStartTime() != null ? t.getStartTime().format(DATE_FMT) : "N/A")
                    .entryStartTime(t.getStartTime() != null ? t.getStartTime().format(TIME_FMT) : "N/A")
                    .startLocation(formatLocation(t.getPrimaryRoad(), t.getStartLatitude(), t.getStartLongitude(), "GPS Start"))
                    .tripEndDate(t.getEndTime() != null ? t.getEndTime().format(DATE_FMT) : "Active")
                    .exitEndTime(t.getEndTime() != null ? t.getEndTime().format(TIME_FMT) : "Active")
                    .endLocation(formatLocation(null, t.getEndLatitude(), t.getEndLongitude(), t.getEndTime() != null ? "GPS End" : "Active / In Transit"))
                    .destination(t.getPrimaryRoad() != null && !t.getPrimaryRoad().isBlank() ? t.getPrimaryRoad() : "N/A")
                    .totalDistanceKm(t.getDistanceKm() != null ? t.getDistanceKm().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .totalTripDurationFormatted(formatDuration(durSec))
                    .durationSeconds(durSec)
                    .averageSpeedKmph(t.getAverageSpeedKmph() != null ? t.getAverageSpeedKmph().setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .maxSpeedKmph(t.getMaxSpeedKmph() != null ? t.getMaxSpeedKmph().setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .alertsDuringTrip(t.getAlertCount() != null ? t.getAlertCount() : 0)
                    .tripStatus(t.getStatus() != null ? t.getStatus() : "COMPLETED")
                    .build());
        }

        // SECTION D: Alert Event Details and Grouped Summaries (8 columns)
        List<AlertReportItemDTO> alertEvents = new ArrayList<>();
        Map<String, AlertReportItemDTO> alertSummaryMap = new LinkedHashMap<>();

        Map<String, Long> alertTypeCounts = new LinkedHashMap<>();
        Map<String, Long> alertSeverityCounts = new LinkedHashMap<>();
        Map<String, Long> alertStatusCounts = new LinkedHashMap<>();

        for (Alert a : alerts) {
            String vNum = a.getVehicle() != null ? a.getVehicle().getRegistrationNumber() : "N/A";
            String aDate = a.getOccurredAt() != null ? a.getOccurredAt().format(DATE_FMT) : "N/A";
            String aTime = a.getOccurredAt() != null ? a.getOccurredAt().format(TIME_FMT) : "N/A";
            String aType = a.getAlertType() != null ? a.getAlertType() : "Unknown Alert";
            String sev   = a.getSeverity() != null ? a.getSeverity() : "WARNING";
            String stat  = a.getStatus() != null ? a.getStatus() : "OPEN";

            alertTypeCounts.put(aType, alertTypeCounts.getOrDefault(aType, 0L) + 1);
            alertSeverityCounts.put(sev, alertSeverityCounts.getOrDefault(sev, 0L) + 1);
            alertStatusCounts.put(stat, alertStatusCounts.getOrDefault(stat, 0L) + 1);

            alertEvents.add(AlertReportItemDTO.builder()
                    .alertId(a.getId())
                    .vehicleNumber(vNum)
                    .date(aDate)
                    .time(aTime)
                    .alertType(aType)
                    .severity(sev)
                    .count(1L)
                    .driverName(driver.getName())
                    .status(stat)
                    .build());

            String groupKey = vNum + "|" + aDate + "|" + aType + "|" + sev + "|" + stat;
            AlertReportItemDTO grouped = alertSummaryMap.computeIfAbsent(groupKey, k -> AlertReportItemDTO.builder()
                    .vehicleNumber(vNum)
                    .date(aDate)
                    .time(aTime)
                    .alertType(aType)
                    .severity(sev)
                    .count(0L)
                    .driverName(driver.getName())
                    .status(stat)
                    .build());
            grouped.setCount(grouped.getCount() + 1);
        }
        List<AlertReportItemDTO> alertSummaries = new ArrayList<>(alertSummaryMap.values());

        String reportId = "DRVRPT_" + driver.getCode() + "_" + rType + "_" + startDate + "_" + UUID.randomUUID().toString().substring(0, 6);
        String fileName = reportId + ".pdf";

        try {
            Files.createDirectories(Paths.get(reportsOutputDir));
            String filePath = reportsOutputDir + File.separator + fileName;

            generateDriverPdfV2(filePath, driver, startDate, endDate, rType,
                    breakdown, vehiclesEntered, vehiclesExited, totalDurationFormatted,
                    vehiclesOperatedList, tripTrackingList, alertSummaries,
                    alertTypeCounts, alertSeverityCounts, alertStatusCounts);

            Map<String, Object> reportDataMap = new LinkedHashMap<>();
            reportDataMap.put("driverName", driver.getName());
            reportDataMap.put("driverCode", driver.getCode());
            reportDataMap.put("driverId", driver.getId());
            reportDataMap.put("reportType", rType);
            reportDataMap.put("startDate", startDate.toString());
            reportDataMap.put("endDate", endDate.toString());
            reportDataMap.put("safetyScore", breakdown.getFinalSafetyScore());
            reportDataMap.put("performanceCategory", breakdown.getPerformanceCategory());
            reportDataMap.put("safetyBreakdown", breakdown);
            reportDataMap.put("totalVehiclesEntered", vehiclesEntered);
            reportDataMap.put("totalVehiclesExited", vehiclesExited);
            reportDataMap.put("totalTrips", totalTrips);
            reportDataMap.put("totalDistanceKm", BigDecimal.valueOf(totalDistKm).setScale(2, RoundingMode.HALF_UP));
            reportDataMap.put("totalDurationSeconds", totalDurationSec);
            reportDataMap.put("totalDurationFormatted", totalDurationFormatted);
            reportDataMap.put("totalAlerts", alerts.size());
            reportDataMap.put("vehiclesOperated", vehiclesOperatedList);
            reportDataMap.put("trips", tripTrackingList);
            reportDataMap.put("alertSummaries", alertSummaries);
            reportDataMap.put("alertEvents", alertEvents);
            reportDataMap.put("alertTypeCounts", alertTypeCounts);
            reportDataMap.put("alertSeverityCounts", alertSeverityCounts);
            reportDataMap.put("alertStatusCounts", alertStatusCounts);

            ReportResponseDTO resp = new ReportResponseDTO();
            resp.setReportId(reportId);
            resp.setReportType(rType);
            resp.setStatus("READY");
            resp.setPdfUrl("/api/reports/" + reportId + "/pdf");
            resp.setDownloadUrl("/api/reports/" + reportId + "/download");
            resp.setGeneratedAt(LocalDateTime.now());
            resp.setMessage("Driver " + rType.toLowerCase() + " report generated successfully with all 4 sections");
            resp.setReportData(reportDataMap);
            return resp;

        } catch (Exception e) {
            log.error("Error generating driver report for {}", driver.getCode(), e);
            ReportResponseDTO resp = new ReportResponseDTO();
            resp.setStatus("FAILED");
            resp.setMessage("Error generating report: " + e.getMessage());
            return resp;
        }
    }

    // ==================================================================
    // 2. FLEET REPORT GENERATION (All 4 Combinations)
    // ==================================================================
    public ReportResponseDTO generateFleetReport(ReportRequestDTO req) {
        OverallReportDTO overall = generateOverallReport(req);
        ReportResponseDTO resp = new ReportResponseDTO();
        resp.setReportId(overall.getReportId());
        resp.setReportType(overall.getReportType());
        resp.setStatus("READY");
        resp.setPdfUrl(overall.getPdfUrl());
        resp.setDownloadUrl(overall.getDownloadUrl());
        resp.setGeneratedAt(overall.getGeneratedAt());
        resp.setMessage("Fleet " + overall.getReportType().toLowerCase() + " report generated successfully with all 4 sections");
        resp.setReportData(overall);
        return resp;
    }

    // ==================================================================
    // 3. OVERALL FLEET REPORT DATA & PDF (Daily / Weekly / Monthly / Custom)
    // ==================================================================
    public OverallReportDTO generateOverallReport(ReportRequestDTO req) {
        LocalDate startDate = resolveStartDate(req);
        LocalDate endDate   = resolveEndDate(req, startDate);
        LocalDateTime from  = startDate.atStartOfDay();
        LocalDateTime to    = endDate.plusDays(1).atStartOfDay();

        String rType = req.getReportType() != null ? req.getReportType().toUpperCase() : "MONTHLY";
        if ("FLEET".equalsIgnoreCase(rType)) {
            rType = "MONTHLY";
        }

        // 1. Vehicle entry / exit counts
        long vehiclesEntered = tripRepository.countDistinctVehiclesWithTripStarted(from, to);
        long vehiclesExited  = tripRepository.countDistinctVehiclesWithTripCompleted(from, to);

        // 2. All Trips and Alerts for the fleet in this half-open date range
        List<Trip> allTrips = tripRepository.findByStartTimeBetween(from, to);
        List<Alert> allAlerts = alertRepository.findAlertsInDateRange(from, to);

        long totalTrips = allTrips.size();
        double totalDistKm = allTrips.stream()
                .mapToDouble(t -> t.getDistanceKm() != null ? t.getDistanceKm().doubleValue() : 0.0)
                .sum();
        long totalDurationSec = allTrips.stream()
                .mapToLong(this::calculateTripDurationSeconds)
                .sum();
        String totalDurationFormatted = formatDuration(totalDurationSec);
        long totalAlerts = allAlerts.size();

        // 3. Aggregate Summaries: Alert Types, Severities, Statuses, Vehicles
        Map<String, Long> alertTypeSummary = new LinkedHashMap<>();
        Map<String, Long> alertSeveritySummary = new LinkedHashMap<>();
        Map<String, Long> alertStatusSummary = new LinkedHashMap<>();
        Map<String, Long> vehicleAlertSummary = new LinkedHashMap<>();

        for (Alert a : allAlerts) {
            String at = a.getAlertType() != null ? a.getAlertType() : "Unknown";
            String sev = a.getSeverity() != null ? a.getSeverity() : "WARNING";
            String stat = a.getStatus() != null ? a.getStatus() : "OPEN";
            String vReg = a.getVehicle() != null ? a.getVehicle().getRegistrationNumber() : "Unknown Vehicle";

            alertTypeSummary.put(at, alertTypeSummary.getOrDefault(at, 0L) + 1);
            alertSeveritySummary.put(sev, alertSeveritySummary.getOrDefault(sev, 0L) + 1);
            alertStatusSummary.put(stat, alertStatusSummary.getOrDefault(stat, 0L) + 1);
            vehicleAlertSummary.put(vReg, vehicleAlertSummary.getOrDefault(vReg, 0L) + 1);
        }

        // 4. Per-Driver Summary & Ranking (Section A)
        List<Driver> allDrivers = driverRepository.findAll();
        List<OverallReportDTO.DriverAlertRow> driverRows = new ArrayList<>();
        double scoreSum = 0.0;
        int activeDriverCount = 0;

        for (Driver d : allDrivers) {
            List<Trip> dTrips = allTrips.stream()
                    .filter(t -> t.getDriver() != null && t.getDriver().getId().equals(d.getId()))
                    .collect(Collectors.toList());

            List<Alert> dAlerts = allAlerts.stream()
                    .filter(a -> (a.getDriver() != null && a.getDriver().getId().equals(d.getId()))
                            || (a.getDriver() == null && a.getTrip() != null && a.getTrip().getDriver() != null && a.getTrip().getDriver().getId().equals(d.getId())))
                    .collect(Collectors.toList());

            if (dTrips.isEmpty() && dAlerts.isEmpty()) {
                continue; // Only drivers with activity in this reporting period
            }

            activeDriverCount++;

            String vehiclesOperated = dTrips.stream()
                    .map(t -> t.getVehicle() != null ? t.getVehicle().getRegistrationNumber() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (vehiclesOperated.isEmpty()) vehiclesOperated = "—";

            long tripsCount = dTrips.size();
            double distKm = dTrips.stream().mapToDouble(t -> t.getDistanceKm() != null ? t.getDistanceKm().doubleValue() : 0.0).sum();
            long dDurationSec = dTrips.stream().mapToLong(this::calculateTripDurationSeconds).sum();

            long ov = dAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("overspeed")).count();
            long hb = dAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("braking")).count();
            long ha = dAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("accel")).count();
            long gps = dAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("gps")).count();
            long night = dAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("night")).count();
            long ign = dAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("ignition")).count();
            long fat = dAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("fatigue")).count();
            long other = dAlerts.size() - (ov + hb + ha + gps + night + ign + fat);

            SafetyScoreBreakdownDTO sc = safetyScoreService.calculateDriverScore(
                    d, (int) tripsCount, distKm, ov, hb, ha, gps, night, night * 1.5, fat, from, to);

            scoreSum += sc.getFinalSafetyScore().doubleValue();

            driverRows.add(OverallReportDTO.DriverAlertRow.builder()
                    .driverId(d.getId())
                    .driverCode(d.getCode())
                    .driverName(d.getName())
                    .vehicles(vehiclesOperated)
                    .tripCount(tripsCount)
                    .totalDistanceKm(BigDecimal.valueOf(distKm).setScale(2, RoundingMode.HALF_UP))
                    .formattedDuration(formatDuration(dDurationSec))
                    .safetyScore(sc.getFinalSafetyScore())
                    .performanceCategory(sc.getPerformanceCategory())
                    .totalAlerts(dAlerts.size())
                    .overspeed(ov)
                    .harshBraking(hb)
                    .harshAcceleration(ha)
                    .gpsDisconnect(gps)
                    .nightDriving(night)
                    .ignition(ign)
                    .fatigue(fat)
                    .other(Math.max(0, other))
                    .build());
        }

        // Sort ranking descending by safety score
        driverRows.sort((a, b) -> b.getSafetyScore().compareTo(a.getSafetyScore()));

        BigDecimal avgFleetScore = activeDriverCount > 0
                ? BigDecimal.valueOf(scoreSum / activeDriverCount).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP);

        String fleetCategory = avgFleetScore.compareTo(BigDecimal.valueOf(80.00)) >= 0 ? "Excellent"
                : avgFleetScore.compareTo(BigDecimal.valueOf(60.00)) >= 0 ? "Average" : "Needs Improvement";

        // 5. SECTION B: Complete Vehicles Operated (Driver-Vehicle Historical Combination Matrix)
        Map<String, DriverVehicleSummaryDTO> dvMap = new LinkedHashMap<>();
        for (Trip t : allTrips) {
            Long drvId = t.getDriver() != null ? t.getDriver().getId() : 0L;
            String drvCode = t.getDriver() != null ? t.getDriver().getCode() : "—";
            String drvName = t.getDriver() != null ? t.getDriver().getName() : "Unassigned";
            Long vId = t.getVehicle() != null ? t.getVehicle().getId() : 0L;
            String vReg = t.getVehicle() != null ? t.getVehicle().getRegistrationNumber() : "Unknown";
            long durSec = calculateTripDurationSeconds(t);

            String key = drvId + "_" + vId;
            DriverVehicleSummaryDTO dto = dvMap.computeIfAbsent(key, k -> DriverVehicleSummaryDTO.builder()
                    .driverId(drvId)
                    .driverCode(drvCode)
                    .driverName(drvName)
                    .vehicleId(vId)
                    .vehicleNumber(vReg)
                    .trips(0L)
                    .distanceKm(BigDecimal.ZERO)
                    .durationSeconds(0L)
                    .formattedDuration("00h 00m 00s")
                    .alerts(0L)
                    .overspeedCount(0L)
                    .harshBrakingCount(0L)
                    .otherAlertCount(0L)
                    .build());

            dto.setTrips(dto.getTrips() + 1);
            if (t.getDistanceKm() != null) {
                dto.setDistanceKm(dto.getDistanceKm().add(t.getDistanceKm()).setScale(2, RoundingMode.HALF_UP));
            }
            dto.setDurationSeconds(dto.getDurationSeconds() + durSec);
            dto.setFormattedDuration(formatDuration(dto.getDurationSeconds()));
        }

        // Link alerts to the corresponding driver-vehicle pairing
        for (Alert a : allAlerts) {
            Long drvId = a.getDriver() != null ? a.getDriver().getId()
                    : (a.getTrip() != null && a.getTrip().getDriver() != null ? a.getTrip().getDriver().getId() : 0L);
            Long vId = a.getVehicle() != null ? a.getVehicle().getId() : 0L;
            String key = drvId + "_" + vId;
            DriverVehicleSummaryDTO dto = dvMap.get(key);
            if (dto != null) {
                dto.setAlerts(dto.getAlerts() + 1);
                String at = a.getAlertType() != null ? a.getAlertType().toLowerCase() : "";
                if (at.contains("overspeed")) {
                    dto.setOverspeedCount(dto.getOverspeedCount() + 1);
                } else if (at.contains("braking")) {
                    dto.setHarshBrakingCount(dto.getHarshBrakingCount() + 1);
                } else {
                    dto.setOtherAlertCount(dto.getOtherAlertCount() + 1);
                }
            }
        }
        List<DriverVehicleSummaryDTO> driverVehicleDetails = new ArrayList<>(dvMap.values());
        driverVehicleDetails.sort((a, b) -> a.getDriverName().compareToIgnoreCase(b.getDriverName()));

        // 6. SECTION C: Trip Tracking records (All 16 columns)
        List<TripTrackingDTO> fleetTripDetails = new ArrayList<>();
        for (Trip t : allTrips) {
            long durSec = calculateTripDurationSeconds(t);
            fleetTripDetails.add(TripTrackingDTO.builder()
                    .tripId(t.getId())
                    .vehicleNumber(t.getVehicle() != null ? t.getVehicle().getRegistrationNumber() : "N/A")
                    .driverName(t.getDriver() != null ? t.getDriver().getName() : "—")
                    .tripStartDate(t.getStartTime() != null ? t.getStartTime().format(DATE_FMT) : "N/A")
                    .entryStartTime(t.getStartTime() != null ? t.getStartTime().format(TIME_FMT) : "N/A")
                    .startLocation(formatLocation(t.getPrimaryRoad(), t.getStartLatitude(), t.getStartLongitude(), "GPS Start"))
                    .tripEndDate(t.getEndTime() != null ? t.getEndTime().format(DATE_FMT) : "Active")
                    .exitEndTime(t.getEndTime() != null ? t.getEndTime().format(TIME_FMT) : "Active")
                    .endLocation(formatLocation(null, t.getEndLatitude(), t.getEndLongitude(), t.getEndTime() != null ? "GPS End" : "Active / In Transit"))
                    .destination(t.getPrimaryRoad() != null && !t.getPrimaryRoad().isBlank() ? t.getPrimaryRoad() : "N/A")
                    .totalDistanceKm(t.getDistanceKm() != null ? t.getDistanceKm().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .totalTripDurationFormatted(formatDuration(durSec))
                    .durationSeconds(durSec)
                    .averageSpeedKmph(t.getAverageSpeedKmph() != null ? t.getAverageSpeedKmph().setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .maxSpeedKmph(t.getMaxSpeedKmph() != null ? t.getMaxSpeedKmph().setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .alertsDuringTrip(t.getAlertCount() != null ? t.getAlertCount() : 0)
                    .tripStatus(t.getStatus() != null ? t.getStatus() : "COMPLETED")
                    .build());
        }

        // 7. SECTION D: Alert Event Details and Grouped Summaries (All 8 columns)
        List<AlertReportItemDTO> fleetAlertEvents = new ArrayList<>();
        Map<String, AlertReportItemDTO> fleetAlertSummaryMap = new LinkedHashMap<>();

        for (Alert a : allAlerts) {
            String vNum = a.getVehicle() != null ? a.getVehicle().getRegistrationNumber() : "N/A";
            String aDate = a.getOccurredAt() != null ? a.getOccurredAt().format(DATE_FMT) : "N/A";
            String aTime = a.getOccurredAt() != null ? a.getOccurredAt().format(TIME_FMT) : "N/A";
            String aType = a.getAlertType() != null ? a.getAlertType() : "Unknown Alert";
            String sev   = a.getSeverity() != null ? a.getSeverity() : "WARNING";
            String dName = a.getDriver() != null ? a.getDriver().getName()
                    : (a.getTrip() != null && a.getTrip().getDriver() != null ? a.getTrip().getDriver().getName() : "—");
            String stat  = a.getStatus() != null ? a.getStatus() : "OPEN";

            fleetAlertEvents.add(AlertReportItemDTO.builder()
                    .alertId(a.getId())
                    .vehicleNumber(vNum)
                    .date(aDate)
                    .time(aTime)
                    .alertType(aType)
                    .severity(sev)
                    .count(1L)
                    .driverName(dName)
                    .status(stat)
                    .build());

            String groupKey = vNum + "|" + aDate + "|" + aType + "|" + sev + "|" + stat + "|" + dName;
            AlertReportItemDTO grouped = fleetAlertSummaryMap.computeIfAbsent(groupKey, k -> AlertReportItemDTO.builder()
                    .vehicleNumber(vNum)
                    .date(aDate)
                    .time(aTime)
                    .alertType(aType)
                    .severity(sev)
                    .count(0L)
                    .driverName(dName)
                    .status(stat)
                    .build());
            grouped.setCount(grouped.getCount() + 1);
        }
        List<AlertReportItemDTO> fleetAlertSummaries = new ArrayList<>(fleetAlertSummaryMap.values());

        // 8. Generate Complete Fleet PDF containing ALL FOUR SECTIONS
        String reportId = "FLTRPT_" + rType + "_" + startDate + "_" + UUID.randomUUID().toString().substring(0, 6);
        String fileName = reportId + ".pdf";
        String pdfUrl = null, downloadUrl = null;

        try {
            Files.createDirectories(Paths.get(reportsOutputDir));
            String filePath = reportsOutputDir + File.separator + fileName;

            generateFleetPdfV2(filePath, rType, startDate, endDate,
                    vehiclesEntered, vehiclesExited, totalTrips, totalDistKm, totalDurationFormatted,
                    totalAlerts, avgFleetScore, fleetCategory,
                    driverRows, driverVehicleDetails, fleetTripDetails,
                    fleetAlertSummaries, alertTypeSummary, alertSeveritySummary, alertStatusSummary);

            pdfUrl = "/api/reports/" + reportId + "/pdf";
            downloadUrl = "/api/reports/" + reportId + "/download";
        } catch (Exception e) {
            log.error("Error generating overall Fleet PDF", e);
        }

        return OverallReportDTO.builder()
                .reportType(rType)
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .totalVehiclesEntered(vehiclesEntered)
                .totalVehiclesExited(vehiclesExited)
                .totalTrips(totalTrips)
                .totalDistanceKm(BigDecimal.valueOf(totalDistKm).setScale(2, RoundingMode.HALF_UP))
                .totalDurationSeconds(totalDurationSec)
                .totalDurationFormatted(totalDurationFormatted)
                .totalAlerts(totalAlerts)
                .averageSafetyScore(avgFleetScore)
                .performanceCategory(fleetCategory)
                .alertTypeSummary(alertTypeSummary)
                .alertSeveritySummary(alertSeveritySummary)
                .alertStatusSummary(alertStatusSummary)
                .vehicleAlertSummary(vehicleAlertSummary)
                .driverAlertDetails(driverRows)
                .driverVehicleDetails(driverVehicleDetails)
                .tripDetails(fleetTripDetails)
                .alertEvents(fleetAlertEvents)
                .alertSummaries(fleetAlertSummaries)
                .reportId(reportId)
                .pdfUrl(pdfUrl)
                .downloadUrl(downloadUrl)
                .message("Fleet " + rType.toLowerCase() + " report generated successfully with all 4 sections")
                .build();
    }

    // ==================================================================
    // 4. PDF GENERATION: DRIVER REPORT (A4 Landscape, All 4 Sections)
    // ==================================================================
    private void generateDriverPdfV2(String filePath, Driver driver, LocalDate startDate, LocalDate endDate,
                                     String rType, SafetyScoreBreakdownDTO breakdown,
                                     long vehiclesEntered, long vehiclesExited, String totalDurationFormatted,
                                     List<DriverVehicleSummaryDTO> vehiclesOperated,
                                     List<TripTrackingDTO> trips,
                                     List<AlertReportItemDTO> alertSummaries,
                                     Map<String, Long> alertTypeCounts,
                                     Map<String, Long> alertSeverityCounts,
                                     Map<String, Long> alertStatusCounts) throws Exception {

        String timestampStr = LocalDateTime.now().format(GEN_FMT);

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(filePath));
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            // Register automatic running page footer with page number
            pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PageFooterHandler(timestampStr));
            doc.setMargins(20, 24, 26, 24);

            // Report Header Banner
            Table header = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            Cell titleCell = new Cell().add(new Paragraph("TRACKFLEET GPS FLEET TRACKING SYSTEM")
                    .setFontSize(8).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph("Driver Performance & Safety Report")
                    .setFontSize(17).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Driver: " + driver.getName() + " (" + driver.getCode() + ")  ·  " + rType + " Report")
                    .setFontSize(10).setFontColor(new DeviceRgb(191, 219, 254)))
                    .setBackgroundColor(HEADER_DARK).setPadding(10).setBorder(null);
            header.addCell(titleCell);

            Cell dateCell = new Cell()
                    .add(new Paragraph("REPORTING PERIOD").setFontSize(7).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph(startDate.format(DATE_FMT) + " to " + endDate.format(DATE_FMT))
                    .setFontSize(10).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Generated: " + timestampStr + " IST")
                    .setFontSize(7).setFontColor(new DeviceRgb(148, 163, 184)))
                    .setBackgroundColor(HEADER_DARK).setPadding(10).setTextAlignment(TextAlignment.RIGHT).setBorder(null);
            header.addCell(dateCell);
            doc.add(header);

            // ==============================================================
            // SECTION 1: OVERVIEW & SCORING
            // ==============================================================
            addSectionHeading(doc, "1. Overview & Scoring", "Executive performance indicators and standardized driver safety scoring");

            // KPI Metric Cards (8 cards in 2 rows of 4)
            Table kpiTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25})).useAllAvailableWidth();
            DeviceRgb scoreCol = breakdown.getFinalSafetyScore().compareTo(BigDecimal.valueOf(80)) >= 0 ? SUCCESS_GREEN
                    : breakdown.getFinalSafetyScore().compareTo(BigDecimal.valueOf(60)) >= 0 ? WARNING_AMBER : DANGER_RED;

            addMetricCard(kpiTable, "Safety Score", breakdown.getFinalSafetyScore() + " / 100", scoreCol);
            addMetricCard(kpiTable, "Performance Category", breakdown.getPerformanceCategory(), scoreCol);
            addMetricCard(kpiTable, "Total Trips Completed", String.valueOf(breakdown.getTotalTrips()), PRIMARY_BLUE);
            addMetricCard(kpiTable, "Total Distance Driven", breakdown.getTotalDistanceKm() + " km", PRIMARY_BLUE);

            addMetricCard(kpiTable, "Total Driving Duration", totalDurationFormatted, PRIMARY_BLUE);
            addMetricCard(kpiTable, "Vehicles Operated (Entered)", String.valueOf(vehiclesEntered), PRIMARY_BLUE);
            addMetricCard(kpiTable, "Vehicles Completed (Exited)", String.valueOf(vehiclesExited), SUCCESS_GREEN);
            long totalAlertCount = alertTypeCounts.values().stream().mapToLong(Long::longValue).sum();
            addMetricCard(kpiTable, "Total Safety Alerts", String.valueOf(totalAlertCount), totalAlertCount == 0 ? SUCCESS_GREEN : DANGER_RED);
            doc.add(kpiTable);

            // Safety Score Breakdown Table
            Paragraph formulaExpl = new Paragraph("Safety Score Formula: 100 - Σ(Normalized Event Rate_i × Weight_i) - Manager Penalty Deductions. Clamped between 0.00 and 100.00.")
                    .setFontSize(7).setFontColor(TEXT_MUTED).setMarginTop(6).setMarginBottom(3);
            doc.add(formulaExpl);

            Table scoreTable = new Table(UnitValue.createPercentArray(new float[]{22, 12, 28, 16, 22})).useAllAvailableWidth();
            addTableHeader(scoreTable, "Metric", "Event Count", "Normalized Event Rate", "Weight", "Weighted Penalty Contribution");
            if (breakdown.getMetrics() != null && !breakdown.getMetrics().isEmpty()) {
                int rIdx = 0;
                for (var m : breakdown.getMetrics()) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    scoreTable.addCell(dataCell(m.getMetricName(), bg, false));
                    scoreTable.addCell(dataCell(m.getCount() != null ? String.valueOf(m.getCount()) : "0", bg, true));
                    scoreTable.addCell(dataCell(m.getNormalizedRate() != null ? m.getNormalizedRate() + " " + m.getRateUnit() : "0.00", bg, false));
                    scoreTable.addCell(dataCell("× " + m.getWeight(), bg, true));
                    scoreTable.addCell(dataCell("-" + m.getPenalty() + " pts", bg, true));
                }
            }
            if (breakdown.getManualPenaltyDeductions() != null && breakdown.getManualPenaltyDeductions().compareTo(BigDecimal.ZERO) > 0) {
                scoreTable.addCell(dataCell("Manager Manual Deductions", ROW_EVEN, false));
                scoreTable.addCell(dataCell("—", ROW_EVEN, true));
                scoreTable.addCell(dataCell("Administrative Disciplinary Deductions", ROW_EVEN, false));
                scoreTable.addCell(dataCell("—", ROW_EVEN, true));
                scoreTable.addCell(dataCell("-" + breakdown.getManualPenaltyDeductions() + " pts", ROW_EVEN, true));
            }
            // Summary reconciliation row
            Cell reconCell = new Cell(1, 5)
                    .add(new Paragraph(String.format("Base Score: 100.00  |  Formula Deductions: -%.2f pts  |  Manual Deductions: -%.2f pts  |  Total Weighted Penalty: -%.2f pts  |  Final Safety Score: %.2f / 100 (%s)",
                            breakdown.getFormulaDeductions() != null ? breakdown.getFormulaDeductions().doubleValue() : 0.0,
                            breakdown.getManualPenaltyDeductions() != null ? breakdown.getManualPenaltyDeductions().doubleValue() : 0.0,
                            breakdown.getTotalWeightedPenalty() != null ? breakdown.getTotalWeightedPenalty().doubleValue() : 0.0,
                            breakdown.getFinalSafetyScore() != null ? breakdown.getFinalSafetyScore().doubleValue() : 100.0,
                            breakdown.getPerformanceCategory()))
                            .setFontSize(7.5f).setBold().setFontColor(TEXT_PRIMARY))
                    .setBackgroundColor(new DeviceRgb(241, 245, 249)).setPadding(5).setTextAlignment(TextAlignment.CENTER);
            scoreTable.addCell(reconCell);
            doc.add(scoreTable);

            // ==============================================================
            // SECTION 2: VEHICLES OPERATED
            // ==============================================================
            addSectionHeading(doc, "2. Vehicles Operated", "Every distinct vehicle operated by this driver during the reporting period with usage telemetry");

            Table vTable = new Table(UnitValue.createPercentArray(new float[]{8, 14, 12, 9, 11, 11, 9, 9, 9, 8})).useAllAvailableWidth();
            addTableHeader(vTable, "Driver ID", "Driver Name", "Vehicle Number", "Trips", "Distance (km)", "Total Duration", "Total Alerts", "Overspeed", "Harsh Brake", "Other");
            if (vehiclesOperated.isEmpty()) {
                addEmptyTableRow(vTable, 10, "No vehicle records found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (var v : vehiclesOperated) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    vTable.addCell(dataCell(v.getDriverCode() != null ? v.getDriverCode() : String.valueOf(v.getDriverId()), bg, false));
                    vTable.addCell(dataCell(v.getDriverName(), bg, false));
                    vTable.addCell(dataCell(v.getVehicleNumber(), bg, false));
                    vTable.addCell(dataCell(String.valueOf(v.getTrips()), bg, true));
                    vTable.addCell(dataCell(v.getDistanceKm() + " km", bg, true));
                    vTable.addCell(dataCell(v.getFormattedDuration(), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getAlerts()), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getOverspeedCount()), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getHarshBrakingCount()), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getOtherAlertCount()), bg, true));
                }
            }
            doc.add(vTable);

            // ==============================================================
            // SECTION 3: TRIP TRACKING
            // ==============================================================
            addSectionHeading(doc, "3. Trip Tracking", "Detailed trip execution log with GPS timestamps, verified road locations, speed telemetry, and status");

            Table tTable = new Table(UnitValue.createPercentArray(new float[]{
                    5.5f, 7.5f, 9.0f, 6.5f, 5.5f, 9.0f, 6.5f, 5.5f, 9.0f, 7.0f, 6.0f, 7.5f, 5.0f, 5.0f, 4.0f, 6.0f
            })).useAllAvailableWidth();

            addTableHeader(tTable,
                    "Trip ID", "Vehicle", "Driver Name", "Start Date", "Start Time", "Start Location",
                    "End Date", "End Time", "End Location", "Destination", "Distance", "Duration",
                    "Avg Spd", "Max Spd", "Alerts", "Status");

            if (trips.isEmpty()) {
                addEmptyTableRow(tTable, 16, "No trip records found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (TripTrackingDTO t : trips) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    tTable.addCell(dataCell("#" + t.getTripId(), bg, false));
                    tTable.addCell(dataCell(t.getVehicleNumber(), bg, false));
                    tTable.addCell(dataCell(t.getDriverName(), bg, false));
                    tTable.addCell(dataCell(t.getTripStartDate(), bg, false));
                    tTable.addCell(dataCell(t.getEntryStartTime(), bg, true));
                    tTable.addCell(dataCell(t.getStartLocation(), bg, false));
                    tTable.addCell(dataCell(t.getTripEndDate(), bg, false));
                    tTable.addCell(dataCell(t.getExitEndTime(), bg, true));
                    tTable.addCell(dataCell(t.getEndLocation(), bg, false));
                    tTable.addCell(dataCell(t.getDestination(), bg, false));
                    tTable.addCell(dataCell(t.getTotalDistanceKm() + " km", bg, true));
                    tTable.addCell(dataCell(t.getTotalTripDurationFormatted(), bg, true));
                    tTable.addCell(dataCell(t.getAverageSpeedKmph() + " km/h", bg, true));
                    tTable.addCell(dataCell(t.getMaxSpeedKmph() + " km/h", bg, true));
                    tTable.addCell(dataCell(String.valueOf(t.getAlertsDuringTrip()), bg, true));
                    tTable.addCell(dataCell(t.getTripStatus(), bg, true));
                }
            }
            doc.add(tTable);

            // ==============================================================
            // SECTION 4: ALERT SUMMARY
            // ==============================================================
            addSectionHeading(doc, "4. Alert Summary", "Aggregate safety violations by type, severity, and status, followed by event-level details");

            // Aggregate Breakdown Tables Side-by-Side (Type Breakdown + Severity/Status Breakdown)
            Table alertAggTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();

            // Left: Alert Type Counts
            Table typeTable = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            addTableHeader(typeTable, "Alert Type", "Count");
            if (alertTypeCounts.isEmpty()) {
                addEmptyTableRow(typeTable, 2, "No alert records found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (var e : alertTypeCounts.entrySet()) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    typeTable.addCell(dataCell(e.getKey(), bg, false));
                    typeTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
                }
            }
            alertAggTable.addCell(new Cell().add(typeTable).setBorder(null).setPadding(2));

            // Right: Severity & Status Breakdown
            Table sevTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            addTableHeader(sevTable, "Category / Status", "Occurrences");
            int sIdx = 0;
            for (var e : alertSeverityCounts.entrySet()) {
                DeviceRgb bg = (sIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                sevTable.addCell(dataCell("Severity: " + e.getKey(), bg, false));
                sevTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
            }
            for (var e : alertStatusCounts.entrySet()) {
                DeviceRgb bg = (sIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                sevTable.addCell(dataCell("Status: " + e.getKey(), bg, false));
                sevTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
            }
            if (alertSeverityCounts.isEmpty() && alertStatusCounts.isEmpty()) {
                addEmptyTableRow(sevTable, 2, "No alerts recorded in this period.");
            }
            alertAggTable.addCell(new Cell().add(sevTable).setBorder(null).setPadding(2));
            doc.add(alertAggTable);

            // Detailed Alert Summary Table (8 columns)
            Paragraph aTblNote = new Paragraph("Alert Summary Table (Filtered for driver " + driver.getName() + ")")
                    .setFontSize(8).setBold().setFontColor(HEADER_DARK).setMarginTop(6).setMarginBottom(3);
            doc.add(aTblNote);

            Table aTable = new Table(UnitValue.createPercentArray(new float[]{14, 10, 8, 22, 12, 8, 16, 10})).useAllAvailableWidth();
            addTableHeader(aTable, "Vehicle Number", "Date", "Time", "Alert Type", "Severity", "Count", "Driver Name", "Status");
            if (alertSummaries.isEmpty()) {
                addEmptyTableRow(aTable, 8, "No alert records found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (AlertReportItemDTO as : alertSummaries) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    aTable.addCell(dataCell(as.getVehicleNumber(), bg, false));
                    aTable.addCell(dataCell(as.getDate(), bg, false));
                    aTable.addCell(dataCell(as.getTime(), bg, true));
                    aTable.addCell(dataCell(as.getAlertType(), bg, false));
                    aTable.addCell(dataCell(as.getSeverity(), bg, true));
                    aTable.addCell(dataCell(String.valueOf(as.getCount()), bg, true));
                    aTable.addCell(dataCell(as.getDriverName(), bg, false));
                    aTable.addCell(dataCell(as.getStatus(), bg, true));
                }
            }
            doc.add(aTable);
        }
    }

    // ==================================================================
    // 5. PDF GENERATION: FLEET REPORT (A4 Landscape, All 4 Sections)
    // ==================================================================
    private void generateFleetPdfV2(String filePath, String rType, LocalDate startDate, LocalDate endDate,
                                    long entered, long exited, long totalTrips, double totalDistKm, String totalDurationFormatted,
                                    long totalAlerts, BigDecimal avgScore, String fleetCategory,
                                    List<OverallReportDTO.DriverAlertRow> driverRows,
                                    List<DriverVehicleSummaryDTO> driverVehicleDetails,
                                    List<TripTrackingDTO> trips,
                                    List<AlertReportItemDTO> alertSummaries,
                                    Map<String, Long> alertTypeSummary,
                                    Map<String, Long> alertSeveritySummary,
                                    Map<String, Long> alertStatusSummary) throws Exception {

        String timestampStr = LocalDateTime.now().format(GEN_FMT);

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(filePath));
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            // Register automatic running page footer with page number
            pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PageFooterHandler(timestampStr));
            doc.setMargins(20, 24, 26, 24);

            // Report Header Banner
            Table header = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            Cell titleCell = new Cell().add(new Paragraph("TRACKFLEET GPS FLEET TRACKING SYSTEM")
                    .setFontSize(8).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph("Fleet Performance & Safety Report")
                    .setFontSize(17).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Overall Fleet Scope  ·  " + rType + " Performance Evaluation")
                    .setFontSize(10).setFontColor(new DeviceRgb(191, 219, 254)))
                    .setBackgroundColor(HEADER_DARK).setPadding(10).setBorder(null);
            header.addCell(titleCell);

            Cell dateCell = new Cell()
                    .add(new Paragraph("REPORTING PERIOD").setFontSize(7).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph(startDate.format(DATE_FMT) + " to " + endDate.format(DATE_FMT))
                    .setFontSize(10).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Generated: " + timestampStr + " IST")
                    .setFontSize(7).setFontColor(new DeviceRgb(148, 163, 184)))
                    .setBackgroundColor(HEADER_DARK).setPadding(10).setTextAlignment(TextAlignment.RIGHT).setBorder(null);
            header.addCell(dateCell);
            doc.add(header);

            // ==============================================================
            // SECTION 1: OVERVIEW & SCORING
            // ==============================================================
            addSectionHeading(doc, "1. Overview & Scoring", "Fleet-wide key performance metrics, overall safety rating, and driver performance ranking");

            // Executive KPI Cards (7 cards)
            Table kpiTable = new Table(UnitValue.createPercentArray(new float[]{14, 14, 14, 15, 15, 14, 14})).useAllAvailableWidth();
            addMetricCard(kpiTable, "Vehicles Entered", String.valueOf(entered), PRIMARY_BLUE);
            addMetricCard(kpiTable, "Vehicles Exited", String.valueOf(exited), SUCCESS_GREEN);
            addMetricCard(kpiTable, "Total Trips", String.valueOf(totalTrips), PRIMARY_BLUE);
            addMetricCard(kpiTable, "Total Distance", String.format("%.2f km", totalDistKm), PRIMARY_BLUE);
            addMetricCard(kpiTable, "Total Duration", totalDurationFormatted, PRIMARY_BLUE);
            addMetricCard(kpiTable, "Total Alerts", String.valueOf(totalAlerts), totalAlerts == 0 ? SUCCESS_GREEN : DANGER_RED);

            DeviceRgb scoreCol = avgScore.compareTo(BigDecimal.valueOf(80)) >= 0 ? SUCCESS_GREEN
                    : avgScore.compareTo(BigDecimal.valueOf(60)) >= 0 ? WARNING_AMBER : DANGER_RED;
            addMetricCard(kpiTable, "Avg Safety Score", avgScore + " (" + fleetCategory + ")", scoreCol);
            doc.add(kpiTable);

            // Driver Performance & Vehicle-Utilization Ranking Table
            Paragraph drvRankNote = new Paragraph("Driver Performance and Vehicle-Utilization Ranking (Sorted by Safety Score)")
                    .setFontSize(8).setBold().setFontColor(HEADER_DARK).setMarginTop(6).setMarginBottom(3);
            doc.add(drvRankNote);

            Table dTable = new Table(UnitValue.createPercentArray(new float[]{9, 14, 18, 8, 11, 11, 8, 10, 11})).useAllAvailableWidth();
            addTableHeader(dTable, "Driver ID", "Driver Name", "Vehicles Operated", "Trips", "Distance (km)", "Total Duration", "Alerts", "Safety Score", "Category");
            if (driverRows.isEmpty()) {
                addEmptyTableRow(dTable, 9, "No active driver records found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (var row : driverRows) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    dTable.addCell(dataCell(row.getDriverCode() != null ? row.getDriverCode() : String.valueOf(row.getDriverId()), bg, false));
                    dTable.addCell(dataCell(row.getDriverName(), bg, false));
                    dTable.addCell(dataCell(row.getVehicles(), bg, false));
                    dTable.addCell(dataCell(String.valueOf(row.getTripCount()), bg, true));
                    dTable.addCell(dataCell(row.getTotalDistanceKm() + " km", bg, true));
                    dTable.addCell(dataCell(row.getFormattedDuration(), bg, true));
                    dTable.addCell(dataCell(String.valueOf(row.getTotalAlerts()), bg, true));
                    dTable.addCell(dataCell(row.getSafetyScore() + " / 100", bg, true));
                    dTable.addCell(dataCell(row.getPerformanceCategory(), bg, true));
                }
            }
            doc.add(dTable);

            // ==============================================================
            // SECTION 2: VEHICLES OPERATED
            // ==============================================================
            addSectionHeading(doc, "2. Vehicles Operated", "Driver-vehicle historical utilization matrix showing all distinct driver and vehicle combinations");

            Table dvTable = new Table(UnitValue.createPercentArray(new float[]{8, 14, 12, 9, 11, 11, 9, 9, 9, 8})).useAllAvailableWidth();
            addTableHeader(dvTable, "Driver ID", "Driver Name", "Vehicle Number", "Trips", "Distance (km)", "Total Duration", "Total Alerts", "Overspeed", "Harsh Brake", "Other");
            if (driverVehicleDetails.isEmpty()) {
                addEmptyTableRow(dvTable, 10, "No vehicle records found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (var dv : driverVehicleDetails) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    dvTable.addCell(dataCell(dv.getDriverCode() != null ? dv.getDriverCode() : String.valueOf(dv.getDriverId()), bg, false));
                    dvTable.addCell(dataCell(dv.getDriverName(), bg, false));
                    dvTable.addCell(dataCell(dv.getVehicleNumber(), bg, false));
                    dvTable.addCell(dataCell(String.valueOf(dv.getTrips()), bg, true));
                    dvTable.addCell(dataCell(dv.getDistanceKm() + " km", bg, true));
                    dvTable.addCell(dataCell(dv.getFormattedDuration(), bg, true));
                    dvTable.addCell(dataCell(String.valueOf(dv.getAlerts()), bg, true));
                    dvTable.addCell(dataCell(String.valueOf(dv.getOverspeedCount()), bg, true));
                    dvTable.addCell(dataCell(String.valueOf(dv.getHarshBrakingCount()), bg, true));
                    dvTable.addCell(dataCell(String.valueOf(dv.getOtherAlertCount()), bg, true));
                }
            }
            doc.add(dvTable);

            // ==============================================================
            // SECTION 3: TRIP TRACKING
            // ==============================================================
            addSectionHeading(doc, "3. Trip Tracking", "Detailed fleet trip logs with entry/exit timestamps, verified coordinates, telemetry speeds, and status");

            Table tTable = new Table(UnitValue.createPercentArray(new float[]{
                    5.5f, 7.5f, 9.0f, 6.5f, 5.5f, 9.0f, 6.5f, 5.5f, 9.0f, 7.0f, 6.0f, 7.5f, 5.0f, 5.0f, 4.0f, 6.0f
            })).useAllAvailableWidth();

            addTableHeader(tTable,
                    "Trip ID", "Vehicle", "Driver Name", "Start Date", "Start Time", "Start Location",
                    "End Date", "End Time", "End Location", "Destination", "Distance", "Duration",
                    "Avg Spd", "Max Spd", "Alerts", "Status");

            if (trips.isEmpty()) {
                addEmptyTableRow(tTable, 16, "No trip records found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (TripTrackingDTO t : trips) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    tTable.addCell(dataCell("#" + t.getTripId(), bg, false));
                    tTable.addCell(dataCell(t.getVehicleNumber(), bg, false));
                    tTable.addCell(dataCell(t.getDriverName(), bg, false));
                    tTable.addCell(dataCell(t.getTripStartDate(), bg, false));
                    tTable.addCell(dataCell(t.getEntryStartTime(), bg, true));
                    tTable.addCell(dataCell(t.getStartLocation(), bg, false));
                    tTable.addCell(dataCell(t.getTripEndDate(), bg, false));
                    tTable.addCell(dataCell(t.getExitEndTime(), bg, true));
                    tTable.addCell(dataCell(t.getEndLocation(), bg, false));
                    tTable.addCell(dataCell(t.getDestination(), bg, false));
                    tTable.addCell(dataCell(t.getTotalDistanceKm() + " km", bg, true));
                    tTable.addCell(dataCell(t.getTotalTripDurationFormatted(), bg, true));
                    tTable.addCell(dataCell(t.getAverageSpeedKmph() + " km/h", bg, true));
                    tTable.addCell(dataCell(t.getMaxSpeedKmph() + " km/h", bg, true));
                    tTable.addCell(dataCell(String.valueOf(t.getAlertsDuringTrip()), bg, true));
                    tTable.addCell(dataCell(t.getTripStatus(), bg, true));
                }
            }
            doc.add(tTable);

            // ==============================================================
            // SECTION 4: ALERT SUMMARY
            // ==============================================================
            addSectionHeading(doc, "4. Alert Summary", "Fleet alert statistics by type, severity, and resolution status, followed by full alert log");

            // Aggregate Summary Tables Side-by-Side
            Table alertAggTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();

            Table typeTable = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            addTableHeader(typeTable, "Alert Type", "Occurrences");
            if (alertTypeSummary.isEmpty()) {
                addEmptyTableRow(typeTable, 2, "No alerts found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (var e : alertTypeSummary.entrySet()) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    typeTable.addCell(dataCell(e.getKey(), bg, false));
                    typeTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
                }
            }
            alertAggTable.addCell(new Cell().add(typeTable).setBorder(null).setPadding(2));

            Table sevTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            addTableHeader(sevTable, "Severity / Status", "Occurrences");
            int sIdx = 0;
            for (var e : alertSeveritySummary.entrySet()) {
                DeviceRgb bg = (sIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                sevTable.addCell(dataCell("Severity: " + e.getKey(), bg, false));
                sevTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
            }
            for (var e : alertStatusSummary.entrySet()) {
                DeviceRgb bg = (sIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                sevTable.addCell(dataCell("Status: " + e.getKey(), bg, false));
                sevTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
            }
            if (alertSeveritySummary.isEmpty() && alertStatusSummary.isEmpty()) {
                addEmptyTableRow(sevTable, 2, "No alerts recorded in this period.");
            }
            alertAggTable.addCell(new Cell().add(sevTable).setBorder(null).setPadding(2));
            doc.add(alertAggTable);

            // Detailed Alert Summary Table (8 columns)
            Paragraph aTblNote = new Paragraph("Fleet Alert Incident Records")
                    .setFontSize(8).setBold().setFontColor(HEADER_DARK).setMarginTop(6).setMarginBottom(3);
            doc.add(aTblNote);

            Table aTable = new Table(UnitValue.createPercentArray(new float[]{14, 10, 8, 22, 12, 8, 16, 10})).useAllAvailableWidth();
            addTableHeader(aTable, "Vehicle Number", "Date", "Time", "Alert Type", "Severity", "Count", "Driver Name", "Status");
            if (alertSummaries.isEmpty()) {
                addEmptyTableRow(aTable, 8, "No alert records found for the selected reporting period.");
            } else {
                int rIdx = 0;
                for (AlertReportItemDTO as : alertSummaries) {
                    DeviceRgb bg = (rIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    aTable.addCell(dataCell(as.getVehicleNumber(), bg, false));
                    aTable.addCell(dataCell(as.getDate(), bg, false));
                    aTable.addCell(dataCell(as.getTime(), bg, true));
                    aTable.addCell(dataCell(as.getAlertType(), bg, false));
                    aTable.addCell(dataCell(as.getSeverity(), bg, true));
                    aTable.addCell(dataCell(String.valueOf(as.getCount()), bg, true));
                    aTable.addCell(dataCell(as.getDriverName(), bg, false));
                    aTable.addCell(dataCell(as.getStatus(), bg, true));
                }
            }
            doc.add(aTable);
        }
    }

    // ==================================================================
    // 6. HELPER METHODS: DURATION, DATES, LOCATION & FORMATTING
    // ==================================================================

    /**
     * Converts seconds into strict "HHh MMm SSs" format.
     * Examples: 00h 45m 12s, 02h 15m 30s.
     */
    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "00h 00m 00s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02dh %02dm %02ds", hours, minutes, secs);
    }

    private long calculateTripDurationSeconds(Trip t) {
        if (t.getDurationSeconds() != null && t.getDurationSeconds() > 0) {
            return t.getDurationSeconds().longValue();
        }
        if (t.getStartTime() != null && t.getEndTime() != null) {
            return Duration.between(t.getStartTime(), t.getEndTime()).getSeconds();
        }
        return 0L;
    }

    private String formatLocation(String road, BigDecimal lat, BigDecimal lon, String fallback) {
        if (road != null && !road.isBlank() && !"N/A".equalsIgnoreCase(road.trim())) {
            return road.trim();
        }
        if (lat != null && lon != null) {
            return String.format("%.4f, %.4f", lat.doubleValue(), lon.doubleValue());
        }
        return fallback;
    }

    /**
     * Resolves start date based on request parameters using Asia/Kolkata timezone.
     */
    private LocalDate resolveStartDate(ReportRequestDTO req) {
        if (req.getStartDate() != null) return req.getStartDate();

        // 1. ISO Week format: e.g. "2026-W38"
        if (req.getWeek() != null && req.getWeek().contains("-W")) {
            try {
                String[] parts = req.getWeek().split("-W");
                int year = Integer.parseInt(parts[0]);
                int week = Integer.parseInt(parts[1]);
                return LocalDate.of(year, 1, 4)
                        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                        .with(java.time.DayOfWeek.MONDAY);
            } catch (Exception e) {
                log.warn("Could not parse week {}: {}", req.getWeek(), e.getMessage());
            }
        }

        // 2. year + month2 (integer 1-12)
        if (req.getYear() != null && req.getMonth2() != null) {
            return LocalDate.of(req.getYear(), req.getMonth2(), 1);
        }

        // 3. year + numeric month string
        if (req.getYear() != null && req.getMonth() != null && !req.getMonth().contains("-")) {
            try {
                int m = Integer.parseInt(req.getMonth().trim());
                return LocalDate.of(req.getYear(), m, 1);
            } catch (NumberFormatException ignored) {}
        }

        // 4. month string "YYYY-MM"
        if (req.getMonth() != null && req.getMonth().contains("-")) {
            try {
                String[] parts = req.getMonth().split("-");
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
            } catch (Exception ignored) {}
        }

        // 5. date string "YYYY-MM-DD" (DAILY)
        if (req.getDate() != null && !req.getDate().isBlank()) {
            try { return LocalDate.parse(req.getDate().trim()); } catch (Exception ignored) {}
        }

        // Default: first day of last month
        return LocalDate.now().minusMonths(1).withDayOfMonth(1);
    }

    /**
     * Resolves end date ensuring exact period coverage.
     */
    private LocalDate resolveEndDate(ReportRequestDTO req, LocalDate startDate) {
        if (req.getEndDate() != null) return req.getEndDate();

        // If week was selected: end date is Sunday of that week (startDate + 6 days)
        if (req.getWeek() != null && req.getWeek().contains("-W")) {
            return startDate.plusDays(6);
        }

        String rtype = req.getReportType() != null ? req.getReportType().toUpperCase() : "MONTHLY";
        return switch (rtype) {
            case "DAILY"   -> startDate;
            case "WEEKLY"  -> startDate.plusDays(6);
            case "MONTHLY", "FLEET" -> startDate.withDayOfMonth(startDate.lengthOfMonth());
            default -> (req.getMonth() != null) ? startDate.withDayOfMonth(startDate.lengthOfMonth()) : LocalDate.now();
        };
    }

    private void addSectionHeading(Document doc, String title, String subtitle) {
        Table secTable = new Table(UnitValue.createPercentArray(new float[]{100})).useAllAvailableWidth();
        secTable.setMarginTop(8).setMarginBottom(3);
        Cell cell = new Cell()
                .add(new Paragraph(title).setFontSize(10.5f).setBold().setFontColor(PRIMARY_BLUE).setMarginBottom(1))
                .add(new Paragraph(subtitle).setFontSize(6.5f).setFontColor(TEXT_MUTED))
                .setBorder(null)
                .setBorderBottom(new SolidBorder(PRIMARY_BLUE, 1.2f))
                .setPaddingBottom(3)
                .setPaddingLeft(0);
        secTable.addCell(cell);
        doc.add(secTable);
    }

    private void addMetricCard(Table table, String label, String value, DeviceRgb color) {
        Cell cell = new Cell()
                .add(new Paragraph(label).setFontSize(6.5f).setFontColor(TEXT_MUTED))
                .add(new Paragraph(value).setFontSize(10.5f).setBold().setFontColor(color))
                .setBackgroundColor(ROW_EVEN)
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(4)
                .setTextAlignment(TextAlignment.CENTER);
        table.addCell(cell);
    }

    private void addTableHeader(Table table, String... headers) {
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFontSize(6.5f).setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(TABLE_HEADER)
                    .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                    .setPadding(3)
                    .setTextAlignment(TextAlignment.CENTER));
        }
    }

    private Cell dataCell(String text, DeviceRgb bgColor, boolean alignRight) {
        return new Cell()
                .add(new Paragraph(text != null && !text.isBlank() ? text : "—").setFontSize(6.5f).setFontColor(TEXT_PRIMARY))
                .setBackgroundColor(bgColor)
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(2.5f)
                .setTextAlignment(alignRight ? TextAlignment.RIGHT : TextAlignment.LEFT);
    }

    private void addEmptyTableRow(Table table, int colspan, String message) {
        table.addCell(new Cell(1, colspan)
                .add(new Paragraph(message).setFontSize(7).setFontColor(TEXT_MUTED).setItalic())
                .setBackgroundColor(ROW_EVEN)
                .setBorder(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(8)
                .setTextAlignment(TextAlignment.CENTER));
    }

    public String getReportFilePath(String reportId) {
        return reportsOutputDir + File.separator + reportId + ".pdf";
    }

    // ==================================================================
    // 7. RUNNING PAGE FOOTER EVENT HANDLER
    // ==================================================================
    private static class PageFooterHandler implements IEventHandler {
        private final String generatedTimestamp;

        public PageFooterHandler(String generatedTimestamp) {
            this.generatedTimestamp = generatedTimestamp;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            int pageNumber = pdfDoc.getPageNumber(page);
            Rectangle pageSize = page.getPageSize();

            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdfDoc);
            Canvas canvas = new Canvas(pdfCanvas, pageSize);

            String footerText = String.format("TrackFleet GPS Fleet Tracking System  ·  Page %d  ·  Generated: %s IST  ·  Confidential Enterprise Report",
                    pageNumber, generatedTimestamp);

            canvas.showTextAligned(
                    new Paragraph(footerText).setFontSize(6.5f).setFontColor(TEXT_MUTED),
                    pageSize.getWidth() / 2, 12, TextAlignment.CENTER
            );
            canvas.close();
        }
    }
}
