package com.gps.tracking.service;

import com.gps.tracking.dto.*;
import com.gps.tracking.entity.*;
import com.gps.tracking.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private final DriverPenaltyRepository penaltyRepository;

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

        List<DriverPenalty> driverPenalties = penaltyRepository.findByDriverIdAndDateAppliedBetweenOrderByDateAppliedDesc(driver.getId(), from, to);
        long driverWarnings = driverPenalties.stream().filter(p -> "WARNING".equalsIgnoreCase(p.getClassification()) && !"REVOKED".equalsIgnoreCase(p.getStatus())).count();
        long driverPenaltyBearing = driverPenalties.stream().filter(p -> "PENALTY".equalsIgnoreCase(p.getClassification()) && "ACTIVE".equalsIgnoreCase(p.getStatus())).count();
        BigDecimal initialCum = penaltyRepository.sumActiveMonetaryAmountByDriverId(driver.getId());
        final BigDecimal driverCumPenalties = initialCum != null ? initialCum : BigDecimal.ZERO;

        Map<Long, DriverPenalty> alertPenaltyMap = new HashMap<>();
        for (Alert a : alerts) {
            if (a.getId() != null) {
                List<DriverPenalty> pList = penaltyRepository.findByAlertId(a.getId());
                if (!pList.isEmpty()) {
                    alertPenaltyMap.put(a.getId(), pList.get(0));
                }
            }
        }

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

            final DriverPenalty p = alertPenaltyMap.get(a.getId());

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
                    .occurrenceNumber(p != null ? p.getOccurrenceNumber() : null)
                    .classification(p != null ? p.getClassification() : null)
                    .penaltyAmount(p != null ? p.getMonetaryAmount() : null)
                    .cumulativePenalties(driverCumPenalties)
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
                    .occurrenceNumber(p != null ? p.getOccurrenceNumber() : null)
                    .classification(p != null ? p.getClassification() : null)
                    .penaltyAmount(p != null ? p.getMonetaryAmount() : null)
                    .cumulativePenalties(driverCumPenalties)
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

            List<DriverPenalty> dPenalties = penaltyRepository.findByDriverIdAndDateAppliedBetweenOrderByDateAppliedDesc(d.getId(), from, to);
            long dWarnings = dPenalties.stream().filter(p -> "WARNING".equalsIgnoreCase(p.getClassification()) && !"REVOKED".equalsIgnoreCase(p.getStatus())).count();
            long dPenaltyBearing = dPenalties.stream().filter(p -> "PENALTY".equalsIgnoreCase(p.getClassification()) && "ACTIVE".equalsIgnoreCase(p.getStatus())).count();
            BigDecimal dTotalPenalties = penaltyRepository.sumActiveMonetaryAmountByDriverId(d.getId());
            if (dTotalPenalties == null) dTotalPenalties = BigDecimal.ZERO;

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
                    .warningCount(dWarnings)
                    .penaltyBearingCount(dPenaltyBearing)
                    .totalPenalties(dTotalPenalties)
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

        Map<Long, DriverPenalty> fleetPenaltyMap = new HashMap<>();
        for (Alert a : allAlerts) {
            if (a.getId() != null) {
                List<DriverPenalty> pList = penaltyRepository.findByAlertId(a.getId());
                if (!pList.isEmpty()) {
                    fleetPenaltyMap.put(a.getId(), pList.get(0));
                }
            }
        }

        for (Alert a : allAlerts) {
            String vNum = a.getVehicle() != null ? a.getVehicle().getRegistrationNumber() : "N/A";
            String aDate = a.getOccurredAt() != null ? a.getOccurredAt().format(DATE_FMT) : "N/A";
            String aTime = a.getOccurredAt() != null ? a.getOccurredAt().format(TIME_FMT) : "N/A";
            String aType = a.getAlertType() != null ? a.getAlertType() : "Unknown Alert";
            String sev   = a.getSeverity() != null ? a.getSeverity() : "WARNING";
            String dName = a.getDriver() != null ? a.getDriver().getName()
                    : (a.getTrip() != null && a.getTrip().getDriver() != null ? a.getTrip().getDriver().getName() : "—");
            String stat  = a.getStatus() != null ? a.getStatus() : "OPEN";

            final DriverPenalty p = fleetPenaltyMap.get(a.getId());

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
                    .occurrenceNumber(p != null ? p.getOccurrenceNumber() : null)
                    .classification(p != null ? p.getClassification() : null)
                    .penaltyAmount(p != null ? p.getMonetaryAmount() : null)
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
                    .occurrenceNumber(p != null ? p.getOccurrenceNumber() : null)
                    .classification(p != null ? p.getClassification() : null)
                    .penaltyAmount(p != null ? p.getMonetaryAmount() : null)
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

        long fleetWarnings = penaltyRepository.countByClassificationAndStatus("WARNING", "ACTIVE")
                + penaltyRepository.countByClassificationAndStatus("WARNING", "COMPLETED");
        long fleetPenaltyBearing = penaltyRepository.countByClassificationAndStatus("PENALTY", "ACTIVE");
        BigDecimal totalFleetPenalties = penaltyRepository.sumTotalActiveMonetaryAmount();
        if (totalFleetPenalties == null) totalFleetPenalties = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> penaltyBreakdown = new LinkedHashMap<>();
        for (Object[] row : penaltyRepository.sumTotalMonetaryGroupedByAlertType()) {
            String at = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            if (at != null) penaltyBreakdown.put(at, amt != null ? amt : BigDecimal.ZERO);
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
                .totalWarnings(fleetWarnings)
                .totalPenaltyBearingAlerts(fleetPenaltyBearing)
                .totalFleetPenalties(totalFleetPenalties)
                .penaltyBreakdownByAlertType(penaltyBreakdown)
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

        // 0. Preset handling
        if (req.getDatePreset() != null && !req.getDatePreset().isBlank()) {
            LocalDate today = LocalDate.now();
            switch (req.getDatePreset().toUpperCase().trim()) {
                case "TODAY": return today;
                case "YESTERDAY": return today.minusDays(1);
                case "THIS_WEEK": return today.with(java.time.DayOfWeek.MONDAY);
                case "LAST_WEEK": return today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY);
                case "THIS_MONTH": return today.withDayOfMonth(1);
                case "LAST_MONTH": return today.minusMonths(1).withDayOfMonth(1);
            }
        }

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

        // 0. Preset handling
        if (req.getDatePreset() != null && !req.getDatePreset().isBlank()) {
            LocalDate today = LocalDate.now();
            switch (req.getDatePreset().toUpperCase().trim()) {
                case "TODAY": return today;
                case "YESTERDAY": return today.minusDays(1);
                case "THIS_WEEK": return today;
                case "LAST_WEEK": return today.minusWeeks(1).with(java.time.DayOfWeek.SUNDAY);
                case "THIS_MONTH": return today;
                case "LAST_MONTH": return today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
            }
        }

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
    // 8. ALERT-BASED DRIVER REPORT GENERATION
    // ==================================================================
    public ReportResponseDTO generateDriverAlertReport(ReportRequestDTO req) {
        final Driver driver;
        if (req.getDriverId() != null) {
            driver = driverRepository.findById(req.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", req.getDriverId()));
        } else if (req.getDriverCode() != null && !req.getDriverCode().isBlank()) {
            driver = driverRepository.findByCode(req.getDriverCode().trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "code", req.getDriverCode()));
        } else {
            throw new IllegalArgumentException("Driver ID or Code is required for Driver Alert Report");
        }

        LocalDate startDate = resolveStartDate(req);
        LocalDate endDate = resolveEndDate(req, startDate);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        Long vehicleId = req.getVehicleId();
        List<String> alertTypes = (req.getAlertTypes() != null && !req.getAlertTypes().isEmpty()) ? req.getAlertTypes() : null;
        String severity = (req.getSeverity() != null && !req.getSeverity().isBlank() && !"ALL".equalsIgnoreCase(req.getSeverity())) ? req.getSeverity().trim() : null;
        String status = (req.getStatus() != null && !req.getStatus().isBlank() && !"ALL".equalsIgnoreCase(req.getStatus())) ? req.getStatus().trim() : null;

        // Query matching alerts using JPA Specification
        Specification<Alert> spec = AlertSpecifications.filterForReport(from, to, driver.getId(), vehicleId, alertTypes, severity, status);
        List<Alert> matchingAlerts = alertRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "occurredAt"));

        // Retrieve general trips and alerts for driver during period (for vehicles operated & safety score)
        List<Trip> driverTrips = tripRepository.findTripsByDriverAndDateRange(driver.getId(), from, to);
        List<Alert> allDriverAlerts = alertRepository.findAlertsByDriverAndDateRange(driver.getId(), from, to);
        List<DriverDailyStats> statsList = statsRepository.findByDriverIdAndStatDateBetweenOrderByStatDateAsc(driver.getId(), startDate, endDate);

        // Vehicles operated by driver in this period
        Map<String, DriverVehicleSummaryDTO> vehicleSummaryMap = new LinkedHashMap<>();
        for (Trip t : driverTrips) {
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

        for (Alert a : allDriverAlerts) {
            String vNum = a.getVehicle() != null ? a.getVehicle().getRegistrationNumber() : "Unknown";
            DriverVehicleSummaryDTO summary = vehicleSummaryMap.get(vNum);
            if (summary != null) {
                summary.setAlerts(summary.getAlerts() + 1);
                String at = a.getAlertType() != null ? a.getAlertType().toLowerCase() : "";
                if (at.contains("overspeed")) summary.setOverspeedCount(summary.getOverspeedCount() + 1);
                else if (at.contains("braking")) summary.setHarshBrakingCount(summary.getHarshBrakingCount() + 1);
                else summary.setOtherAlertCount(summary.getOtherAlertCount() + 1);
            }
        }
        List<DriverVehicleSummaryDTO> vehiclesOperated = new ArrayList<>(vehicleSummaryMap.values());

        // Safety Score
        int totalTrips = driverTrips.size();
        double totalDistKm = driverTrips.stream()
                .mapToDouble(t -> t.getDistanceKm() != null ? t.getDistanceKm().doubleValue() : 0.0).sum();
        long overspeed = allDriverAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("overspeed")).count();
        long harshBraking = allDriverAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("braking")).count();
        long harshAccel = allDriverAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("accel")).count();
        long gpsDisc = allDriverAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("gps")).count();
        long nightDriving = allDriverAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("night")).count();
        long fatigue = allDriverAlerts.stream().filter(a -> a.getAlertType().toLowerCase().contains("fatigue")).count();
        int nightSec = statsList.stream().mapToInt(s -> s.getNightDrivingSeconds() != null ? s.getNightDrivingSeconds() : 0).sum();
        double nightHours = nightSec > 0 ? nightSec / 3600.0 : (nightDriving * 1.5);

        SafetyScoreBreakdownDTO breakdown = safetyScoreService.calculateDriverScore(
                driver, totalTrips, totalDistKm,
                overspeed, harshBraking, harshAccel, gpsDisc, nightDriving, nightHours, fatigue,
                from, to);

        // Penalties for driver in this period
        List<DriverPenalty> penaltyEntities = penaltyRepository
                .findByDriverIdAndDateAppliedBetweenOrderByDateAppliedDesc(driver.getId(), from, to);
        List<PenaltyDTO> penalties = penaltyEntities.stream().map(p -> PenaltyDTO.builder()
                .id(p.getId())
                .driverId(driver.getId())
                .driverCode(driver.getCode())
                .driverName(driver.getName())
                .penaltyType(p.getPenaltyType())
                .reason(p.getReason())
                .penaltyPoints(p.getPenaltyPoints())
                .monetaryAmount(p.getMonetaryAmount())
                .status(p.getStatus())
                .dateApplied(p.getDateApplied())
                .appliedBy(p.getAppliedBy())
                .build()).collect(Collectors.toList());

        // Group matching alerts
        Map<String, Long> alertTypeBreakdown = new LinkedHashMap<>();
        Map<String, Long> alertSeverityBreakdown = new LinkedHashMap<>();
        Map<String, Long> alertStatusBreakdown = new LinkedHashMap<>();
        Map<String, Long> dailyTrendsMap = new LinkedHashMap<>();

        List<AlertBasedReportDTO.AlertDetailRowDTO> detailedAlerts = new ArrayList<>();
        int srNo = 1;

        for (Alert a : matchingAlerts) {
            String aType = a.getAlertType() != null ? a.getAlertType() : "Unknown";
            String aSev = a.getSeverity() != null ? a.getSeverity().toUpperCase() : "WARNING";
            String aStat = a.getStatus() != null ? a.getStatus().toUpperCase() : "OPEN";
            String aDate = a.getOccurredAt() != null ? a.getOccurredAt().format(DATE_FMT) : "N/A";
            String aTime = a.getOccurredAt() != null ? a.getOccurredAt().format(TIME_FMT) : "N/A";
            String vNum = a.getVehicle() != null ? a.getVehicle().getRegistrationNumber() : "N/A";
            String vCode = a.getVehicle() != null ? a.getVehicle().getCode() : "N/A";
            String loc = formatLocation(a.getRoadName(), a.getLatitude(), a.getLongitude(), "GPS Coordinate");

            alertTypeBreakdown.put(aType, alertTypeBreakdown.getOrDefault(aType, 0L) + 1);
            alertSeverityBreakdown.put(aSev, alertSeverityBreakdown.getOrDefault(aSev, 0L) + 1);
            alertStatusBreakdown.put(aStat, alertStatusBreakdown.getOrDefault(aStat, 0L) + 1);
            dailyTrendsMap.put(aDate, dailyTrendsMap.getOrDefault(aDate, 0L) + 1);

            detailedAlerts.add(AlertBasedReportDTO.AlertDetailRowDTO.builder()
                    .srNo(srNo++)
                    .alertId(a.getId())
                    .date(aDate)
                    .time(aTime)
                    .vehicleNumber(vNum)
                    .vehicleCode(vCode)
                    .driverName(driver.getName())
                    .driverCode(driver.getCode())
                    .driverId(driver.getId())
                    .alertType(aType)
                    .severity(aSev)
                    .location(loc)
                    .status(aStat)
                    .speedKmph(a.getSpeedKmph() != null ? a.getSpeedKmph().doubleValue() : null)
                    .speedLimitKmph(a.getSpeedLimitKmph() != null ? a.getSpeedLimitKmph().doubleValue() : null)
                    .build());
        }

        List<AlertBasedReportDTO.DailyAlertTrendDTO> dailyTrends = new ArrayList<>();
        for (Map.Entry<String, Long> entry : dailyTrendsMap.entrySet()) {
            dailyTrends.add(AlertBasedReportDTO.DailyAlertTrendDTO.builder()
                    .date(entry.getKey())
                    .count(entry.getValue())
                    .build());
        }

        // Recommendations
        List<String> recommendations = new ArrayList<>();
        long mOverspeed = alertTypeBreakdown.getOrDefault("Overspeed Alert", 0L);
        long mHarshBrake = alertTypeBreakdown.getOrDefault("Harsh Braking Alert", 0L);
        long mGpsDisc = alertTypeBreakdown.getOrDefault("GPS Disconnect Alert", 0L);
        long mNight = alertTypeBreakdown.getOrDefault("Night Driving Alert", 0L);

        if (mOverspeed > 3) recommendations.add("⚠️ High frequency of overspeed violations (" + mOverspeed + " events). Mandatory speed-governor calibration and defensive driving retraining recommended.");
        if (mHarshBrake > 5) recommendations.add("⚠️ Multiple harsh braking events (" + mHarshBrake + " events) detected. Indicates unsafe following distance or delayed hazard recognition.");
        if (mGpsDisc > 0) recommendations.add("⚠️ GPS disconnect events (" + mGpsDisc + " events) observed. Inspect vehicle tracker wiring and OBD-II port integrity.");
        if (mNight > 0) recommendations.add("⚠️ Night driving detected (" + mNight + " events). Monitor driver fatigue and ensure mandatory rest intervals.");
        if (!penalties.isEmpty()) recommendations.add("⚠️ Driver has " + penalties.size() + " active penalty record(s) during this period. Review with operations manager.");
        if (recommendations.isEmpty()) recommendations.add("✅ Excellent safety compliance maintained during this period with zero critical alert patterns.");

        String vehicleFilterStr = null;
        if (vehicleId != null) {
            vehicleFilterStr = vehicleRepository.findById(vehicleId)
                    .map(v -> v.getRegistrationNumber() + " (" + v.getCode() + ")")
                    .orElse("Vehicle #" + vehicleId);
        }

        AlertBasedReportDTO reportData = AlertBasedReportDTO.builder()
                .reportScope("DRIVER")
                .reportType("ALERT_BASED")
                .title("Driver Alert & Safety Analysis Report")
                .reportingPeriod(startDate.format(DATE_FMT) + " to " + endDate.format(DATE_FMT))
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .selectedAlertTypes(alertTypes != null ? alertTypes : List.of("All Supported Alert Types"))
                .severityFilter(severity != null ? severity : "All")
                .statusFilter(status != null ? status : "All")
                .vehicleIdFilter(vehicleId)
                .vehicleNumberFilter(vehicleFilterStr)
                .driverId(driver.getId())
                .driverCode(driver.getCode())
                .driverName(driver.getName())
                .driverPhone(driver.getPhone())
                .vehiclesOperated(vehiclesOperated)
                .safetyScore(breakdown)
                .penalties(penalties)
                .recommendations(recommendations)
                .totalAlerts((long) matchingAlerts.size())
                .alertTypeBreakdown(alertTypeBreakdown)
                .alertSeverityBreakdown(alertSeverityBreakdown)
                .alertStatusBreakdown(alertStatusBreakdown)
                .dailyTrends(dailyTrends)
                .detailedAlerts(detailedAlerts)
                .build();

        // PDF Generation
        String reportId = "DRVALRTRPT_" + driver.getCode() + "_" + startDate + "_" + UUID.randomUUID().toString().substring(0, 6);
        String fileName = reportId + ".pdf";
        String pdfUrl = null, downloadUrl = null;

        try {
            Files.createDirectories(Paths.get(reportsOutputDir));
            String filePath = reportsOutputDir + File.separator + fileName;
            generateDriverAlertPdf(filePath, driver, startDate, endDate, reportData);
            pdfUrl = "/api/reports/" + reportId + "/pdf";
            downloadUrl = "/api/reports/" + reportId + "/download";
        } catch (Exception e) {
            log.error("Failed to generate Driver Alert PDF: {}", e.getMessage(), e);
        }

        ReportResponseDTO res = new ReportResponseDTO();
        res.setReportId(reportId);
        res.setReportType("ALERT_BASED");
        res.setStatus(pdfUrl != null ? "READY" : "FAILED");
        res.setPdfUrl(pdfUrl);
        res.setDownloadUrl(downloadUrl);
        res.setGeneratedAt(LocalDateTime.now());
        res.setMessage("Alert-based driver report successfully generated.");
        res.setReportData(reportData);
        return res;
    }

    // ==================================================================
    // 9. ALERT-BASED FLEET REPORT GENERATION
    // ==================================================================
    public ReportResponseDTO generateFleetAlertReport(ReportRequestDTO req) {
        LocalDate startDate = resolveStartDate(req);
        LocalDate endDate = resolveEndDate(req, startDate);
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        Long vehicleId = req.getVehicleId();
        List<String> alertTypes = (req.getAlertTypes() != null && !req.getAlertTypes().isEmpty()) ? req.getAlertTypes() : null;
        String severity = (req.getSeverity() != null && !req.getSeverity().isBlank() && !"ALL".equalsIgnoreCase(req.getSeverity())) ? req.getSeverity().trim() : null;
        String status = (req.getStatus() != null && !req.getStatus().isBlank() && !"ALL".equalsIgnoreCase(req.getStatus())) ? req.getStatus().trim() : null;

        // Query matching alerts using JPA Specification
        Specification<Alert> spec = AlertSpecifications.filterForReport(from, to, null, vehicleId, alertTypes, severity, status);
        List<Alert> matchingAlerts = alertRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "occurredAt"));

        // Query all trips in range to compute vehicles operated per driver accurately
        List<Trip> allTripsInRange = tripRepository.findByStartTimeBetween(from, to);

        // Group matching alerts
        Map<String, Long> alertTypeBreakdown = new LinkedHashMap<>();
        Map<String, Long> alertSeverityBreakdown = new LinkedHashMap<>();
        Map<String, Long> alertStatusBreakdown = new LinkedHashMap<>();
        Map<String, Long> dailyTrendsMap = new LinkedHashMap<>();

        Set<Long> coveredVehicles = new HashSet<>();
        Set<Long> coveredDrivers = new HashSet<>();

        Map<Long, AlertBasedReportDTO.VehicleAlertSummaryDTO> vehicleSummaryMap = new LinkedHashMap<>();
        Map<Long, AlertBasedReportDTO.DriverAlertSummaryDTO> driverSummaryMap = new LinkedHashMap<>();
        Map<Long, Set<String>> driverVehiclesOperatedMap = new HashMap<>();

        // Populate driver vehicles operated from all trips in range
        for (Trip t : allTripsInRange) {
            if (t.getDriver() != null && t.getVehicle() != null) {
                driverVehiclesOperatedMap.computeIfAbsent(t.getDriver().getId(), k -> new LinkedHashSet<>())
                        .add(t.getVehicle().getRegistrationNumber());
            }
        }

        List<AlertBasedReportDTO.AlertDetailRowDTO> detailedAlerts = new ArrayList<>();
        int srNo = 1;

        for (Alert a : matchingAlerts) {
            String aType = a.getAlertType() != null ? a.getAlertType() : "Unknown";
            String aSev = a.getSeverity() != null ? a.getSeverity().toUpperCase() : "WARNING";
            String aStat = a.getStatus() != null ? a.getStatus().toUpperCase() : "OPEN";
            String aDate = a.getOccurredAt() != null ? a.getOccurredAt().format(DATE_FMT) : "N/A";
            String aTime = a.getOccurredAt() != null ? a.getOccurredAt().format(TIME_FMT) : "N/A";

            String vNum = a.getVehicle() != null ? a.getVehicle().getRegistrationNumber() : "N/A";
            String vCode = a.getVehicle() != null ? a.getVehicle().getCode() : "N/A";
            Long vId = a.getVehicle() != null ? a.getVehicle().getId() : null;

            Driver d = a.getDriver() != null ? a.getDriver() : (a.getTrip() != null ? a.getTrip().getDriver() : null);
            String dName = d != null ? d.getName() : "—";
            String dCode = d != null ? d.getCode() : "—";
            Long dId = d != null ? d.getId() : null;

            String loc = formatLocation(a.getRoadName(), a.getLatitude(), a.getLongitude(), "GPS Coordinate");

            alertTypeBreakdown.put(aType, alertTypeBreakdown.getOrDefault(aType, 0L) + 1);
            alertSeverityBreakdown.put(aSev, alertSeverityBreakdown.getOrDefault(aSev, 0L) + 1);
            alertStatusBreakdown.put(aStat, alertStatusBreakdown.getOrDefault(aStat, 0L) + 1);
            dailyTrendsMap.put(aDate, dailyTrendsMap.getOrDefault(aDate, 0L) + 1);

            if (vId != null) coveredVehicles.add(vId);
            if (dId != null) {
                coveredDrivers.add(dId);
                if (vNum != null && !"N/A".equals(vNum)) {
                    driverVehiclesOperatedMap.computeIfAbsent(dId, k -> new LinkedHashSet<>()).add(vNum);
                }
            }

            // Vehicle summary breakdown
            if (vId != null) {
                AlertBasedReportDTO.VehicleAlertSummaryDTO vSum = vehicleSummaryMap.computeIfAbsent(vId, k -> AlertBasedReportDTO.VehicleAlertSummaryDTO.builder()
                        .vehicleId(vId)
                        .vehicleNumber(vNum)
                        .vehicleCode(vCode)
                        .totalAlerts(0L)
                        .overspeedCount(0L)
                        .harshBrakingCount(0L)
                        .harshAccelCount(0L)
                        .gpsDisconnectCount(0L)
                        .nightDrivingCount(0L)
                        .ignitionCount(0L)
                        .otherCount(0L)
                        .build());
                vSum.setTotalAlerts(vSum.getTotalAlerts() + 1);
                String lower = aType.toLowerCase();
                if (lower.contains("overspeed")) vSum.setOverspeedCount(vSum.getOverspeedCount() + 1);
                else if (lower.contains("braking")) vSum.setHarshBrakingCount(vSum.getHarshBrakingCount() + 1);
                else if (lower.contains("accel")) vSum.setHarshAccelCount(vSum.getHarshAccelCount() + 1);
                else if (lower.contains("gps")) vSum.setGpsDisconnectCount(vSum.getGpsDisconnectCount() + 1);
                else if (lower.contains("night")) vSum.setNightDrivingCount(vSum.getNightDrivingCount() + 1);
                else if (lower.contains("ignition")) vSum.setIgnitionCount(vSum.getIgnitionCount() + 1);
                else vSum.setOtherCount(vSum.getOtherCount() + 1);
            }

            // Driver summary breakdown
            if (dId != null) {
                AlertBasedReportDTO.DriverAlertSummaryDTO dSum = driverSummaryMap.computeIfAbsent(dId, k -> AlertBasedReportDTO.DriverAlertSummaryDTO.builder()
                        .driverId(dId)
                        .driverCode(dCode)
                        .driverName(dName)
                        .vehiclesOperated("")
                        .totalAlerts(0L)
                        .overspeedCount(0L)
                        .harshBrakingCount(0L)
                        .harshAccelCount(0L)
                        .gpsDisconnectCount(0L)
                        .nightDrivingCount(0L)
                        .ignitionCount(0L)
                        .otherCount(0L)
                        .build());
                dSum.setTotalAlerts(dSum.getTotalAlerts() + 1);
                String lower = aType.toLowerCase();
                if (lower.contains("overspeed")) dSum.setOverspeedCount(dSum.getOverspeedCount() + 1);
                else if (lower.contains("braking")) dSum.setHarshBrakingCount(dSum.getHarshBrakingCount() + 1);
                else if (lower.contains("accel")) dSum.setHarshAccelCount(dSum.getHarshAccelCount() + 1);
                else if (lower.contains("gps")) dSum.setGpsDisconnectCount(dSum.getGpsDisconnectCount() + 1);
                else if (lower.contains("night")) dSum.setNightDrivingCount(dSum.getNightDrivingCount() + 1);
                else if (lower.contains("ignition")) dSum.setIgnitionCount(dSum.getIgnitionCount() + 1);
                else dSum.setOtherCount(dSum.getOtherCount() + 1);
            }

            detailedAlerts.add(AlertBasedReportDTO.AlertDetailRowDTO.builder()
                    .srNo(srNo++)
                    .alertId(a.getId())
                    .date(aDate)
                    .time(aTime)
                    .vehicleNumber(vNum)
                    .vehicleCode(vCode)
                    .driverName(dName)
                    .driverCode(dCode)
                    .driverId(dId)
                    .alertType(aType)
                    .severity(aSev)
                    .location(loc)
                    .status(aStat)
                    .speedKmph(a.getSpeedKmph() != null ? a.getSpeedKmph().doubleValue() : null)
                    .speedLimitKmph(a.getSpeedLimitKmph() != null ? a.getSpeedLimitKmph().doubleValue() : null)
                    .build());
        }

        // Attach vehiclesOperated string to each driver summary
        for (AlertBasedReportDTO.DriverAlertSummaryDTO dSum : driverSummaryMap.values()) {
            Set<String> vSet = driverVehiclesOperatedMap.get(dSum.getDriverId());
            if (vSet != null && !vSet.isEmpty()) {
                dSum.setVehiclesOperated(String.join(", ", vSet));
            } else {
                dSum.setVehiclesOperated("—");
            }
        }

        List<AlertBasedReportDTO.VehicleAlertSummaryDTO> vehicleSummaries = new ArrayList<>(vehicleSummaryMap.values());
        vehicleSummaries.sort((x, y) -> y.getTotalAlerts().compareTo(x.getTotalAlerts()));

        List<AlertBasedReportDTO.DriverAlertSummaryDTO> driverSummaries = new ArrayList<>(driverSummaryMap.values());
        driverSummaries.sort((x, y) -> y.getTotalAlerts().compareTo(x.getTotalAlerts()));

        List<AlertBasedReportDTO.DailyAlertTrendDTO> dailyTrends = new ArrayList<>();
        for (Map.Entry<String, Long> entry : dailyTrendsMap.entrySet()) {
            dailyTrends.add(AlertBasedReportDTO.DailyAlertTrendDTO.builder()
                    .date(entry.getKey())
                    .count(entry.getValue())
                    .build());
        }

        String vehicleFilterStr = null;
        if (vehicleId != null) {
            vehicleFilterStr = vehicleRepository.findById(vehicleId)
                    .map(v -> v.getRegistrationNumber() + " (" + v.getCode() + ")")
                    .orElse("Vehicle #" + vehicleId);
        }

        AlertBasedReportDTO reportData = AlertBasedReportDTO.builder()
                .reportScope("FLEET")
                .reportType("ALERT_BASED")
                .title("Fleet Overview Alert & Safety Analysis Report")
                .reportingPeriod(startDate.format(DATE_FMT) + " to " + endDate.format(DATE_FMT))
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .selectedAlertTypes(alertTypes != null ? alertTypes : List.of("All Supported Alert Types"))
                .severityFilter(severity != null ? severity : "All")
                .statusFilter(status != null ? status : "All")
                .vehicleIdFilter(vehicleId)
                .vehicleNumberFilter(vehicleFilterStr)
                .totalVehiclesCovered(coveredVehicles.size())
                .totalDriversCovered(coveredDrivers.size())
                .totalAlerts((long) matchingAlerts.size())
                .alertTypeBreakdown(alertTypeBreakdown)
                .alertSeverityBreakdown(alertSeverityBreakdown)
                .alertStatusBreakdown(alertStatusBreakdown)
                .dailyTrends(dailyTrends)
                .vehicleSummaries(vehicleSummaries)
                .driverSummaries(driverSummaries)
                .detailedAlerts(detailedAlerts)
                .build();

        // PDF Generation
        String reportId = "FLTALRTRPT_" + startDate + "_" + UUID.randomUUID().toString().substring(0, 6);
        String fileName = reportId + ".pdf";
        String pdfUrl = null, downloadUrl = null;

        try {
            Files.createDirectories(Paths.get(reportsOutputDir));
            String filePath = reportsOutputDir + File.separator + fileName;
            generateFleetAlertPdf(filePath, startDate, endDate, reportData);
            pdfUrl = "/api/reports/" + reportId + "/pdf";
            downloadUrl = "/api/reports/" + reportId + "/download";
        } catch (Exception e) {
            log.error("Failed to generate Fleet Alert PDF: {}", e.getMessage(), e);
        }

        ReportResponseDTO res = new ReportResponseDTO();
        res.setReportId(reportId);
        res.setReportType("ALERT_BASED");
        res.setStatus(pdfUrl != null ? "READY" : "FAILED");
        res.setPdfUrl(pdfUrl);
        res.setDownloadUrl(downloadUrl);
        res.setGeneratedAt(LocalDateTime.now());
        res.setMessage("Alert-based fleet report successfully generated.");
        res.setReportData(reportData);
        return res;
    }

    // ==================================================================
    // 10. PDF GENERATION: DRIVER ALERT REPORT (A4 Landscape)
    // ==================================================================
    private void generateDriverAlertPdf(String filePath, Driver driver, LocalDate startDate, LocalDate endDate,
                                        AlertBasedReportDTO data) throws Exception {
        String timestampStr = LocalDateTime.now().format(GEN_FMT);

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(filePath));
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PageFooterHandler(timestampStr));
            doc.setMargins(20, 24, 26, 24);

            // Report Header Banner
            Table header = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            Cell titleCell = new Cell().add(new Paragraph("TRACKFLEET GPS FLEET TRACKING SYSTEM")
                    .setFontSize(8).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph("Driver Alert & Safety Analysis Report")
                    .setFontSize(17).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Driver: " + driver.getName() + " (" + driver.getCode() + ")  ·  Contact: " + (driver.getPhone() != null ? driver.getPhone() : "N/A"))
                    .setFontSize(10).setFontColor(new DeviceRgb(191, 219, 254)))
                    .setBackgroundColor(HEADER_DARK).setPadding(10).setBorder(null);
            header.addCell(titleCell);

            String selectedTypesStr = (data.getSelectedAlertTypes() != null && !data.getSelectedAlertTypes().isEmpty())
                    ? String.join(", ", data.getSelectedAlertTypes()) : "All Supported Types";
            String vehFilterStr = data.getVehicleNumberFilter() != null ? (" | Vehicle: " + data.getVehicleNumberFilter()) : "";
            String filtersText = "Alerts: " + selectedTypesStr + " | Severity: " + data.getSeverityFilter() + " | Status: " + data.getStatusFilter() + vehFilterStr;

            Cell dateCell = new Cell()
                    .add(new Paragraph("REPORTING PERIOD").setFontSize(7).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph(startDate.format(DATE_FMT) + " to " + endDate.format(DATE_FMT))
                    .setFontSize(10).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Generated: " + timestampStr + " IST")
                    .setFontSize(7).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph(filtersText).setFontSize(6.5f).setFontColor(new DeviceRgb(203, 213, 225)).setMarginTop(3))
                    .setBackgroundColor(HEADER_DARK).setPadding(10).setTextAlignment(TextAlignment.RIGHT).setBorder(null);
            header.addCell(dateCell);
            doc.add(header);

            // SECTION 1: OVERVIEW & SCORING
            addSectionHeading(doc, "1. Executive Summary & Driver Scoring", "Overall alert frequency, performance categorization, and standardized safety rating");

            Table kpiTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25})).useAllAvailableWidth();
            SafetyScoreBreakdownDTO breakdown = data.getSafetyScore();
            DeviceRgb scoreCol = (breakdown != null && breakdown.getFinalSafetyScore() != null)
                    ? (breakdown.getFinalSafetyScore().compareTo(BigDecimal.valueOf(80)) >= 0 ? SUCCESS_GREEN
                    : breakdown.getFinalSafetyScore().compareTo(BigDecimal.valueOf(60)) >= 0 ? WARNING_AMBER : DANGER_RED)
                    : PRIMARY_BLUE;

            addMetricCard(kpiTable, "Matching Alerts", String.valueOf(data.getTotalAlerts()), data.getTotalAlerts() == 0 ? SUCCESS_GREEN : DANGER_RED);
            addMetricCard(kpiTable, "Safety Score", (breakdown != null ? breakdown.getFinalSafetyScore() + " / 100" : "N/A"), scoreCol);
            addMetricCard(kpiTable, "Performance Category", (breakdown != null ? breakdown.getPerformanceCategory() : "N/A"), scoreCol);
            addMetricCard(kpiTable, "Vehicles Operated", String.valueOf(data.getVehiclesOperated() != null ? data.getVehiclesOperated().size() : 0), PRIMARY_BLUE);

            long critCount = data.getAlertSeverityBreakdown().getOrDefault("CRITICAL", 0L);
            long warnCount = data.getAlertSeverityBreakdown().getOrDefault("WARNING", 0L);
            addMetricCard(kpiTable, "Critical Severity", String.valueOf(critCount), critCount > 0 ? DANGER_RED : SUCCESS_GREEN);
            addMetricCard(kpiTable, "Warning Severity", String.valueOf(warnCount), warnCount > 0 ? WARNING_AMBER : SUCCESS_GREEN);
            addMetricCard(kpiTable, "Active Penalties", String.valueOf(data.getPenalties() != null ? data.getPenalties().size() : 0), (data.getPenalties() != null && !data.getPenalties().isEmpty()) ? DANGER_RED : SUCCESS_GREEN);
            addMetricCard(kpiTable, "Total Distance Driven", (breakdown != null ? breakdown.getTotalDistanceKm() + " km" : "0 km"), PRIMARY_BLUE);
            doc.add(kpiTable);

            // SECTION 2: ALERT TYPE & SEVERITY DISTRIBUTIONS
            addSectionHeading(doc, "2. Alert Breakdown by Category & Severity", "Distribution of captured driving events matching the active filter criteria");

            Table distTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();

            Table typeTable = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            addTableHeader(typeTable, "Alert Category", "Incident Count");
            if (data.getAlertTypeBreakdown() != null && !data.getAlertTypeBreakdown().isEmpty()) {
                int r = 0;
                for (Map.Entry<String, Long> e : data.getAlertTypeBreakdown().entrySet()) {
                    DeviceRgb bg = (r++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    typeTable.addCell(dataCell(e.getKey(), bg, false));
                    typeTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
                }
            } else {
                addEmptyTableRow(typeTable, 2, "No alerts recorded for selected categories");
            }
            Cell c1 = new Cell().add(typeTable).setBorder(null).setPaddingRight(5);
            distTable.addCell(c1);

            Table sevTable = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            addTableHeader(sevTable, "Severity / Status", "Count");
            int r2 = 0;
            if (data.getAlertSeverityBreakdown() != null) {
                for (Map.Entry<String, Long> e : data.getAlertSeverityBreakdown().entrySet()) {
                    DeviceRgb bg = (r2++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    sevTable.addCell(dataCell("Severity: " + e.getKey(), bg, false));
                    sevTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
                }
            }
            if (data.getAlertStatusBreakdown() != null) {
                for (Map.Entry<String, Long> e : data.getAlertStatusBreakdown().entrySet()) {
                    DeviceRgb bg = (r2++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    sevTable.addCell(dataCell("Status: " + e.getKey(), bg, false));
                    sevTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
                }
            }
            Cell c2 = new Cell().add(sevTable).setBorder(null).setPaddingLeft(5);
            distTable.addCell(c2);
            doc.add(distTable);

            // SECTION 3: DAILY ALERT TRENDS
            if (data.getDailyTrends() != null && !data.getDailyTrends().isEmpty()) {
                addSectionHeading(doc, "3. Daily Alert Activity Trends", "Chronological occurrence of alert violations throughout the reporting period");
                Table trendTable = new Table(UnitValue.createPercentArray(new float[]{40, 60})).useAllAvailableWidth();
                addTableHeader(trendTable, "Incident Date", "Violations Count");
                int trIdx = 0;
                for (var trend : data.getDailyTrends()) {
                    DeviceRgb bg = (trIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    trendTable.addCell(dataCell(trend.getDate(), bg, false));
                    trendTable.addCell(dataCell(String.valueOf(trend.getCount()), bg, true));
                }
                doc.add(trendTable);
            }

            // SECTION 4: VEHICLES OPERATED BY DRIVER
            addSectionHeading(doc, "4. Vehicles Operated by Driver", "Comprehensive list of vehicles assigned or driven by this driver during the period");
            Table vehTable = new Table(UnitValue.createPercentArray(new float[]{18, 12, 10, 14, 16, 10, 10, 10})).useAllAvailableWidth();
            addTableHeader(vehTable, "Vehicle Reg No", "Code", "Trips", "Distance Driven", "Duration", "Total Alerts", "Overspeed", "Harsh Brake");
            if (data.getVehiclesOperated() != null && !data.getVehiclesOperated().isEmpty()) {
                int vr = 0;
                for (var v : data.getVehiclesOperated()) {
                    DeviceRgb bg = (vr++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    vehTable.addCell(dataCell(v.getVehicleNumber(), bg, false));
                    vehTable.addCell(dataCell(v.getVehicleId() != null ? "VH" + String.format("%03d", v.getVehicleId()) : "—", bg, false));
                    vehTable.addCell(dataCell(String.valueOf(v.getTrips()), bg, true));
                    vehTable.addCell(dataCell(v.getDistanceKm() != null ? v.getDistanceKm() + " km" : "0 km", bg, true));
                    vehTable.addCell(dataCell(v.getFormattedDuration(), bg, true));
                    vehTable.addCell(dataCell(String.valueOf(v.getAlerts()), bg, true));
                    vehTable.addCell(dataCell(String.valueOf(v.getOverspeedCount()), bg, true));
                    vehTable.addCell(dataCell(String.valueOf(v.getHarshBrakingCount()), bg, true));
                }
            } else {
                addEmptyTableRow(vehTable, 8, "No vehicles operated during this period");
            }
            doc.add(vehTable);

            // SECTION 5: DETAILED ALERT EVENTS LOG
            addSectionHeading(doc, "5. Detailed Alert Events Audit Trail", "Granular violation records with timestamp, vehicle registration, location, and status");
            Table alertDetailTable = new Table(UnitValue.createPercentArray(new float[]{6, 12, 10, 14, 18, 10, 20, 10})).useAllAvailableWidth();
            addTableHeader(alertDetailTable, "Sr", "Date", "Time", "Vehicle Reg", "Alert Type", "Severity", "Location", "Status");

            if (data.getDetailedAlerts() != null && !data.getDetailedAlerts().isEmpty()) {
                int ar = 0;
                for (var item : data.getDetailedAlerts()) {
                    DeviceRgb bg = (ar++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    alertDetailTable.addCell(dataCell(String.valueOf(item.getSrNo()), bg, true));
                    alertDetailTable.addCell(dataCell(item.getDate(), bg, false));
                    alertDetailTable.addCell(dataCell(item.getTime(), bg, false));
                    alertDetailTable.addCell(dataCell(item.getVehicleNumber(), bg, false));
                    alertDetailTable.addCell(dataCell(item.getAlertType(), bg, false));
                    alertDetailTable.addCell(dataCell(item.getSeverity(), bg, false));
                    alertDetailTable.addCell(dataCell(item.getLocation(), bg, false));
                    alertDetailTable.addCell(dataCell(item.getStatus(), bg, false));
                }
            } else {
                addEmptyTableRow(alertDetailTable, 8, "No alert events matched the selected filter criteria");
            }
            doc.add(alertDetailTable);

            // SECTION 6: STANDARDIZED SAFETY SCORE BREAKDOWN
            if (breakdown != null && breakdown.getMetrics() != null && !breakdown.getMetrics().isEmpty()) {
                addSectionHeading(doc, "6. Standardized Driver Safety Score Breakdown", "Formula: 100 - Σ(Normalized Event Rate_i × Weight_i) - Penalty Deductions");
                Table scoreTable = new Table(UnitValue.createPercentArray(new float[]{22, 12, 28, 16, 22})).useAllAvailableWidth();
                addTableHeader(scoreTable, "Metric", "Event Count", "Normalized Rate (per 1,000 km)", "Weight", "Weighted Deduction");
                int sr = 0;
                for (var m : breakdown.getMetrics()) {
                    DeviceRgb bg = (sr++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    scoreTable.addCell(dataCell(m.getMetricName(), bg, false));
                    scoreTable.addCell(dataCell(m.getCount() != null ? String.valueOf(m.getCount()) : "0", bg, true));
                    scoreTable.addCell(dataCell(m.getNormalizedRate() != null ? m.getNormalizedRate() + " " + m.getRateUnit() : "0.00", bg, false));
                    scoreTable.addCell(dataCell("× " + m.getWeight(), bg, true));
                    scoreTable.addCell(dataCell("-" + m.getPenalty() + " pts", bg, true));
                }
                if (breakdown.getManualPenaltyDeductions() != null && breakdown.getManualPenaltyDeductions().compareTo(BigDecimal.ZERO) > 0) {
                    scoreTable.addCell(dataCell("Manager Manual Deductions", ROW_EVEN, false));
                    scoreTable.addCell(dataCell("—", ROW_EVEN, true));
                    scoreTable.addCell(dataCell("Active Penalty Points", ROW_EVEN, false));
                    scoreTable.addCell(dataCell("1.00", ROW_EVEN, true));
                    scoreTable.addCell(dataCell("-" + breakdown.getManualPenaltyDeductions() + " pts", ROW_EVEN, true));
                }
                doc.add(scoreTable);
            }

            // SECTION 7: PENALTY SUMMARY
            addSectionHeading(doc, "7. Driver Penalty & Fine Summary", "Official penalties and demerit points issued by management during this period");
            Table penaltyTable = new Table(UnitValue.createPercentArray(new float[]{8, 20, 24, 12, 12, 12, 12})).useAllAvailableWidth();
            addTableHeader(penaltyTable, "ID", "Penalty Type", "Reason", "Points", "Fine (₹)", "Status", "Date Applied");
            if (data.getPenalties() != null && !data.getPenalties().isEmpty()) {
                int pr = 0;
                for (var p : data.getPenalties()) {
                    DeviceRgb bg = (pr++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    penaltyTable.addCell(dataCell("#" + p.getId(), bg, true));
                    penaltyTable.addCell(dataCell(p.getPenaltyType(), bg, false));
                    penaltyTable.addCell(dataCell(p.getReason(), bg, false));
                    penaltyTable.addCell(dataCell(p.getPenaltyPoints() != null ? p.getPenaltyPoints() + " pts" : "0", bg, true));
                    penaltyTable.addCell(dataCell(p.getMonetaryAmount() != null ? "₹" + p.getMonetaryAmount() : "₹0", bg, true));
                    penaltyTable.addCell(dataCell(p.getStatus(), bg, false));
                    penaltyTable.addCell(dataCell(p.getDateApplied() != null ? p.getDateApplied().format(DATE_FMT) : "N/A", bg, false));
                }
            } else {
                addEmptyTableRow(penaltyTable, 7, "No manager penalty records registered for this driver during this period");
            }
            doc.add(penaltyTable);

            // SECTION 8: RECOMMENDATIONS
            if (data.getRecommendations() != null && !data.getRecommendations().isEmpty()) {
                addSectionHeading(doc, "8. Performance Evaluation & Recommendations", "Actionable recommendations derived from actual driving telemetry");
                for (String rec : data.getRecommendations()) {
                    doc.add(new Paragraph("• " + rec).setFontSize(8).setFontColor(TEXT_PRIMARY).setMarginBottom(3));
                }
            }
        }
    }

    // ==================================================================
    // 11. PDF GENERATION: FLEET ALERT REPORT (A4 Landscape)
    // ==================================================================
    private void generateFleetAlertPdf(String filePath, LocalDate startDate, LocalDate endDate,
                                       AlertBasedReportDTO data) throws Exception {
        String timestampStr = LocalDateTime.now().format(GEN_FMT);

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(filePath));
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, new PageFooterHandler(timestampStr));
            doc.setMargins(20, 24, 26, 24);

            // Report Header Banner
            Table header = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            Cell titleCell = new Cell().add(new Paragraph("TRACKFLEET GPS FLEET TRACKING SYSTEM")
                    .setFontSize(8).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph("Fleet Overview Alert & Safety Analysis Report")
                    .setFontSize(17).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Enterprise Fleet Violations, Vehicle Distribution & Driver Safety Compliance")
                    .setFontSize(10).setFontColor(new DeviceRgb(191, 219, 254)))
                    .setBackgroundColor(HEADER_DARK).setPadding(10).setBorder(null);
            header.addCell(titleCell);

            String selectedTypesStr = (data.getSelectedAlertTypes() != null && !data.getSelectedAlertTypes().isEmpty())
                    ? String.join(", ", data.getSelectedAlertTypes()) : "All Supported Types";
            String vehFilterStr = data.getVehicleNumberFilter() != null ? (" | Vehicle: " + data.getVehicleNumberFilter()) : "";
            String filtersText = "Alerts: " + selectedTypesStr + " | Severity: " + data.getSeverityFilter() + " | Status: " + data.getStatusFilter() + vehFilterStr;

            Cell dateCell = new Cell()
                    .add(new Paragraph("REPORTING PERIOD").setFontSize(7).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph(startDate.format(DATE_FMT) + " to " + endDate.format(DATE_FMT))
                    .setFontSize(10).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Generated: " + timestampStr + " IST")
                    .setFontSize(7).setFontColor(new DeviceRgb(148, 163, 184)))
                    .add(new Paragraph(filtersText).setFontSize(6.5f).setFontColor(new DeviceRgb(203, 213, 225)).setMarginTop(3))
                    .setBackgroundColor(HEADER_DARK).setPadding(10).setTextAlignment(TextAlignment.RIGHT).setBorder(null);
            header.addCell(dateCell);
            doc.add(header);

            // SECTION 1: FLEET EXECUTIVE KPI METRICS
            addSectionHeading(doc, "1. Fleet Executive Summary", "High-level overview of violations, asset coverage, and severity distribution");

            Table kpiTable = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25})).useAllAvailableWidth();
            addMetricCard(kpiTable, "Matching Alerts", String.valueOf(data.getTotalAlerts()), data.getTotalAlerts() == 0 ? SUCCESS_GREEN : DANGER_RED);
            addMetricCard(kpiTable, "Vehicles Covered", String.valueOf(data.getTotalVehiclesCovered()), PRIMARY_BLUE);
            addMetricCard(kpiTable, "Drivers Covered", String.valueOf(data.getTotalDriversCovered()), PRIMARY_BLUE);

            long critCount = data.getAlertSeverityBreakdown().getOrDefault("CRITICAL", 0L);
            long warnCount = data.getAlertSeverityBreakdown().getOrDefault("WARNING", 0L);
            long infoCount = data.getAlertSeverityBreakdown().getOrDefault("INFO", 0L);
            addMetricCard(kpiTable, "Critical Alerts", String.valueOf(critCount), critCount > 0 ? DANGER_RED : SUCCESS_GREEN);
            addMetricCard(kpiTable, "Warning Alerts", String.valueOf(warnCount), warnCount > 0 ? WARNING_AMBER : SUCCESS_GREEN);
            addMetricCard(kpiTable, "Info Alerts", String.valueOf(infoCount), PRIMARY_BLUE);

            long openCount = data.getAlertStatusBreakdown().getOrDefault("OPEN", 0L);
            long resCount = data.getAlertStatusBreakdown().getOrDefault("RESOLVED", 0L);
            addMetricCard(kpiTable, "Open Alerts", String.valueOf(openCount), openCount > 0 ? DANGER_RED : SUCCESS_GREEN);
            addMetricCard(kpiTable, "Resolved Alerts", String.valueOf(resCount), SUCCESS_GREEN);
            doc.add(kpiTable);

            // SECTION 2: ALERT DISTRIBUTIONS
            addSectionHeading(doc, "2. Alert Breakdown by Category, Severity & Lifecycle", "Categorization of all violations recorded across the fleet during this window");

            Table distTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();

            Table typeTable = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            addTableHeader(typeTable, "Alert Category", "Total Count");
            if (data.getAlertTypeBreakdown() != null && !data.getAlertTypeBreakdown().isEmpty()) {
                int r = 0;
                for (Map.Entry<String, Long> e : data.getAlertTypeBreakdown().entrySet()) {
                    DeviceRgb bg = (r++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    typeTable.addCell(dataCell(e.getKey(), bg, false));
                    typeTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
                }
            } else {
                addEmptyTableRow(typeTable, 2, "No alerts recorded");
            }
            Cell c1 = new Cell().add(typeTable).setBorder(null).setPaddingRight(5);
            distTable.addCell(c1);

            Table sevTable = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            addTableHeader(sevTable, "Severity / Lifecycle Status", "Count");
            int r2 = 0;
            if (data.getAlertSeverityBreakdown() != null) {
                for (Map.Entry<String, Long> e : data.getAlertSeverityBreakdown().entrySet()) {
                    DeviceRgb bg = (r2++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    sevTable.addCell(dataCell("Severity: " + e.getKey(), bg, false));
                    sevTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
                }
            }
            if (data.getAlertStatusBreakdown() != null) {
                for (Map.Entry<String, Long> e : data.getAlertStatusBreakdown().entrySet()) {
                    DeviceRgb bg = (r2++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    sevTable.addCell(dataCell("Status: " + e.getKey(), bg, false));
                    sevTable.addCell(dataCell(String.valueOf(e.getValue()), bg, true));
                }
            }
            Cell c2 = new Cell().add(sevTable).setBorder(null).setPaddingLeft(5);
            distTable.addCell(c2);
            doc.add(distTable);

            // SECTION 3: DAILY ALERT TRENDS
            if (data.getDailyTrends() != null && !data.getDailyTrends().isEmpty()) {
                addSectionHeading(doc, "3. Daily Alert Activity Trends", "Daily volume of captured alert violations across the entire fleet");
                Table trendTable = new Table(UnitValue.createPercentArray(new float[]{40, 60})).useAllAvailableWidth();
                addTableHeader(trendTable, "Incident Date", "Violations Count");
                int trIdx = 0;
                for (var trend : data.getDailyTrends()) {
                    DeviceRgb bg = (trIdx++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    trendTable.addCell(dataCell(trend.getDate(), bg, false));
                    trendTable.addCell(dataCell(String.valueOf(trend.getCount()), bg, true));
                }
                doc.add(trendTable);
            }

            // SECTION 4: VEHICLE-WISE ALERT SUMMARY
            addSectionHeading(doc, "4. Vehicle-Wise Alert Summary", "Violation breakdown aggregated by fleet vehicle");
            Table vTable = new Table(UnitValue.createPercentArray(new float[]{18, 12, 12, 12, 12, 12, 12, 10})).useAllAvailableWidth();
            addTableHeader(vTable, "Vehicle Reg No", "Code", "Total Alerts", "Overspeed", "Harsh Brake", "GPS Disconnect", "Night Driving", "Other");
            if (data.getVehicleSummaries() != null && !data.getVehicleSummaries().isEmpty()) {
                int vr = 0;
                for (var v : data.getVehicleSummaries()) {
                    DeviceRgb bg = (vr++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    vTable.addCell(dataCell(v.getVehicleNumber(), bg, false));
                    vTable.addCell(dataCell(v.getVehicleCode(), bg, false));
                    vTable.addCell(dataCell(String.valueOf(v.getTotalAlerts()), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getOverspeedCount()), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getHarshBrakingCount()), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getGpsDisconnectCount()), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getNightDrivingCount()), bg, true));
                    vTable.addCell(dataCell(String.valueOf(v.getOtherCount()), bg, true));
                }
            } else {
                addEmptyTableRow(vTable, 8, "No vehicles with matching alerts");
            }
            doc.add(vTable);

            // SECTION 5: DRIVER-WISE ALERT SUMMARY
            addSectionHeading(doc, "5. Driver-Wise Alert Summary", "Violation breakdown and all vehicles operated by each driver during this period");
            Table dTable = new Table(UnitValue.createPercentArray(new float[]{16, 10, 22, 10, 8, 8, 8, 9, 9})).useAllAvailableWidth();
            addTableHeader(dTable, "Driver Name", "Driver ID", "Vehicles Operated", "Total Alerts", "Overspeed", "Harsh Brake", "Harsh Accel", "GPS Disc", "Night/Other");
            if (data.getDriverSummaries() != null && !data.getDriverSummaries().isEmpty()) {
                int dr = 0;
                for (var d : data.getDriverSummaries()) {
                    DeviceRgb bg = (dr++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    dTable.addCell(dataCell(d.getDriverName(), bg, false));
                    dTable.addCell(dataCell(d.getDriverCode(), bg, false));
                    dTable.addCell(dataCell(d.getVehiclesOperated(), bg, false));
                    dTable.addCell(dataCell(String.valueOf(d.getTotalAlerts()), bg, true));
                    dTable.addCell(dataCell(String.valueOf(d.getOverspeedCount()), bg, true));
                    dTable.addCell(dataCell(String.valueOf(d.getHarshBrakingCount()), bg, true));
                    dTable.addCell(dataCell(String.valueOf(d.getHarshAccelCount()), bg, true));
                    dTable.addCell(dataCell(String.valueOf(d.getGpsDisconnectCount()), bg, true));
                    long nightOther = d.getNightDrivingCount() + d.getOtherCount() + d.getIgnitionCount();
                    dTable.addCell(dataCell(String.valueOf(nightOther), bg, true));
                }
            } else {
                addEmptyTableRow(dTable, 9, "No drivers with matching alerts");
            }
            doc.add(dTable);

            // SECTION 6: DETAILED ALERT EVENTS
            addSectionHeading(doc, "6. Detailed Alert Events Audit Trail", "Complete incident log across all fleet assets matching the selected filters");
            Table detailTable = new Table(UnitValue.createPercentArray(new float[]{5, 11, 8, 14, 14, 10, 16, 12, 10})).useAllAvailableWidth();
            addTableHeader(detailTable, "Sr", "Date", "Time", "Vehicle Reg", "Driver Name", "Alert Type", "Severity", "Location", "Status");

            if (data.getDetailedAlerts() != null && !data.getDetailedAlerts().isEmpty()) {
                int ar = 0;
                for (var item : data.getDetailedAlerts()) {
                    DeviceRgb bg = (ar++ % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    detailTable.addCell(dataCell(String.valueOf(item.getSrNo()), bg, true));
                    detailTable.addCell(dataCell(item.getDate(), bg, false));
                    detailTable.addCell(dataCell(item.getTime(), bg, false));
                    detailTable.addCell(dataCell(item.getVehicleNumber(), bg, false));
                    detailTable.addCell(dataCell(item.getDriverName(), bg, false));
                    detailTable.addCell(dataCell(item.getAlertType(), bg, false));
                    detailTable.addCell(dataCell(item.getSeverity(), bg, false));
                    detailTable.addCell(dataCell(item.getLocation(), bg, false));
                    detailTable.addCell(dataCell(item.getStatus(), bg, false));
                }
            } else {
                addEmptyTableRow(detailTable, 9, "No alert events matched the selected filter criteria");
            }
            doc.add(detailTable);
        }
    }

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
