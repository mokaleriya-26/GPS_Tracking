package com.gps.tracking.service;

import com.gps.tracking.dto.*;
import com.gps.tracking.entity.*;
import com.gps.tracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ChatService — intent parsing and response generation for the fleet chatbot.
 *
 * Intent categories (from MIGRATION_PLAN.md Section 9.1):
 * - DRIVER_REPORT
 * - VEHICLE_REPORT
 * - FLEET_REPORT
 * - DRIVER_RANKING
 * - ALERT_QUERY
 * - DRIVER_STATS
 * - VEHICLE_STATS
 * - ENTRY_EXIT
 * - UNKNOWN
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final DriverService driverService;
    private final VehicleService vehicleService;
    private final AlertService alertService;
    private final TripService tripService;
    private final DriverDailyStatsRepository statsRepository;
    private final DriverRepository driverRepository;
    private final ReportService reportService;

    // In-memory session context (for production use Redis or DB)
    private final Map<String, Map<String, String>> sessionContext = new HashMap<>();

    @Transactional(readOnly = true)
    public ChatResponseDTO processMessage(String sessionId, String message) {
        if (sessionId == null) sessionId = UUID.randomUUID().toString();
        Map<String, String> ctx = sessionContext.computeIfAbsent(sessionId, k -> new HashMap<>());

        String normalized = message.toLowerCase().trim();
        log.info("Chat [{}]: {}", sessionId, message);

        // ---------------------------------------------------------
        // DRIVER RANKING INTENT
        // ---------------------------------------------------------
        if (normalized.matches(".*(trustworthy|best driver|top driver|safest|safety ranking|driver rank|rank driver).*")) {
            return handleDriverRanking(sessionId, normalized, ctx);
        }

        // ---------------------------------------------------------
        // DRIVER REPORT INTENT
        // ---------------------------------------------------------
        if (normalized.matches(".*(report|monthly|daily|weekly).*(driver|drv).*") ||
            normalized.matches(".*(driver|drv).*(report|monthly|daily|weekly).*")) {
            return handleDriverReportIntent(sessionId, normalized, message, ctx);
        }

        // ---------------------------------------------------------
        // VEHICLE REPORT INTENT
        // ---------------------------------------------------------
        if (normalized.matches(".*(report|monthly).*(vehicle|vh\\d+|mh\\d+).*") ||
            normalized.matches(".*(vehicle|vh\\d+|mh\\d+).*(report|monthly).*")) {
            return handleVehicleReportIntent(sessionId, normalized, message, ctx);
        }

        // ---------------------------------------------------------
        // FLEET REPORT INTENT
        // ---------------------------------------------------------
        if (normalized.contains("fleet") && (normalized.contains("report") || normalized.contains("monthly"))) {
            return handleFleetReport(sessionId, normalized, ctx);
        }

        // ---------------------------------------------------------
        // ALERT QUERY INTENT
        // ---------------------------------------------------------
        if (normalized.matches(".*(alert|overspeed|harsh braking|disconnect|night driving).*")) {
            return handleAlertQuery(sessionId, normalized, ctx);
        }

        // ---------------------------------------------------------
        // ENTRY/EXIT INTENT
        // ---------------------------------------------------------
        if (normalized.matches(".*(entr|exit|trip count|trips).*")) {
            return handleEntryExit(sessionId, normalized, ctx);
        }

        // ---------------------------------------------------------
        // DRIVER STATS INTENT
        // ---------------------------------------------------------
        if (normalized.matches(".*(how many|statistics|stats|performance).*(driver|drv).*") ||
            normalized.matches(".*(driver|drv).*(how many|statistics|stats).*")) {
            return handleDriverStats(sessionId, normalized, ctx);
        }

        // ---------------------------------------------------------
        // CONTEXT-BASED: User answering a clarification question
        // ---------------------------------------------------------
        if (ctx.containsKey("awaiting_month") && (normalized.matches(".*(january|february|march|april|may|june|july|august|september|october|november|december|\\d{4}-\\d{2}).*"))) {
            return handleContextualMonth(sessionId, normalized, ctx);
        }

        // ---------------------------------------------------------
        // UNKNOWN INTENT
        // ---------------------------------------------------------
        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("UNKNOWN")
            .message("I can help you with:\n" +
                "• **Driver reports**: \"Give me monthly report of DRV001\" or \"Rohan's September report\"\n" +
                "• **Vehicle reports**: \"Report for vehicle VH001\"\n" +
                "• **Fleet report**: \"Generate fleet monthly report\"\n" +
                "• **Driver ranking**: \"Who is the most trustworthy driver?\"\n" +
                "• **Alerts**: \"Show unresolved alerts\" or \"Overspeed alerts this month\"\n" +
                "• **Stats**: \"How many trips did DRV001 complete?\"\n" +
                "• **Entry/Exit**: \"How many entries and exits this month?\"\n\n" +
                "What would you like to know?")
            .build();
    }

    // ---- DRIVER RANKING ----
    private ChatResponseDTO handleDriverRanking(String sessionId, String normalized, Map<String, String> ctx) {
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = LocalDate.now();

        // Check if specific month mentioned
        YearMonth ym = extractYearMonth(normalized);
        if (ym != null) {
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        }

        List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);

        int limit = 5;
        if (normalized.contains("top 10")) limit = 10;
        else if (normalized.contains("top 3")) limit = 3;

        List<DriverRankingDTO> topN = rankings.stream().limit(limit).collect(Collectors.toList());

        String msg = String.format(
            "Based on data from **%s to %s**, here are the top %d drivers by safety score:\n\n" +
            "The ranking is calculated using: Safety Score = 100 - (Overspeed×5) - (Harsh Braking×4) - " +
            "(Harsh Acceleration×4) - (Night Driving hours×3) - (Fatigue×8) + (Clean Trips×1).\n\n" +
            "| Rank | Driver | Safety Score | Trips | Distance | Alerts | Overspeed | Harsh Braking |\n" +
            "|------|--------|-------------|-------|----------|--------|-----------|---------------|\n",
            from, to, limit);

        StringBuilder table = new StringBuilder(msg);
        for (DriverRankingDTO r : topN) {
            table.append(String.format("| %d | %s (%s) | %.1f | %d | %.1f km | %d | %d | %d |\n",
                r.getRank(), r.getDriverName(), r.getDriverCode(),
                r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0,
                r.getTripCount() != null ? r.getTripCount() : 0,
                r.getTotalDistanceKm() != null ? r.getTotalDistanceKm().doubleValue() : 0,
                r.getTotalAlerts() != null ? r.getTotalAlerts() : 0,
                r.getOverspeedEvents() != null ? r.getOverspeedEvents() : 0,
                r.getHarshBrakingEvents() != null ? r.getHarshBrakingEvents() : 0));
        }

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("DRIVER_RANKING")
            .message(table.toString())
            .data(topN)
            .build();
    }

    // ---- DRIVER REPORT ----
    private ChatResponseDTO handleDriverReportIntent(String sessionId, String normalized,
                                                      String original, Map<String, String> ctx) {
        // Try to find driver from message
        Driver driver = resolveDriverFromMessage(normalized);

        if (driver == null && ctx.containsKey("last_driver_id")) {
            driver = driverRepository.findById(Long.parseLong(ctx.get("last_driver_id"))).orElse(null);
        }

        if (driver == null) {
            // Try to extract from context or ask
            ctx.put("awaiting_driver", "true");
            ctx.put("pending_intent", "DRIVER_REPORT");
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("DRIVER_REPORT")
                .message("Which driver would you like a report for? Please provide the driver name or ID (e.g. DRV001).")
                .askingForClarification(true)
                .clarificationQuestion("Please provide a driver name or ID.")
                .build();
        }

        ctx.put("last_driver_id", String.valueOf(driver.getId()));
        ctx.put("last_driver_name", driver.getName());

        // Determine report type
        String reportType = "MONTHLY";
        if (normalized.contains("daily")) reportType = "DAILY";
        else if (normalized.contains("weekly")) reportType = "WEEKLY";

        // Try to get date range
        YearMonth ym = extractYearMonth(normalized);
        LocalDate startDate, endDate;

        if (ym != null) {
            startDate = ym.atDay(1);
            endDate = ym.atEndOfMonth();
        } else if ("MONTHLY".equals(reportType)) {
            // Ask for month if not provided
            ctx.put("awaiting_month", "true");
            ctx.put("pending_driver_id", String.valueOf(driver.getId()));
            ctx.put("pending_report_type", reportType);
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("DRIVER_REPORT")
                .message("For which month would you like " + driver.getName() + "'s report? (e.g. September, October 2026)")
                .askingForClarification(true)
                .clarificationQuestion("Please specify the month.")
                .build();
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = LocalDate.now();
        }

        return generateDriverReportResponse(sessionId, driver, reportType, startDate, endDate, ctx);
    }

    private ChatResponseDTO generateDriverReportResponse(String sessionId, Driver driver,
                                                          String reportType, LocalDate startDate,
                                                          LocalDate endDate, Map<String, String> ctx) {
        try {
            ReportRequestDTO req = new ReportRequestDTO();
            req.setReportType(reportType);
            req.setDriverId(driver.getId());
            req.setStartDate(startDate);
            req.setEndDate(endDate);

            ReportResponseDTO report = reportService.generateDriverReport(req);

            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("DRIVER_REPORT")
                .message(String.format("✅ **%s report for %s** (%s to %s) is ready!\n\nClick below to view or download the PDF.",
                    reportType, driver.getName(), startDate, endDate))
                .reportId(report.getReportId())
                .pdfUrl(report.getPdfUrl())
                .downloadUrl(report.getDownloadUrl())
                .data(report.getReportData())
                .build();
        } catch (Exception e) {
            log.error("Error generating driver report", e);
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("DRIVER_REPORT")
                .message("Sorry, I couldn't generate the report: " + e.getMessage())
                .build();
        }
    }

    // ---- VEHICLE REPORT ----
    private ChatResponseDTO handleVehicleReportIntent(String sessionId, String normalized,
                                                       String original, Map<String, String> ctx) {
        return ChatResponseDTO.builder()
            .sessionId(sessionId).intent("VEHICLE_REPORT")
            .message("Vehicle report generation is available. Please use the Reports page for detailed vehicle reports, or specify: \"Report for vehicle VH001 for September.\"")
            .build();
    }

    // ---- FLEET REPORT ----
    private ChatResponseDTO handleFleetReport(String sessionId, String normalized, Map<String, String> ctx) {
        YearMonth ym = extractYearMonth(normalized);
        LocalDate from = ym != null ? ym.atDay(1) : LocalDate.now().withDayOfMonth(1);
        LocalDate to = ym != null ? ym.atEndOfMonth() : LocalDate.now();

        try {
            ReportRequestDTO req = new ReportRequestDTO();
            req.setReportType("MONTHLY");
            req.setStartDate(from);
            req.setEndDate(to);
            ReportResponseDTO report = reportService.generateFleetReport(req);

            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("FLEET_REPORT")
                .message(String.format("✅ **Fleet Monthly Report** (%s to %s) is ready!", from, to))
                .reportId(report.getReportId())
                .pdfUrl(report.getPdfUrl())
                .downloadUrl(report.getDownloadUrl())
                .build();
        } catch (Exception e) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("FLEET_REPORT")
                .message("Error generating fleet report: " + e.getMessage())
                .build();
        }
    }

    // ---- ALERT QUERY ----
    private ChatResponseDTO handleAlertQuery(String sessionId, String normalized, Map<String, String> ctx) {
        long openCount = alertService.countOpenAlerts();
        String filterType = null;
        if (normalized.contains("overspeed")) filterType = "Overspeed Alert";
        else if (normalized.contains("harsh braking")) filterType = "Harsh Braking Alert";
        else if (normalized.contains("disconnect")) filterType = "GPS Disconnect Alert";
        else if (normalized.contains("night driving")) filterType = "Night Driving Alert";

        String msg = String.format("There are currently **%d open alerts**.", openCount);
        if (filterType != null) msg += " Filtering by: **" + filterType + "**. Please visit the Alerts page for full details.";
        else msg += " Use the Alerts page to view, filter, and resolve alerts.";

        return ChatResponseDTO.builder()
            .sessionId(sessionId).intent("ALERT_QUERY")
            .message(msg).build();
    }

    // ---- ENTRY/EXIT ----
    private ChatResponseDTO handleEntryExit(String sessionId, String normalized, Map<String, String> ctx) {
        YearMonth ym = extractYearMonth(normalized);
        LocalDate from = ym != null ? ym.atDay(1) : LocalDate.now().withDayOfMonth(1);
        LocalDate to = ym != null ? ym.atEndOfMonth() : LocalDate.now();

        Driver driver = resolveDriverFromMessage(normalized);
        if (driver == null && ctx.containsKey("last_driver_id")) {
            driver = driverRepository.findById(Long.parseLong(ctx.get("last_driver_id"))).orElse(null);
        }

        String scope = driver != null ? "driver **" + driver.getName() + "**" : "all drivers";
        String msg = String.format(
            "Entry/Exit analysis for %s from **%s to %s**:\n\n" +
            "• **Entries** (trip starts) correspond to ignition ON / trip start events.\n" +
            "• **Exits** (trip ends) correspond to vehicle stopped / ignition OFF events.\n\n" +
            "Please use the Reports page to generate a detailed Entry/Exit report with timestamps and locations.",
            scope, from, to);

        return ChatResponseDTO.builder()
            .sessionId(sessionId).intent("ENTRY_EXIT").message(msg).build();
    }

    // ---- DRIVER STATS ----
    private ChatResponseDTO handleDriverStats(String sessionId, String normalized, Map<String, String> ctx) {
        Driver driver = resolveDriverFromMessage(normalized);
        if (driver == null) {
            return ChatResponseDTO.builder().sessionId(sessionId).intent("DRIVER_STATS")
                .message("Which driver? Please provide a name or ID (e.g. DRV001).")
                .askingForClarification(true).build();
        }

        YearMonth ym = extractYearMonth(normalized);
        LocalDate from = ym != null ? ym.atDay(1) : LocalDate.now().withDayOfMonth(1);
        LocalDate to = ym != null ? ym.atEndOfMonth() : LocalDate.now();

        List<DriverDailyStats> stats = statsRepository.findByDriverIdAndStatDateBetweenOrderByStatDateAsc(
            driver.getId(), from, to);

        int trips = stats.stream().mapToInt(s -> s.getTripCount() != null ? s.getTripCount() : 0).sum();
        double dist = stats.stream().mapToDouble(s -> s.getDistanceKm() != null ? s.getDistanceKm().doubleValue() : 0).sum();
        int overspeed = stats.stream().mapToInt(s -> s.getOverspeedEvents() != null ? s.getOverspeedEvents() : 0).sum();
        OptionalDouble avgScore = stats.stream().filter(s -> s.getSafetyScore() != null)
            .mapToDouble(s -> s.getSafetyScore().doubleValue()).average();

        String msg = String.format(
            "**%s** (%s) — %s to %s:\n\n" +
            "• Trips Completed: **%d**\n" +
            "• Total Distance: **%.1f km**\n" +
            "• Overspeed Events: **%d**\n" +
            "• Average Safety Score: **%.1f/100**\n\n" +
            "For a full detailed report, type: \"Give me monthly report of %s for %s\"",
            driver.getName(), driver.getCode(), from, to,
            trips, dist, overspeed,
            avgScore.isPresent() ? avgScore.getAsDouble() : 0,
            driver.getName(),
            ym != null ? ym.getMonth().name() : "this month");

        return ChatResponseDTO.builder()
            .sessionId(sessionId).intent("DRIVER_STATS").message(msg).build();
    }

    // ---- CONTEXT HANDLING: Month answer ----
    private ChatResponseDTO handleContextualMonth(String sessionId, String normalized, Map<String, String> ctx) {
        ctx.remove("awaiting_month");
        Long driverId = ctx.containsKey("pending_driver_id") ?
            Long.parseLong(ctx.get("pending_driver_id")) : null;
        String reportType = ctx.getOrDefault("pending_report_type", "MONTHLY");

        YearMonth ym = extractYearMonth(normalized);
        if (ym == null) ym = YearMonth.now().minusMonths(1);

        if (driverId != null) {
            Driver driver = driverRepository.findById(driverId).orElse(null);
            if (driver != null) {
                ctx.remove("pending_driver_id");
                ctx.remove("pending_report_type");
                return generateDriverReportResponse(sessionId, driver, reportType,
                    ym.atDay(1), ym.atEndOfMonth(), ctx);
            }
        }
        return ChatResponseDTO.builder().sessionId(sessionId).intent("UNKNOWN")
            .message("Sorry, I lost context. Could you please repeat your request?").build();
    }

    // ---- HELPERS ----
    private Driver resolveDriverFromMessage(String message) {
        // Try DRV code first
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("drv\\d+").matcher(message.toLowerCase());
        if (m.find()) {
            String code = m.group().toUpperCase();
            return driverRepository.findByCode(code).orElse(null);
        }
        // Try name match
        List<Driver> all = driverRepository.findAll();
        for (Driver d : all) {
            if (message.toLowerCase().contains(d.getName().split(" ")[0].toLowerCase())) {
                return d;
            }
        }
        return null;
    }

    private YearMonth extractYearMonth(String text) {
        Map<String, Integer> months = Map.ofEntries(
            Map.entry("january", 1), Map.entry("february", 2), Map.entry("march", 3),
            Map.entry("april", 4), Map.entry("may", 5), Map.entry("june", 6),
            Map.entry("july", 7), Map.entry("august", 8), Map.entry("september", 9),
            Map.entry("october", 10), Map.entry("november", 11), Map.entry("december", 12)
        );
        int year = LocalDate.now().getYear();

        // Match "YYYY-MM"
        java.util.regex.Matcher ym = java.util.regex.Pattern.compile("(\\d{4})-(\\d{2})").matcher(text);
        if (ym.find()) {
            return YearMonth.of(Integer.parseInt(ym.group(1)), Integer.parseInt(ym.group(2)));
        }

        // Match year
        java.util.regex.Matcher yr = java.util.regex.Pattern.compile("20(2[0-9])").matcher(text);
        if (yr.find()) year = Integer.parseInt(yr.group());

        for (Map.Entry<String, Integer> entry : months.entrySet()) {
            if (text.contains(entry.getKey())) {
                return YearMonth.of(year, entry.getValue());
            }
        }
        return null;
    }
}
