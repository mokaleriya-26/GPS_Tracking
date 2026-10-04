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
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ChatService — Upgraded AI Fleet Assistant
 *
 * Provides intelligent, natural language understanding of vehicles, drivers,
 * trips, alerts, fleet performance, multi-turn dialogue, and PDF report generation.
 * All responses are grounded in real PostgreSQL fleet data.
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
    private final VehicleRepository vehicleRepository;
    private final AlertRepository alertRepository;
    private final TripRepository tripRepository;
    private final TelemetryRepository telemetryRepository;
    private final ReportService reportService;

    // In-memory session context for multi-turn dialogue
    private final Map<String, Map<String, String>> sessionContext = new HashMap<>();

    // Date range helper class
    private static class DateRange {
        final LocalDate startDate;
        final LocalDate endDate;
        final String label;

        DateRange(LocalDate s, LocalDate e, String l) {
            this.startDate = s;
            this.endDate = e;
            this.label = l;
        }

        LocalDateTime startDateTime() { return startDate.atStartOfDay(); }
        LocalDateTime endDateTime() { return endDate.plusDays(1).atStartOfDay(); }
    }

    @Transactional(readOnly = true)
    public ChatResponseDTO processMessage(String sessionId, String message) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        Map<String, String> ctx = sessionContext.computeIfAbsent(sessionId, k -> new HashMap<>());

        if (message == null) message = "";
        String normalized = message.toLowerCase().trim();
        log.info("Chat [{}]: {}", sessionId, message);

        // =========================================================
        // 1. CONTEXT RESUMPTION (Awaiting Clarification)
        // =========================================================
        if (ctx.containsKey("awaiting_report_type")) {
            return handleClarificationReportType(sessionId, normalized, ctx);
        }
        if (ctx.containsKey("awaiting_driver")) {
            Driver d = resolveDriverFromMessage(message, ctx);
            if (d != null) {
                ctx.remove("awaiting_driver");
                ctx.put("last_driver_id", String.valueOf(d.getId()));
                ctx.put("last_driver_name", d.getName());
                ctx.put("last_driver_code", d.getCode());
                ctx.put("last_subject", "DRIVER");

                String pendingIntent = ctx.remove("pending_intent");
                if ("DRIVER_STATS".equals(pendingIntent)) {
                    return handleDriverStats(sessionId, normalized, ctx);
                } else {
                    return handleDriverReportIntent(sessionId, normalized, message, ctx);
                }
            }
        }
        if (ctx.containsKey("awaiting_vehicle")) {
            Vehicle v = resolveVehicleFromMessage(message, ctx);
            if (v != null) {
                ctx.remove("awaiting_vehicle");
                ctx.put("last_vehicle_id", String.valueOf(v.getId()));
                ctx.put("last_vehicle_code", v.getCode());
                ctx.put("last_subject", "VEHICLE");

                String pendingIntent = ctx.remove("pending_intent");
                if ("VEHICLE_REPORT".equals(pendingIntent)) {
                    return handleVehicleReportIntent(sessionId, normalized, message, ctx);
                } else {
                    return handleVehicleQueryOrStats(sessionId, normalized, ctx);
                }
            }
        }
        if (ctx.containsKey("awaiting_month")) {
            DateRange dr = parseDateRange(normalized);
            YearMonth ym = extractYearMonth(normalized);
            if (dr != null || ym != null) {
                return handleContextualMonth(sessionId, normalized, ctx);
            }
        }

        // =========================================================
        // 2. GREETINGS & HELP
        // =========================================================
        if (normalized.matches("^(hi|hello|hey|greetings|help|howdy|good\\s+(morning|afternoon|evening)|what can you do).*")
                || normalized.equals("hi") || normalized.equals("hello") || normalized.equals("hey")) {
            return handleGreetingOrHelp(sessionId);
        }

        // =========================================================
        // 3. VEHICLE ALERT ANALYSIS (Most alerts / issues)
        // =========================================================
        if (normalized.matches(".*(which vehicle|what vehicle|vehicles with|top vehicle|vehicle.*(most|highest|worst|problem|trouble|highest alert)|needs attention).*")
                || normalized.matches(".*(most alerts|most overspeed|highest overspeed|highest alert|most problem).*")) {
            return handleVehicleAlertAnalysis(sessionId, normalized, ctx);
        }

        // =========================================================
        // 4. FLEET SUMMARY & GENERAL COUNTS
        // =========================================================
        if (normalized.matches(".*(fleet summary|fleet performance|fleet status|fleet activity|fleet statistics|overall fleet|fleet overview|how is the fleet|how is our fleet).*")
                || normalized.matches(".*(how many vehicles|how many drivers|how many total trips|fleet stats).*")) {
            return handleFleetSummary(sessionId, normalized, ctx);
        }

        // =========================================================
        // 5. DRIVER RANKING & SAFETY LEADERS
        // =========================================================
        if (normalized.matches(".*(trustworthy|best driver|top driver|safest|safety ranking|driver rank|rank driver|who is the safest|safest driver).*")) {
            return handleDriverRanking(sessionId, normalized, ctx);
        }

        // =========================================================
        // 6. SPECIFIC VEHICLE QUERY / STATS / DETAILS
        // =========================================================
        if (normalized.matches(".*(tell me about|details for|details of|about|show|check|status of).*vh\\d+.*")
                || normalized.matches(".*vh\\d+.*(details|info|performance|trips|distance|alerts|speed|status|odometer).*")
                || normalized.matches(".*(how many trips|how far|distance|performance).*(vh\\d+|vehicle).*")
                || (("VEHICLE".equals(ctx.get("last_subject")) || normalized.contains(" it ") || normalized.startsWith("it ") || normalized.contains("its"))
                    && normalized.matches(".*(trips?|alerts?|distance|performance|details|status).*"))) {
            return handleVehicleQueryOrStats(sessionId, normalized, ctx);
        }

        // =========================================================
        // 7. DRIVER STATS / PERFORMANCE
        // =========================================================
        if (normalized.matches(".*(how many|statistics|stats|performance).*(driver|drv|rohan|deepak|sunita|amit|kavitha|meena|suresh|vijay|priya|manoj|sanjay|ravi).*")
                || normalized.matches(".*(driver|drv).*(how many|statistics|stats|performance).*")
                || (("DRIVER".equals(ctx.get("last_subject")) || normalized.matches(".*\\b(he|him|his|she|her)\\b.*"))
                    && normalized.matches(".*(trips?|performance|stats|distance|score).*"))) {
            return handleDriverStats(sessionId, normalized, ctx);
        }

        // =========================================================
        // 8. GENERAL / SPECIFIC REPORT TRIGGERS
        // =========================================================
        if (normalized.matches(".*(generate|create|download|give me|show|make).*(report).*") || normalized.equals("report") || normalized.equals("reports")) {
            return handleGeneralReportTrigger(sessionId, normalized, message, ctx);
        }
        if (normalized.matches(".*(report|monthly|daily|weekly).*(driver|drv).*") ||
            normalized.matches(".*(driver|drv).*(report|monthly|daily|weekly).*")) {
            return handleDriverReportIntent(sessionId, normalized, message, ctx);
        }
        if (normalized.matches(".*(report|monthly).*(vehicle|vh\\d+|mh\\d+).*") ||
            normalized.matches(".*(vehicle|vh\\d+|mh\\d+).*(report|monthly).*")) {
            return handleVehicleReportIntent(sessionId, normalized, message, ctx);
        }
        if (normalized.contains("fleet") && (normalized.contains("report") || normalized.contains("monthly"))) {
            return handleFleetReport(sessionId, normalized, ctx);
        }

        // =========================================================
        // 9. ALERT QUERIES & BREAKDOWNS
        // =========================================================
        if (normalized.matches(".*(alert|overspeed|harsh braking|disconnect|night driving|speeding incidents|fatigue).*")) {
            return handleAlertQuery(sessionId, normalized, ctx);
        }

        // =========================================================
        // 10. ENTRY / EXIT INTENT
        // =========================================================
        if (normalized.matches(".*(entr|exit|trip count|trips).*")) {
            return handleEntryExit(sessionId, normalized, ctx);
        }

        // =========================================================
        // 11. UNKNOWN INTENT FALLBACK
        // =========================================================
        return handleUnknownFallback(sessionId);
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    /** Greetings and Help */
    private ChatResponseDTO handleGreetingOrHelp(String sessionId) {
        String msg = "👋 **Hi! I'm your Fleet Assistant.**\n\n" +
            "I have real-time access to your fleet database, including **vehicles, drivers, trips, alerts, and PDF reports**.\n\n" +
            "Here are some examples of what you can ask:\n" +
            "• *\"Give me today's fleet summary\"*\n" +
            "• *\"Which vehicle has the most alerts?\"*\n" +
            "• *\"Tell me about VH003\"*\n" +
            "• *\"Who is the safest driver?\"*\n" +
            "• *\"How many trips did DRV001 complete?\"*\n" +
            "• *\"Show overspeed alerts\"*\n" +
            "• *\"Generate fleet report for September 2026\"*";

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("GREETING")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "🚗 Vehicles", "⚠️ Top Alerts", "📄 Reports"))
            .build();
    }

    /** 3. FLEET SUMMARY */
    private ChatResponseDTO handleFleetSummary(String sessionId, String normalized, Map<String, String> ctx) {
        long totalVehicles = vehicleRepository.count();
        long activeVehicles = vehicleRepository.findByActiveTrue().size();
        long totalDrivers = driverRepository.count();
        long activeDrivers = driverRepository.findByActiveTrue().size();

        DateRange dr = parseDateRange(normalized);
        long tripCount;
        Double totalDistance;
        long totalAlerts;
        List<Object[]> alertTypes;
        String periodLabel;

        if (dr != null) {
            LocalDateTime from = dr.startDateTime();
            LocalDateTime to = dr.endDateTime();
            tripCount = tripRepository.countInDateRange(from, to);
            totalDistance = tripRepository.sumDistanceInDateRange(from, to);
            totalAlerts = alertRepository.countInDateRange(from, to);
            alertTypes = alertRepository.countByTypeInDateRange(from, to);
            periodLabel = dr.label;
        } else {
            tripCount = tripRepository.count();
            totalDistance = tripRepository.sumTotalDistance();
            totalAlerts = alertRepository.count();
            alertTypes = alertRepository.countAllGroupedByType();
            periodLabel = "Overall (All-Time)";
        }

        long openAlerts = alertRepository.countOpenAlerts();

        Map<String, Long> alertMap = new LinkedHashMap<>();
        for (Object[] row : alertTypes) {
            alertMap.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        long overspeed = alertMap.getOrDefault("Overspeed Alert", 0L);
        long harshBraking = alertMap.getOrDefault("Harsh Braking Alert", 0L);
        long disconnect = alertMap.getOrDefault("GPS Disconnect Alert", 0L);
        long nightDriving = alertMap.getOrDefault("Night Driving Alert", 0L);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 **Fleet Summary (%s)**\n\n", periodLabel));
        sb.append("| Metric | Count |\n");
        sb.append("|:---|---:|\n");
        sb.append(String.format("| **Total Vehicles** | %d (%d active) |\n", totalVehicles, activeVehicles));
        sb.append(String.format("| **Total Drivers** | %d (%d active) |\n", totalDrivers, activeDrivers));
        sb.append(String.format("| **Trips Completed** | %,d |\n", tripCount));
        sb.append(String.format("| **Total Distance** | %,.1f km |\n", totalDistance != null ? totalDistance : 0.0));
        sb.append(String.format("| **Total Alerts** | %,d (%d open) |\n\n", totalAlerts, openAlerts));

        sb.append("⚠️ **Alert Breakdown:**\n");
        sb.append(String.format("• **Overspeed:** %,d\n", overspeed));
        sb.append(String.format("• **Harsh Braking:** %,d\n", harshBraking));
        sb.append(String.format("• **GPS Disconnects:** %,d\n", disconnect));
        sb.append(String.format("• **Night Driving:** %,d\n\n", nightDriving));

        if (openAlerts > 0) {
            sb.append(String.format("💡 *There are currently %d open alerts requiring fleet manager review.*", openAlerts));
        } else {
            sb.append("💡 *All alerts are currently resolved or historical.*");
        }

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("FLEET_SUMMARY")
            .message(sb.toString())
            .quickActions(List.of("🏆 Safest Driver", "🚗 Vehicle Alerts", "⚠️ Unresolved Alerts", "📄 Fleet Report"))
            .build();
    }

    /** 4. VEHICLE ALERT ANALYSIS (Most alerts / issues) */
    private ChatResponseDTO handleVehicleAlertAnalysis(String sessionId, String normalized, Map<String, String> ctx) {
        List<Object[]> rows;
        String filterMsg;

        if (normalized.contains("overspeed") || normalized.contains("speed")) {
            rows = alertRepository.findTopVehiclesByAlertType("overspeed");
            filterMsg = "Top vehicles by **Overspeed Alerts**";
        } else if (normalized.contains("harsh braking") || normalized.contains("braking")) {
            rows = alertRepository.findTopVehiclesByAlertType("harsh braking");
            filterMsg = "Top vehicles by **Harsh Braking Alerts**";
        } else if (normalized.contains("disconnect") || normalized.contains("offline") || normalized.contains("gps")) {
            rows = alertRepository.findTopVehiclesByAlertType("disconnect");
            filterMsg = "Top vehicles by **GPS Disconnects**";
        } else {
            rows = alertRepository.findTopVehiclesByAlertCount();
            filterMsg = "Vehicles with the **Most Alerts** (Needs Attention)";
        }

        if (rows.isEmpty()) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("VEHICLE_ALERT_ANALYSIS")
                .message("No vehicles found with alerts matching your criteria.")
                .quickActions(List.of("📊 Fleet Summary", "🚗 All Vehicles"))
                .build();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("🚨 **%s**:\n\n", filterMsg));
        sb.append("| Rank | Vehicle | Registration | Total Alerts |\n");
        sb.append("|:---:|:---|:---|---:|\n");

        int rank = 1;
        String topVehicleCode = null;
        for (Object[] r : rows.stream().limit(5).collect(Collectors.toList())) {
            String vCode = String.valueOf(r[1]);
            String vReg = String.valueOf(r[2]);
            long count = ((Number) r[3]).longValue();
            if (topVehicleCode == null) topVehicleCode = vCode;

            sb.append(String.format("| %d | **%s** | %s | %,d |\n", rank++, vCode, vReg, count));
        }

        if (topVehicleCode != null) {
            sb.append(String.format("\n💡 **%s** has the highest alert frequency and may require maintenance or driver coaching.", topVehicleCode));
            ctx.put("last_subject", "VEHICLE");
            ctx.put("last_vehicle_code", topVehicleCode);
            Optional<Vehicle> vOpt = vehicleRepository.findByCode(topVehicleCode);
            vOpt.ifPresent(v -> ctx.put("last_vehicle_id", String.valueOf(v.getId())));
        }

        List<String> actions = new ArrayList<>();
        if (topVehicleCode != null) actions.add("Tell me about " + topVehicleCode);
        actions.add("⚠️ Show All Alerts");
        actions.add("📊 Fleet Summary");

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("VEHICLE_ALERT_ANALYSIS")
            .message(sb.toString())
            .quickActions(actions)
            .build();
    }

    /** 2 & 6. VEHICLE QUERY & STATS */
    private ChatResponseDTO handleVehicleQueryOrStats(String sessionId, String normalized, Map<String, String> ctx) {
        Vehicle v = resolveVehicleFromMessage(normalized, ctx);

        if (v == null) {
            ctx.put("awaiting_vehicle", "true");
            ctx.put("pending_intent", "VEHICLE_QUERY");
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("VEHICLE_QUERY")
                .message("Which vehicle would you like to check? Please provide a Vehicle ID such as **VH001** or registration number (e.g. MH03AC4582).")
                .askingForClarification(true)
                .clarificationQuestion("Please specify the vehicle ID.")
                .quickActions(List.of("VH001", "VH002", "VH003", "VH004"))
                .build();
        }

        ctx.put("last_subject", "VEHICLE");
        ctx.put("last_vehicle_id", String.valueOf(v.getId()));
        ctx.put("last_vehicle_code", v.getCode());

        long trips = tripRepository.countByVehicleId(v.getId());
        Double distance = tripRepository.sumDistanceByVehicleId(v.getId());
        long totalAlerts = alertRepository.countByVehicleId(v.getId());

        List<Object[]> alertTypes = alertRepository.countByTypeForVehicle(v.getId());
        Map<String, Long> alertMap = new HashMap<>();
        for (Object[] r : alertTypes) {
            alertMap.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        }

        long overspeed = alertMap.getOrDefault("Overspeed Alert", 0L);
        long harshBraking = alertMap.getOrDefault("Harsh Braking Alert", 0L);
        long disconnect = alertMap.getOrDefault("GPS Disconnect Alert", 0L);
        long nightDriving = alertMap.getOrDefault("Night Driving Alert", 0L);

        Optional<Telemetry> telem = telemetryRepository.findTopByVehicleIdOrderByRecordedAtDesc(v.getId());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("🚗 **Vehicle Details: %s**\n\n", v.getCode()));
        sb.append("| Attribute | Value |\n");
        sb.append("|:---|:---|\n");
        sb.append(String.format("| **Registration** | %s |\n", v.getRegistrationNumber()));
        sb.append(String.format("| **Model** | %s (%s) |\n", v.getMakeModel(), v.getVehicleType()));
        sb.append(String.format("| **Fuel Type** | %s (Tank: %.1f L) |\n", v.getFuelType(), v.getTankCapacityLitres() != null ? v.getTankCapacityLitres().doubleValue() : 0.0));
        sb.append(String.format("| **Status** | %s |\n", v.getActive() ? "Active 🟢" : "Inactive ⚪"));
        if (v.getDriver() != null) {
            sb.append(String.format("| **Assigned Driver** | %s (%s) |\n", v.getDriver().getName(), v.getDriver().getCode()));
            ctx.put("last_driver_id", String.valueOf(v.getDriver().getId()));
            ctx.put("last_driver_name", v.getDriver().getName());
            ctx.put("last_driver_code", v.getDriver().getCode());
        } else {
            sb.append("| **Assigned Driver** | Unassigned |\n");
        }
        sb.append(String.format("| **Total Trips** | %,d |\n", trips));
        sb.append(String.format("| **Total Distance** | %,.1f km |\n", distance != null ? distance : 0.0));
        sb.append(String.format("| **Odometer** | %,.1f km |\n", v.getOdometerKm() != null ? v.getOdometerKm().doubleValue() : 0.0));
        sb.append(String.format("| **Total Alerts** | %,d |\n\n", totalAlerts));

        sb.append("⚠️ **Alert Breakdown:**\n");
        sb.append(String.format("• Overspeed: **%,d**  • Harsh Braking: **%,d**\n", overspeed, harshBraking));
        sb.append(String.format("• GPS Disconnects: **%,d**  • Night Driving: **%,d**\n\n", disconnect, nightDriving));

        if (telem.isPresent()) {
            Telemetry t = telem.get();
            sb.append(String.format("📍 **Latest Telemetry:** Speed: **%.1f km/h** | Ignition: **%s**",
                t.getSpeedKmph() != null ? t.getSpeedKmph().doubleValue() : 0.0,
                Boolean.TRUE.equals(t.getIgnition()) ? "ON" : "OFF"));
            if (t.getRoadName() != null && !t.getRoadName().isEmpty()) {
                sb.append(" | Near: *").append(t.getRoadName()).append("*");
            }
        }

        List<String> actions = new ArrayList<>();
        actions.add("How many alerts did it have?");
        actions.add("What about its trips?");
        if (v.getDriver() != null) actions.add("Tell me about " + v.getDriver().getName());
        actions.add("Generate its report");

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("VEHICLE_STATS")
            .message(sb.toString())
            .quickActions(actions)
            .build();
    }

    /** 1. DRIVER RANKING INTENT */
    private ChatResponseDTO handleDriverRanking(String sessionId, String normalized, Map<String, String> ctx) {
        DateRange dr = parseDateRange(normalized);
        LocalDate from, to;
        String periodLabel;

        if (dr != null) {
            from = dr.startDate;
            to = dr.endDate;
            periodLabel = dr.label;
        } else {
            from = LocalDate.now().withDayOfMonth(1);
            to = LocalDate.now();
            periodLabel = "Current Month";
        }

        List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);

        int limit = 5;
        if (normalized.contains("top 10")) limit = 10;
        else if (normalized.contains("top 3")) limit = 3;

        List<DriverRankingDTO> topN = rankings.stream().limit(limit).collect(Collectors.toList());

        if (topN.isEmpty()) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("DRIVER_RANKING")
                .message(String.format("No driver rankings available for **%s** (%s to %s).", periodLabel, from, to))
                .quickActions(List.of("📊 Fleet Summary", "👨 All Drivers"))
                .build();
        }

        StringBuilder table = new StringBuilder();
        table.append(String.format("🏆 **Top Drivers by Safety Score (%s)**\n\n", periodLabel));
        table.append("| Rank | Driver | Safety Score | Trips | Distance | Alerts | Overspeed | Harsh Braking |\n");
        table.append("|:---:|:---|:---:|---:|---:|---:|---:|---:|\n");

        for (DriverRankingDTO r : topN) {
            table.append(String.format("| %d | **%s** (%s) | **%.1f** | %,d | %,.1f km | %,d | %,d | %,d |\n",
                r.getRank(), r.getDriverName(), r.getDriverCode(),
                r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0,
                r.getTripCount() != null ? r.getTripCount() : 0,
                r.getTotalDistanceKm() != null ? r.getTotalDistanceKm().doubleValue() : 0.0,
                r.getTotalAlerts() != null ? r.getTotalAlerts() : 0,
                r.getOverspeedEvents() != null ? r.getOverspeedEvents() : 0,
                r.getHarshBrakingEvents() != null ? r.getHarshBrakingEvents() : 0));
        }

        DriverRankingDTO best = topN.get(0);
        table.append(String.format("\n🌟 **%s** is currently the safest driver with a score of **%.1f/100** across %,d trips.",
            best.getDriverName(), best.getSafetyScore(), best.getTripCount()));

        ctx.put("last_subject", "DRIVER");
        ctx.put("last_driver_id", String.valueOf(best.getDriverId()));
        ctx.put("last_driver_name", best.getDriverName());
        ctx.put("last_driver_code", best.getDriverCode());

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("DRIVER_RANKING")
            .message(table.toString())
            .data(topN)
            .quickActions(List.of("Show details for " + best.getDriverName(), "📊 Fleet Summary", "📄 Generate Driver Report"))
            .build();
    }

    /** 7. DRIVER STATS */
    private ChatResponseDTO handleDriverStats(String sessionId, String normalized, Map<String, String> ctx) {
        Driver driver = resolveDriverFromMessage(normalized, ctx);
        if (driver == null) {
            ctx.put("awaiting_driver", "true");
            ctx.put("pending_intent", "DRIVER_STATS");
            return ChatResponseDTO.builder().sessionId(sessionId).intent("DRIVER_STATS")
                .message("Which driver would you like to check? Please provide a name (e.g. Rohan) or ID (e.g. DRV001).")
                .askingForClarification(true)
                .clarificationQuestion("Please provide a driver name or ID.")
                .quickActions(List.of("DRV001", "DRV002", "DRV003", "DRV004"))
                .build();
        }

        ctx.put("last_subject", "DRIVER");
        ctx.put("last_driver_id", String.valueOf(driver.getId()));
        ctx.put("last_driver_name", driver.getName());
        ctx.put("last_driver_code", driver.getCode());

        DateRange dr = parseDateRange(normalized);
        LocalDate from, to;
        String periodLabel;
        if (dr != null) {
            from = dr.startDate;
            to = dr.endDate;
            periodLabel = dr.label;
        } else {
            from = LocalDate.now().withDayOfMonth(1);
            to = LocalDate.now();
            periodLabel = "Current Month";
        }

        List<DriverDailyStats> stats = statsRepository.findByDriverIdAndStatDateBetweenOrderByStatDateAsc(
            driver.getId(), from, to);

        long tripCount = tripRepository.countByDriverIdAndDateRange(driver.getId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        if (tripCount == 0 && stats.size() > 0) {
            tripCount = stats.stream().mapToInt(s -> s.getTripCount() != null ? s.getTripCount() : 0).sum();
        }
        Double dist = tripRepository.sumDistanceByDriverAndDateRange(driver.getId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        if (dist == null || dist == 0.0) {
            dist = stats.stream().mapToDouble(s -> s.getDistanceKm() != null ? s.getDistanceKm().doubleValue() : 0.0).sum();
        }

        long overspeed = stats.stream().mapToInt(s -> s.getOverspeedEvents() != null ? s.getOverspeedEvents() : 0).sum();
        OptionalDouble avgScore = stats.stream().filter(s -> s.getSafetyScore() != null)
            .mapToDouble(s -> s.getSafetyScore().doubleValue()).average();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("👨 **Driver Performance: %s (%s)**\n\n", driver.getName(), driver.getCode()));
        sb.append(String.format("Period: **%s** (%s to %s)\n\n", periodLabel, from, to));
        sb.append("| Metric | Value |\n");
        sb.append("|:---|---:|\n");
        sb.append(String.format("| **Trips Completed** | %,d |\n", tripCount));
        sb.append(String.format("| **Total Distance** | %,.1f km |\n", dist != null ? dist : 0.0));
        sb.append(String.format("| **Overspeed Events** | %,d |\n", overspeed));
        sb.append(String.format("| **Average Safety Score** | **%.1f / 100** |\n\n", avgScore.isPresent() ? avgScore.getAsDouble() : 100.0));

        sb.append("💡 *Safety score incorporates speed limits, braking intensity, acceleration, and fatigue control.*");

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("DRIVER_STATS")
            .message(sb.toString())
            .quickActions(List.of("Generate report for " + driver.getCode(), "🏆 Driver Rankings", "📊 Fleet Summary"))
            .build();
    }

    /** 8. GENERAL REPORT TRIGGER */
    private ChatResponseDTO handleGeneralReportTrigger(String sessionId, String normalized, String message, Map<String, String> ctx) {
        if (normalized.contains("fleet") || normalized.contains("overall")) {
            return handleFleetReport(sessionId, normalized, ctx);
        }
        if (normalized.contains("driver") || normalized.contains("drv")) {
            return handleDriverReportIntent(sessionId, normalized, message, ctx);
        }
        if (normalized.contains("vehicle") || normalized.contains("vh")) {
            return handleVehicleReportIntent(sessionId, normalized, message, ctx);
        }

        // Check if we have an active subject in context
        if ("DRIVER".equals(ctx.get("last_subject")) && ctx.containsKey("last_driver_id")) {
            return handleDriverReportIntent(sessionId, normalized, message, ctx);
        }
        if ("VEHICLE".equals(ctx.get("last_subject")) && ctx.containsKey("last_vehicle_id")) {
            return handleVehicleReportIntent(sessionId, normalized, message, ctx);
        }

        // Ask for clarification
        ctx.put("awaiting_report_type", "true");
        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("REPORT_FLOW")
            .message("Sure! Which type of report would you like to generate?")
            .askingForClarification(true)
            .clarificationQuestion("Please select a report type.")
            .quickActions(List.of("👨 Driver Report", "🚗 Vehicle Report", "📊 Fleet Monthly Report"))
            .build();
    }

    private ChatResponseDTO handleClarificationReportType(String sessionId, String normalized, Map<String, String> ctx) {
        ctx.remove("awaiting_report_type");
        if (normalized.contains("driver")) {
            return handleDriverReportIntent(sessionId, normalized, "", ctx);
        } else if (normalized.contains("vehicle")) {
            return handleVehicleReportIntent(sessionId, normalized, "", ctx);
        } else {
            return handleFleetReport(sessionId, normalized, ctx);
        }
    }

    /** DRIVER REPORT INTENT */
    private ChatResponseDTO handleDriverReportIntent(String sessionId, String normalized, String original, Map<String, String> ctx) {
        Driver driver = resolveDriverFromMessage(normalized, ctx);

        if (driver == null && ctx.containsKey("last_driver_id")) {
            driver = driverRepository.findById(Long.parseLong(ctx.get("last_driver_id"))).orElse(null);
        }

        if (driver == null) {
            ctx.put("awaiting_driver", "true");
            ctx.put("pending_intent", "DRIVER_REPORT");
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("DRIVER_REPORT")
                .message("Which driver would you like a report for? Please provide a driver name or ID (e.g. **DRV001** or **Rohan**).")
                .askingForClarification(true)
                .clarificationQuestion("Please provide a driver name or ID.")
                .quickActions(List.of("DRV001", "DRV002", "DRV003", "DRV004"))
                .build();
        }

        ctx.put("last_subject", "DRIVER");
        ctx.put("last_driver_id", String.valueOf(driver.getId()));
        ctx.put("last_driver_name", driver.getName());
        ctx.put("last_driver_code", driver.getCode());

        String reportType = "MONTHLY";
        if (normalized.contains("daily")) reportType = "DAILY";
        else if (normalized.contains("weekly")) reportType = "WEEKLY";

        DateRange dr = parseDateRange(normalized);
        LocalDate startDate, endDate;

        if (dr != null) {
            startDate = dr.startDate;
            endDate = dr.endDate;
        } else if ("MONTHLY".equals(reportType)) {
            YearMonth ym = extractYearMonth(normalized);
            if (ym != null) {
                startDate = ym.atDay(1);
                endDate = ym.atEndOfMonth();
            } else {
                ctx.put("awaiting_month", "true");
                ctx.put("pending_driver_id", String.valueOf(driver.getId()));
                ctx.put("pending_report_type", reportType);
                return ChatResponseDTO.builder()
                    .sessionId(sessionId).intent("DRIVER_REPORT")
                    .message("For which month would you like **" + driver.getName() + "'s** report?")
                    .askingForClarification(true)
                    .clarificationQuestion("Please specify the month.")
                    .quickActions(List.of("September 2026", "October 2026", "August 2026"))
                    .build();
            }
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
                .message(String.format("✅ **%s Report for %s (%s)** is ready!\n\nPeriod: **%s to %s**\nClick below to view or download the generated PDF.",
                    reportType, driver.getName(), driver.getCode(), startDate, endDate))
                .reportId(report.getReportId())
                .pdfUrl(report.getPdfUrl())
                .downloadUrl(report.getDownloadUrl())
                .data(report.getReportData())
                .quickActions(List.of("Show details for " + driver.getCode(), "🏆 Driver Rankings", "📊 Fleet Summary"))
                .build();
        } catch (Exception e) {
            log.error("Error generating driver report", e);
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("DRIVER_REPORT")
                .message("Sorry, I encountered an error generating the report: " + e.getMessage())
                .build();
        }
    }

    /** VEHICLE REPORT INTENT */
    private ChatResponseDTO handleVehicleReportIntent(String sessionId, String normalized, String original, Map<String, String> ctx) {
        Vehicle v = resolveVehicleFromMessage(normalized, ctx);
        if (v == null) {
            ctx.put("awaiting_vehicle", "true");
            ctx.put("pending_intent", "VEHICLE_REPORT");
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("VEHICLE_REPORT")
                .message("Which vehicle would you like a report for? (e.g. **VH001**, **VH003**)")
                .askingForClarification(true)
                .clarificationQuestion("Please specify the vehicle ID.")
                .quickActions(List.of("VH001", "VH002", "VH003", "VH004"))
                .build();
        }

        ctx.put("last_subject", "VEHICLE");
        ctx.put("last_vehicle_id", String.valueOf(v.getId()));
        ctx.put("last_vehicle_code", v.getCode());

        // If vehicle has an assigned driver, generate the comprehensive driver/vehicle report
        if (v.getDriver() != null) {
            DateRange dr = parseDateRange(normalized);
            LocalDate start = dr != null ? dr.startDate : LocalDate.now().withDayOfMonth(1);
            LocalDate end = dr != null ? dr.endDate : LocalDate.now();

            return generateDriverReportResponse(sessionId, v.getDriver(), "MONTHLY", start, end, ctx);
        } else {
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("VEHICLE_REPORT")
                .message(String.format("Vehicle **%s** does not have an assigned driver. You can view its full telemetry on the Vehicles page.", v.getCode()))
                .quickActions(List.of("Tell me about " + v.getCode(), "📊 Fleet Summary"))
                .build();
        }
    }

    /** FLEET REPORT INTENT */
    private ChatResponseDTO handleFleetReport(String sessionId, String normalized, Map<String, String> ctx) {
        DateRange dr = parseDateRange(normalized);
        LocalDate from, to;

        if (dr != null) {
            from = dr.startDate;
            to = dr.endDate;
        } else {
            YearMonth ym = extractYearMonth(normalized);
            from = ym != null ? ym.atDay(1) : LocalDate.now().withDayOfMonth(1);
            to = ym != null ? ym.atEndOfMonth() : LocalDate.now();
        }

        try {
            ReportRequestDTO req = new ReportRequestDTO();
            req.setReportType("MONTHLY");
            req.setStartDate(from);
            req.setEndDate(to);
            ReportResponseDTO report = reportService.generateFleetReport(req);

            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("FLEET_REPORT")
                .message(String.format("✅ **Fleet Monthly Report (%s to %s)** is ready!\n\nClick below to view or download the comprehensive fleet PDF report.", from, to))
                .reportId(report.getReportId())
                .pdfUrl(report.getPdfUrl())
                .downloadUrl(report.getDownloadUrl())
                .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "⚠️ View Alerts"))
                .build();
        } catch (Exception e) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent("FLEET_REPORT")
                .message("Error generating fleet report: " + e.getMessage())
                .build();
        }
    }

    /** 9. ALERT QUERY INTENT */
    private ChatResponseDTO handleAlertQuery(String sessionId, String normalized, Map<String, String> ctx) {
        String typeFilter = null;
        String typeLabel = null;

        if (normalized.contains("overspeed") || normalized.contains("speed")) {
            typeFilter = "overspeed";
            typeLabel = "Overspeed";
        } else if (normalized.contains("harsh braking") || normalized.contains("braking")) {
            typeFilter = "harsh braking";
            typeLabel = "Harsh Braking";
        } else if (normalized.contains("disconnect") || normalized.contains("offline") || normalized.contains("gps")) {
            typeFilter = "disconnect";
            typeLabel = "GPS Disconnect";
        } else if (normalized.contains("night")) {
            typeFilter = "night";
            typeLabel = "Night Driving";
        } else if (normalized.contains("fatigue")) {
            typeFilter = "fatigue";
            typeLabel = "Fatigue";
        }

        DateRange dr = parseDateRange(normalized);
        StringBuilder sb = new StringBuilder();

        if (typeFilter != null) {
            long totalForType = dr != null
                ? alertRepository.countByAlertTypeLikeInDateRange(typeFilter, dr.startDateTime(), dr.endDateTime())
                : alertRepository.countByAlertTypeLike(typeFilter);
            long openForType = alertRepository.countOpenByAlertTypeLike(typeFilter);

            List<Object[]> topVehicles = alertRepository.findTopVehiclesByAlertType(typeFilter);

            sb.append(String.format("⚠️ **%s Alert Summary%s**:\n\n", typeLabel, dr != null ? " (" + dr.label + ")" : ""));
            sb.append(String.format("• **Total Occurrences:** %,d\n", totalForType));
            sb.append(String.format("• **Currently Open:** %,d\n\n", openForType));

            if (!topVehicles.isEmpty()) {
                sb.append("**Top vehicles affected:**\n");
                for (Object[] r : topVehicles.stream().limit(3).collect(Collectors.toList())) {
                    sb.append(String.format("• Vehicle **%s** (%s): %,d alerts\n", r[1], r[2], ((Number) r[3]).longValue()));
                }
            }
        } else {
            long openCount = alertRepository.countOpenAlerts();
            long totalCount = alertRepository.count();
            List<Object[]> typeCounts = alertRepository.countAllGroupedByType();

            sb.append("⚠️ **Fleet Alerts Overview**:\n\n");
            sb.append(String.format("• **Total Historical Alerts:** %,d\n", totalCount));
            sb.append(String.format("• **Active Open Alerts:** %,d\n\n", openCount));
            sb.append("| Alert Category | Count |\n");
            sb.append("|:---|---:|\n");
            for (Object[] r : typeCounts) {
                sb.append(String.format("| %s | %,d |\n", r[0], ((Number) r[1]).longValue()));
            }
        }

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("ALERT_QUERY")
            .message(sb.toString())
            .quickActions(List.of("Which vehicle has the most alerts?", "📊 Fleet Summary", "📄 Fleet Report"))
            .build();
    }

    /** 10. ENTRY / EXIT INTENT */
    private ChatResponseDTO handleEntryExit(String sessionId, String normalized, Map<String, String> ctx) {
        DateRange dr = parseDateRange(normalized);
        LocalDate from = dr != null ? dr.startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate to = dr != null ? dr.endDate : LocalDate.now();

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        long entries = tripRepository.countDistinctVehiclesWithTripStarted(fromDt, toDt);
        long exits = tripRepository.countDistinctVehiclesWithTripCompleted(fromDt, toDt);

        String msg = String.format(
            "🚪 **Entry / Exit Activity (%s to %s)**:\n\n" +
            "• **Vehicle Entries (Trips Started):** **%,d** active vehicles\n" +
            "• **Vehicle Exits (Trips Completed):** **%,d** completed trips\n\n" +
            "Trips start when vehicle ignition is turned ON and finish when the engine is turned OFF.\n" +
            "Full timestamped logs are accessible on the Trips page.",
            from, to, entries, exits);

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("ENTRY_EXIT")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🚗 Vehicle Alerts", "🏆 Safest Driver"))
            .build();
    }

    /** Month context handler for clarification answers */
    private ChatResponseDTO handleContextualMonth(String sessionId, String normalized, Map<String, String> ctx) {
        ctx.remove("awaiting_month");
        Long driverId = ctx.containsKey("pending_driver_id") ?
            Long.parseLong(ctx.get("pending_driver_id")) : null;
        String reportType = ctx.getOrDefault("pending_report_type", "MONTHLY");

        DateRange dr = parseDateRange(normalized);
        YearMonth ym = extractYearMonth(normalized);
        LocalDate startDate, endDate;

        if (dr != null) {
            startDate = dr.startDate;
            endDate = dr.endDate;
        } else if (ym != null) {
            startDate = ym.atDay(1);
            endDate = ym.atEndOfMonth();
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = LocalDate.now();
        }

        if (driverId != null) {
            Driver driver = driverRepository.findById(driverId).orElse(null);
            if (driver != null) {
                ctx.remove("pending_driver_id");
                ctx.remove("pending_report_type");
                return generateDriverReportResponse(sessionId, driver, reportType, startDate, endDate, ctx);
            }
        }
        return ChatResponseDTO.builder().sessionId(sessionId).intent("UNKNOWN")
            .message("Sorry, I lost context. Could you please specify your request again?")
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "📄 Generate Report"))
            .build();
    }

    /** Fallback when unknown */
    private ChatResponseDTO handleUnknownFallback(String sessionId) {
        String msg = "I'm not quite sure how to answer that, but I can help you with:\n\n" +
            "• **Fleet Overview**: *\"Give me today's fleet summary\"*\n" +
            "• **Vehicles**: *\"Tell me about VH003\"* or *\"Which vehicle has the most alerts?\"*\n" +
            "• **Drivers**: *\"Who is the safest driver?\"* or *\"How many trips did DRV001 complete?\"*\n" +
            "• **Alerts**: *\"Show overspeed alerts\"* or *\"Any GPS disconnects?\"*\n" +
            "• **Reports**: *\"Generate fleet report for September 2026\"*\n\n" +
            "What would you like to explore?";

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("UNKNOWN")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "🚗 VH003 Details", "⚠️ Top Alerts"))
            .build();
    }

    // =========================================================================
    // RESOLVERS & HELPERS
    // =========================================================================

    /** Resolve vehicle from text or session context */
    private Vehicle resolveVehicleFromMessage(String message, Map<String, String> ctx) {
        String lower = message.toLowerCase().trim();

        // 1. Direct code: vh001, vh003, vh12
        Matcher m = Pattern.compile("vh\\d+").matcher(lower);
        if (m.find()) {
            String code = m.group().toUpperCase();
            Optional<Vehicle> v = vehicleRepository.findByCode(code);
            if (v.isPresent()) return v.get();
        }

        // 2. Registration number: MH...
        Matcher regM = Pattern.compile("mh\\d{2}[a-z]{1,2}\\d{4}").matcher(lower);
        if (regM.find()) {
            String reg = regM.group().toUpperCase();
            Optional<Vehicle> v = vehicleRepository.findByRegistrationNumber(reg);
            if (v.isPresent()) return v.get();
        }

        // 3. Make/model or text match
        List<Vehicle> all = vehicleRepository.findAll();
        for (Vehicle v : all) {
            if (v.getMakeModel() != null && lower.contains(v.getMakeModel().toLowerCase())) {
                return v;
            }
            if (v.getRegistrationNumber() != null && lower.contains(v.getRegistrationNumber().toLowerCase())) {
                return v;
            }
        }

        // 4. Pronoun or context reference ("it", "its", "the vehicle", "this vehicle")
        if (lower.matches(".*\\b(it|its|the vehicle|this vehicle)\\b.*") || "VEHICLE".equals(ctx.get("last_subject"))) {
            if (ctx.containsKey("last_vehicle_id")) {
                try {
                    Long id = Long.parseLong(ctx.get("last_vehicle_id"));
                    return vehicleRepository.findById(id).orElse(null);
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    /** Resolve driver from text or session context */
    private Driver resolveDriverFromMessage(String message, Map<String, String> ctx) {
        String lower = message.toLowerCase().trim();

        // 1. Direct DRV code
        Matcher m = Pattern.compile("drv\\d+").matcher(lower);
        if (m.find()) {
            String code = m.group().toUpperCase();
            Optional<Driver> d = driverRepository.findByCode(code);
            if (d.isPresent()) return d.get();
        }

        // 2. Name match
        List<Driver> all = driverRepository.findAll();
        for (Driver d : all) {
            String[] parts = d.getName().toLowerCase().split("\\s+");
            for (String part : parts) {
                if (part.length() >= 3 && lower.contains(part)) {
                    return d;
                }
            }
        }

        // 3. Pronoun reference ("he", "him", "his", "she", "her", "the driver")
        if (lower.matches(".*\\b(he|him|his|she|her|the driver)\\b.*") || "DRIVER".equals(ctx.get("last_subject"))) {
            if (ctx.containsKey("last_driver_id")) {
                try {
                    Long id = Long.parseLong(ctx.get("last_driver_id"));
                    return driverRepository.findById(id).orElse(null);
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    /** Flexible date range parser */
    private DateRange parseDateRange(String text) {
        String lower = text.toLowerCase();
        LocalDate now = LocalDate.now();

        if (lower.contains("today")) {
            return new DateRange(now, now, "Today (" + now + ")");
        }
        if (lower.contains("yesterday")) {
            LocalDate y = now.minusDays(1);
            return new DateRange(y, y, "Yesterday (" + y + ")");
        }
        if (lower.contains("this week")) {
            LocalDate start = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            return new DateRange(start, now, "This Week (" + start + " to " + now + ")");
        }
        if (lower.contains("last week")) {
            LocalDate end = now.with(TemporalAdjusters.previous(java.time.DayOfWeek.SUNDAY));
            LocalDate start = end.minusDays(6);
            return new DateRange(start, end, "Last Week (" + start + " to " + end + ")");
        }
        if (lower.contains("this month")) {
            LocalDate start = now.withDayOfMonth(1);
            return new DateRange(start, now, "This Month (" + start + " to " + now + ")");
        }
        if (lower.contains("last month")) {
            LocalDate lastM = now.minusMonths(1);
            LocalDate start = lastM.withDayOfMonth(1);
            LocalDate end = lastM.withDayOfMonth(lastM.lengthOfMonth());
            return new DateRange(start, end, "Last Month (" + start + " to " + end + ")");
        }

        YearMonth ym = extractYearMonth(lower);
        if (ym != null) {
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            return new DateRange(start, end, ym.getMonth().name() + " " + ym.getYear());
        }

        Matcher ymMat = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(text);
        if (ymMat.find()) {
            try {
                LocalDate d = LocalDate.parse(ymMat.group(1));
                return new DateRange(d, d, d.toString());
            } catch (Exception ignored) {}
        }

        return null;
    }

    /** Extract YearMonth from month name and optional year */
    private YearMonth extractYearMonth(String text) {
        Map<String, Integer> months = Map.ofEntries(
            Map.entry("january", 1), Map.entry("february", 2), Map.entry("march", 3),
            Map.entry("april", 4), Map.entry("may", 5), Map.entry("june", 6),
            Map.entry("july", 7), Map.entry("august", 8), Map.entry("september", 9),
            Map.entry("october", 10), Map.entry("november", 11), Map.entry("december", 12)
        );
        int year = LocalDate.now().getYear();

        Matcher ym = Pattern.compile("(\\d{4})-(\\d{2})").matcher(text);
        if (ym.find()) {
            return YearMonth.of(Integer.parseInt(ym.group(1)), Integer.parseInt(ym.group(2)));
        }

        Matcher yr = Pattern.compile("20(2[0-9])").matcher(text);
        if (yr.find()) year = Integer.parseInt(yr.group());

        for (Map.Entry<String, Integer> entry : months.entrySet()) {
            if (text.contains(entry.getKey())) {
                return YearMonth.of(year, entry.getValue());
            }
        }
        return null;
    }
}
