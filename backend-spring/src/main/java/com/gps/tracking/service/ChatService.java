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
    private final IntentClassifier intentClassifier;

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
        // 2. INTENT CLASSIFICATION VIA NLP ENGINE
        // =========================================================
        IntentClassifier.ClassifiedIntent classified = intentClassifier.classify(message);
        IntentClassifier.Intent intent = classified.getIntent();
        log.info("Classified intent: {} (score={}) for msg: {}", intent, classified.getScore(), message);

        switch (intent) {
            case GREETING:
                return handleGreetingOrHelp(sessionId);

            case COMPARE_DRIVERS:
                return handleCompareDrivers(sessionId, normalized, classified, ctx);

            case DRIVER_RECOMMENDATION:
                return handleDriverRecommendation(sessionId, normalized, ctx);

            case DRIVER_RANKING:
                return handleDriverRanking(sessionId, normalized, classified, ctx);

            case DRIVER_REPORT:
                return handleDriverReportIntent(sessionId, normalized, message, classified, ctx);

            case DRIVER_STATS:
                return handleDriverStats(sessionId, normalized, ctx);

            case VEHICLE_ALERT_ANALYSIS:
                return handleVehicleAlertAnalysis(sessionId, normalized, ctx);

            case VEHICLE_STATS:
                return handleVehicleQueryOrStats(sessionId, normalized, ctx);

            case OVERALL_REPORT:
                return handleOverallReport(sessionId, normalized, classified, ctx);

            case FLEET_REPORT:
                return handleFleetReport(sessionId, normalized, ctx);

            case ALERT_QUERY:
                return handleAlertQuery(sessionId, normalized, ctx);

            case TRIP_QUERY:
                return handleTripQuery(sessionId, normalized, classified, ctx);

            case ENTRY_EXIT:
                return handleEntryExit(sessionId, normalized, ctx);

            case FLEET_SUMMARY:
                return handleFleetSummary(sessionId, normalized, ctx);

            case UNKNOWN:
            default:
                if (normalized.matches(".*(generate|create|download|give me|show|make).*(report).*") || normalized.equals("report") || normalized.equals("reports")) {
                    return handleGeneralReportTrigger(sessionId, normalized, message, ctx);
                }
                if (resolveDriverFromMessage(message, ctx) != null) {
                    return handleDriverStats(sessionId, normalized, ctx);
                }
                if (resolveVehicleFromMessage(message, ctx) != null) {
                    return handleVehicleQueryOrStats(sessionId, normalized, ctx);
                }
                return handleUnknownFallback(sessionId);
        }
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
            "• *\"Recommend 3 drivers for an important trip\"*\n" +
            "• *\"Which driver is best for a long-distance route?\"*\n" +
            "• *\"How many trips did DRV001 complete?\"*\n" +
            "• *\"Show overspeed alerts\"*\n" +
            "• *\"Generate fleet report for September 2026\"*";

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("GREETING")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "🌟 Recommend Drivers", "🚗 Vehicles", "⚠️ Top Alerts", "📄 Reports"))
            .build();
    }

    /** 3. FLEET SUMMARY */
    private ChatResponseDTO handleFleetSummary(String sessionId, String normalized, Map<String, String> ctx) {
        long totalVehicles = vehicleRepository.count();
        long activeVehicles = vehicleRepository.findByActiveTrue().size();
        long totalDrivers = driverRepository.count();
        long activeDrivers = driverRepository.findByActiveTrue().size();

        DateRange dr = parseDateRange(normalized);
        boolean isToday = normalized.contains("today") || normalized.contains("operational") || normalized.contains("health") || normalized.contains("status");

        LocalDateTime from, to;
        String periodLabel;

        if (dr != null) {
            from = dr.startDateTime();
            to = dr.endDateTime();
            periodLabel = dr.label;
        } else if (isToday) {
            from = LocalDate.now().atStartOfDay();
            to = LocalDate.now().plusDays(1).atStartOfDay();
            periodLabel = "Today (" + LocalDate.now() + ")";
        } else {
            from = null;
            to = null;
            periodLabel = "Overall (All-Time)";
        }

        long tripCount = from != null ? tripRepository.countInDateRange(from, to) : tripRepository.count();
        Double totalDistance = from != null ? tripRepository.sumDistanceInDateRange(from, to) : tripRepository.sumTotalDistance();
        long totalAlerts = from != null ? alertRepository.countInDateRange(from, to) : alertRepository.count();
        List<Object[]> alertTypes = from != null ? alertRepository.countByTypeInDateRange(from, to) : alertRepository.countAllGroupedByType();
        long openAlerts = alertRepository.countOpenAlerts();
        long critAlerts = from != null ? alertRepository.countBySeverityInDateRange("CRITICAL", from, to) : alertRepository.countBySeverity("CRITICAL");
        long vehiclesEntered = from != null ? tripRepository.countDistinctVehiclesWithTripStarted(from, to) : 0;
        long vehiclesExited = from != null ? tripRepository.countDistinctVehiclesWithTripCompleted(from, to) : 0;

        Map<String, Long> alertMap = new LinkedHashMap<>();
        for (Object[] row : alertTypes) {
            alertMap.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        long overspeed = alertMap.getOrDefault("Overspeed Alert", 0L);
        long harshBraking = alertMap.getOrDefault("Harsh Braking Alert", 0L);
        long disconnect = alertMap.getOrDefault("GPS Disconnect Alert", 0L);
        long nightDriving = alertMap.getOrDefault("Night Driving Alert", 0L);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 **Fleet Summary (%s)**:\n\n", periodLabel));
        if (from != null) {
            sb.append(String.format("• **Vehicles entered:** %,d\n", vehiclesEntered));
            sb.append(String.format("• **Vehicles exited:** %,d\n", vehiclesExited));
            sb.append(String.format("• **Trips completed:** %,d\n", tripCount));
            sb.append(String.format("• **Total distance covered:** %,.1f km\n", totalDistance != null ? totalDistance : 0.0));
            sb.append(String.format("• **Alerts generated:** %,d\n", totalAlerts));
            sb.append(String.format("• **Critical alerts:** %,d\n", critAlerts));
            sb.append(String.format("• **Open alerts:** %,d\n\n", openAlerts));
        }

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
        if (normalized.contains("inactive")) {
            List<Vehicle> inactive = vehicleRepository.findByActiveFalse();
            if (inactive.isEmpty()) {
                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("VEHICLE_STATS")
                    .message("✅ All vehicles in the fleet are currently **active** (0 inactive vehicles).")
                    .quickActions(List.of("📊 Fleet Summary", "🚗 Vehicle Alerts"))
                    .build();
            }
            StringBuilder sb = new StringBuilder("🚗 **Inactive Vehicles in Fleet:**\n\n");
            for (Vehicle inv : inactive) {
                sb.append(String.format("• Vehicle **%s** (%s) — %s (%s)\n",
                    inv.getCode(), inv.getRegistrationNumber(), inv.getMakeModel(), inv.getVehicleType()));
            }
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("VEHICLE_STATS")
                .message(sb.toString())
                .quickActions(List.of("📊 Fleet Summary", "🚗 Vehicle Alerts"))
                .build();
        }

        if (normalized.contains("highest speed") || normalized.contains("fastest vehicle") || normalized.contains("max speed") || normalized.contains("top speed")) {
            Optional<Telemetry> maxTelem = telemetryRepository.findTopByOrderBySpeedKmphDesc();
            if (maxTelem.isPresent()) {
                Telemetry t = maxTelem.get();
                String vCode = t.getVehicle() != null ? t.getVehicle().getCode() : "Unknown";
                String vReg = t.getVehicle() != null ? t.getVehicle().getRegistrationNumber() : "N/A";
                double spd = t.getSpeedKmph() != null ? t.getSpeedKmph().doubleValue() : 0.0;
                String timeStr = t.getRecordedAt() != null ? t.getRecordedAt().toString().replace('T', ' ') : "recorded date";
                String loc = t.getRoadName() != null ? " on " + t.getRoadName() : "";

                String msg = String.format("🏎️ Vehicle **%s** (%s) recorded the highest speed in the fleet at **%.1f km/h**%s (%s).",
                    vCode, vReg, spd, loc, timeStr);

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("VEHICLE_STATS")
                    .message(msg)
                    .quickActions(List.of("Details for " + vCode, "⚠️ Overspeed Alerts", "📊 Fleet Summary"))
                    .build();
            }
        }

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
        return handleDriverRanking(sessionId, normalized, null, ctx);
    }

    private ChatResponseDTO handleDriverRanking(String sessionId, String normalized,
                                               IntentClassifier.ClassifiedIntent classified,
                                               Map<String, String> ctx) {
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
        if (rankings.isEmpty() && dr == null) {
            from = LocalDate.of(2020, 1, 1);
            rankings = driverService.getDriverRanking(from, to);
            periodLabel = "All-Time";
        }

        boolean sortByMostAlerts = normalized.matches(".*(most alert|highest alert|most problem|most overspeed|worst).*");
        if (sortByMostAlerts) {
            rankings = rankings.stream()
                .sorted(Comparator.comparingLong((DriverRankingDTO r) -> r.getTotalAlerts() != null ? r.getTotalAlerts() : 0).reversed())
                .collect(Collectors.toList());
        }

        // Check if user is asking for the single safest / top driver
        boolean askingForSingleSafest = !sortByMostAlerts && (normalized.contains("who is safest") || normalized.contains("who is the safest") ||
            normalized.contains("which driver is safest") || normalized.contains("who has the best safety") ||
            normalized.contains("who has the highest safety") || normalized.contains("who has the lowest risk") ||
            normalized.contains("cleanest record") || normalized.contains("safest driver") ||
            (normalized.contains("top driver") && !normalized.contains("drivers")) ||
            (normalized.contains("best driver") && !normalized.contains("drivers")))
            && !normalized.contains("top 5") && !normalized.contains("top 3") && !normalized.contains("top 10")
            && !normalized.contains("rank") && !normalized.contains("leaderboard");

        if (askingForSingleSafest && !rankings.isEmpty()) {
            DriverRankingDTO top = rankings.get(0);
            double score = top.getSafetyScore() != null ? top.getSafetyScore().doubleValue() : 0.0;
            String singleMsg = String.format("The safest driver is **%s (%s)** with a safety score of **%.1f**, currently ranked **#1**.\n\n" +
                "• **Completed Trips:** %,d\n" +
                "• **Distance:** %,.1f km\n" +
                "• **Total Alerts:** %,d (%d overspeed, %d harsh braking)",
                top.getDriverName(), top.getDriverCode(), score,
                top.getTripCount() != null ? top.getTripCount() : 0,
                top.getTotalDistanceKm() != null ? top.getTotalDistanceKm().doubleValue() : 0.0,
                top.getTotalAlerts() != null ? top.getTotalAlerts() : 0,
                top.getOverspeedEvents() != null ? top.getOverspeedEvents() : 0,
                top.getHarshBrakingEvents() != null ? top.getHarshBrakingEvents() : 0);

            ctx.put("last_subject", "DRIVER");
            ctx.put("last_driver_id", String.valueOf(top.getDriverId()));
            ctx.put("last_driver_name", top.getDriverName());
            ctx.put("last_driver_code", top.getDriverCode());

            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("DRIVER_RANKING")
                .message(singleMsg)
                .quickActions(List.of("🏆 Top 5 Drivers", "🌟 Recommend Drivers", "📊 Fleet Summary", "📄 " + top.getDriverCode() + " Report"))
                .build();
        }

        int limit = (classified != null && classified.getRankingLimit() > 0) ? classified.getRankingLimit() : 5;
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
        if (sortByMostAlerts) {
            table.append(String.format("⚠️ **Drivers with the Most Alerts (%s)**\n\n", periodLabel));
        } else {
            table.append(String.format("🏆 **Top %d Safest Drivers (%s):**\n\n", topN.size(), periodLabel));
            int rIdx = 1;
            for (DriverRankingDTO r : topN) {
                table.append(String.format("%d. **%s** (%s) — **%.1f**\n",
                    rIdx++, r.getDriverName(), r.getDriverCode(),
                    r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0));
            }
            table.append("\n");
        }
        table.append("| Rank | Driver | Safety Score | Trips | Distance | Alerts | Overspeed | Harsh Braking |\n");
        table.append("|:---:|:---|:---:|---:|---:|---:|---:|---:|\n");

        int displayRank = 1;
        for (DriverRankingDTO r : topN) {
            table.append(String.format("| %d | **%s** (%s) | **%.1f** | %,d | %,.1f km | %,d | %,d | %,d |\n",
                displayRank++, r.getDriverName(), r.getDriverCode(),
                r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0,
                r.getTripCount() != null ? r.getTripCount() : 0,
                r.getTotalDistanceKm() != null ? r.getTotalDistanceKm().doubleValue() : 0.0,
                r.getTotalAlerts() != null ? r.getTotalAlerts() : 0,
                r.getOverspeedEvents() != null ? r.getOverspeedEvents() : 0,
                r.getHarshBrakingEvents() != null ? r.getHarshBrakingEvents() : 0));
        }

        DriverRankingDTO best = topN.get(0);
        if (sortByMostAlerts) {
            table.append(String.format("\n⚠️ **%s** has accumulated the most alerts (%d alerts across %,d trips). Consider reviewing driving patterns or safety coaching.",
                best.getDriverName(), best.getTotalAlerts() != null ? best.getTotalAlerts() : 0, best.getTripCount() != null ? best.getTripCount() : 0));
        } else {
            table.append(String.format("\n🌟 **%s** is currently the safest driver with a score of **%.1f/100** across %,d trips.",
                best.getDriverName(), best.getSafetyScore(), best.getTripCount()));
        }

        ctx.put("last_subject", "DRIVER");
        ctx.put("last_driver_id", String.valueOf(best.getDriverId()));
        ctx.put("last_driver_name", best.getDriverName());
        ctx.put("last_driver_code", best.getDriverCode());

        List<String> qa = new ArrayList<>();
        qa.add("Show details for " + best.getDriverName());
        qa.add("📊 Fleet Summary");
        qa.add("🌟 Recommend Drivers");

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("DRIVER_RANKING")
            .message(table.toString())
            .data(topN)
            .quickActions(qa)
            .build();
    }

    // =========================================================================
    // DRIVER RECOMMENDATION INTENT DETECTION HELPER
    // =========================================================================

    /**
     * Returns true when the user's message is asking for driver recommendations,
     * suggestions, or "who should I choose/assign" type questions.
     * Deliberately narrow enough NOT to fire on vehicle-focused or alert-only queries.
     */
    private boolean isDriverRecommendationIntent(String n) {
        // Exclude queries clearly asking about vehicles
        if (n.contains("vehicle") || n.contains("truck") || n.contains("car") || n.matches(".*vh\\d+.*")) {
            return false;
        }

        // Strong direct signals: recommend / suggestion
        if (n.matches(".*(recommend|recommendation|suggest|suggestion).*(driver|drv|who)?.*")) return true;
        if (n.matches(".*(driver|drv|who).*(recommend|recommendation|suggest|suggestion).*")) return true;

        // "give me / show me / list ... drivers"
        if (n.matches(".*(give me|show me|list|tell me|get).*(recommend|top driver|best driver|good driver|reliable driver|safest driver|leading driver).*")) return true;
        if (n.matches(".*(give me|show me|list|get).*(\\d+).*(driver|recommend).*")) return true;
        if (n.matches(".*(top|best|recommend)\\s*\\d+\\s*drivers?.*")) return true;

        // "who / which driver should I choose / assign / pick / select / trust"
        if (n.matches(".*(who|which driver).*(should i|would you|can i|do you|to).*(choose|assign|pick|select|trust|recommend|use|take).*")) return true;
        if (n.matches(".*who\\s+should\\s+i\\s+(choose|assign|pick|select|trust|recommend|use).*")) return true;

        // "who is / which driver is best for long trip / route / distance"
        if (n.matches(".*(who|which driver|best driver).*(best for|suited for|good for|ideal for|great for).*(long|trip|distance|route|important|assignment).*")) return true;
        if (n.matches(".*(who|which driver).*(for a long|for long|on long).*(trip|drive|route).*")) return true;

        // "who is / which drivers are most reliable / safest / best / top"
        if (n.matches(".*(who|which driver|which drivers)\\s+(are|is|have been)\\s+(the\\s+)?(top|most reliable|safest|best|good|trustworthy|most experienced).*")) return true;
        if (n.matches(".*(who|which driver).*(most reliable|most trustworthy|best performing|best performance|most experienced).*")) return true;
        if (n.matches(".*who\\s+(are|is)\\s+(the\\s+)?best\\s+(drivers?|in the fleet).*")) return true;
        if (n.matches(".*who\\s+(are|is)\\s+(the\\s+)?safest\\s+drivers?.*")) return true;
        if (n.matches(".*who\\s+is\\s+(the\\s+)?safest\\s+driver.*")) return true;
        if (n.matches(".*which\\s+drivers?\\s+(are|is)\\s+(the\\s+)?(top|safest|best|good|reliable|most reliable).*")) return true;
        if (n.matches(".*(which drivers? (have|has|had) (the )?best (performance|record|safety|score|results?)).*")) return true;
        if (n.matches(".*(which drivers? (have|has) performed (the )?best).*")) return true;
        if (n.matches(".*(top performing|best performing)\\s+drivers?.*")) return true;
        if (n.matches(".*who\\s+are\\s+my\\s+top.*")) return true;

        // "who / which driver has the most trips / fewest alerts / highest score / best safety record"
        if (n.matches(".*(who|which driver|which drivers)\\s+(has|have|got|with)\\s+(the\\s+)?(most trips?|highest trips?|fewest alerts?|least alerts?|lowest alerts?|best safety record|highest safety score).*")) return true;
        if (n.matches(".*(driver|drivers).*(most trips?|highest trips?|fewest alerts?|least alerts?|safest record).*")) return true;
        if (n.matches(".*who\\s+has\\s+(the\\s+)?(most trips?|fewest alerts?|best safety record|highest safety score).*")) return true;

        // "recommend a driver", "recommend some drivers", "best overall driver"
        if (n.matches(".*(recommend a driver|recommend some driver|recommend the best driver|recommend good driver|recommend top driver).*")) return true;
        if (n.matches(".*(best overall driver|top performing driver|top driver.*(recommend|suggest)|recommend.*top driver).*")) return true;

        // UI quick action button strings
        if (n.contains("recommend driver") || n.contains("recommend drivers")) return true;
        if (n.matches(".*(best overall|fewest alert|best for long trip|long trip|long-distance trip).*")) return true;
        if (n.contains("\ud83c\udf1f") || n.contains("\ud83c\udfc6") || n.contains("\ud83d\udee3") || n.contains("\ud83d\udd14")) return true;

        return false;
    }

    // =========================================================================
    // DRIVER RECOMMENDATION HANDLER
    // =========================================================================

    /** DRIVER_RECOMMENDATION intent handler */
    private ChatResponseDTO handleDriverRecommendation(String sessionId, String normalized, Map<String, String> ctx) {
        // Determine how many to recommend
        int limit = extractRecommendationCount(normalized);

        // Determine sub-criteria from message
        String criteria = detectRecommendationCriteria(normalized);

        // Fetch all-time rankings (use full date range for max data coverage)
        LocalDate from = LocalDate.of(2020, 1, 1);
        LocalDate to = LocalDate.now();
        List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);

        if (rankings.isEmpty()) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("DRIVER_RECOMMENDATION")
                .message("I couldn't find any driver data to make a recommendation. Please ensure the fleet database has trip and alert history.")
                .quickActions(List.of("📊 Fleet Summary", "🏆 Driver Rankings"))
                .build();
        }

        // Sort by the appropriate sub-criteria
        List<DriverRankingDTO> sorted = sortByRecommendationCriteria(rankings, criteria);

        // Only recommend drivers with meaningful data (at least 10 trips)
        List<DriverRankingDTO> qualified = sorted.stream()
            .filter(r -> r.getTripCount() != null && r.getTripCount() >= 10)
            .collect(Collectors.toList());

        // If filter is too strict, relax it
        if (qualified.isEmpty()) qualified = sorted;

        List<DriverRankingDTO> topN = qualified.stream().limit(limit).collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        String criteriaLabel = getCriteriaLabel(criteria);
        sb.append(String.format("🌟 **Driver Recommendation (%s)**\n\n", criteriaLabel));
        sb.append("Based on actual fleet data, here are my recommendations:\n\n");

        int displayRank = 1;
        for (DriverRankingDTO r : topN) {
            double score = r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0;
            long trips = r.getTripCount() != null ? r.getTripCount() : 0;
            double dist = r.getTotalDistanceKm() != null ? r.getTotalDistanceKm().doubleValue() : 0.0;
            long alerts = r.getTotalAlerts() != null ? r.getTotalAlerts() : 0;
            long overspeed = r.getOverspeedEvents() != null ? r.getOverspeedEvents() : 0;
            long harshBraking = r.getHarshBrakingEvents() != null ? r.getHarshBrakingEvents() : 0;

            String recommendation = buildRecommendationNote(r, criteria, displayRank);

            sb.append(String.format("**%d. %s (%s)**\n", displayRank, r.getDriverName(), r.getDriverCode()));
            sb.append(String.format("   • Safety Score: **%.1f / 100**\n", score));
            sb.append(String.format("   • Trips: **%,d** | Distance: **%,.1f km**\n", trips, dist));
            sb.append(String.format("   • Overspeed Alerts: **%,d** | Harsh Braking: **%,d** | Total Alerts: **%,d**\n", overspeed, harshBraking, alerts));
            sb.append(String.format("   • ✅ *%s*\n\n", recommendation));
            displayRank++;
        }

        // Overall summary
        DriverRankingDTO best = topN.get(0);
        sb.append(String.format(
            "🏆 **My top pick: %s (%s)** — %s",
            best.getDriverName(), best.getDriverCode(),
            buildRecommendationNote(best, criteria, 1)));

        ctx.put("last_subject", "DRIVER");
        ctx.put("last_driver_id", String.valueOf(best.getDriverId()));
        ctx.put("last_driver_name", best.getDriverName());
        ctx.put("last_driver_code", best.getDriverCode());

        List<String> actions = new ArrayList<>();
        actions.add("Tell me more about " + best.getDriverName());
        actions.add("🏆 Best Overall");
        actions.add("🛡️ Safest Driver");
        actions.add("🛣️ Best for Long Trips");
        actions.add("🔔 Fewest Alerts");

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("DRIVER_RECOMMENDATION")
            .message(sb.toString())
            .data(topN)
            .quickActions(actions)
            .build();
    }

    /** Extract the requested count from "give me 3 drivers", "top 5", etc. */
    private int extractRecommendationCount(String n) {
        // Match "top 3", "3 drivers", "recommend 5", etc.
        Matcher m = Pattern.compile("\\b(\\d{1,2})\\s*(?:drivers?|recommended|recommendations?)\\b|\\b(?:top|best|recommend|show me|give me)\\s*(\\d{1,2})\\b").matcher(n);
        if (m.find()) {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            if (val != null) {
                int count = Integer.parseInt(val);
                if (count >= 1 && count <= 17) return count;
            }
        }
        // Default: if singular ("recommend a driver", "who should I choose", "safest driver") return 1; else 3
        if (n.matches(".*(\\ba driver\\b|\\bone driver\\b|\\bwho should i\\b|\\bwho would you\\b|\\bwho is the safest\\b|\\bwho is the best\\b|\\bwhich driver has\\b|\\bwho has the\\b).*")) return 1;
        return 3;
    }

    /** Detect sub-criteria from the user's request */
    private String detectRecommendationCriteria(String n) {
        if (n.matches(".*(long.?trip|long.?distance|long.?route|high.?distance|most.?km|experienced).*")) return "LONG_TRIP";
        if (n.matches(".*(fewest alert|least alert|low alert|minimum alert|no alert|lowest alert).*")) return "FEWEST_ALERTS";
        if (n.matches(".*(most trips?|highest trips?|trip count|most experience|veteran).*")) return "MOST_TRIPS";
        if (n.matches(".*(safest|safety score|safe driver|low speed|safe record|safety record).*")) return "SAFETY";
        // default: overall composite score
        return "OVERALL";
    }

    /** Sort rankings by the requested criteria */
    private List<DriverRankingDTO> sortByRecommendationCriteria(List<DriverRankingDTO> rankings, String criteria) {
        switch (criteria) {
            case "LONG_TRIP":
                // Best for long trips: high distance AND good safety score
                return rankings.stream()
                    .sorted(Comparator
                        .comparingDouble((DriverRankingDTO r) -> r.getTotalDistanceKm() != null ? r.getTotalDistanceKm().doubleValue() : 0.0)
                        .reversed()
                        .thenComparingDouble(r -> r.getSafetyScore() != null ? -r.getSafetyScore().doubleValue() : 0.0))
                    .collect(Collectors.toList());
            case "FEWEST_ALERTS":
                // Fewest total alerts (normalized by trips to avoid low-trip bias)
                return rankings.stream()
                    .filter(r -> r.getTripCount() != null && r.getTripCount() > 0)
                    .sorted(Comparator.comparingDouble((DriverRankingDTO r) -> {
                        double alerts = r.getTotalAlerts() != null ? r.getTotalAlerts() : 0;
                        double trips = r.getTripCount() != null && r.getTripCount() > 0 ? r.getTripCount() : 1;
                        return alerts / trips; // alerts per trip (lower = better)
                    }))
                    .collect(Collectors.toList());
            case "MOST_TRIPS":
                return rankings.stream()
                    .sorted(Comparator.comparingInt((DriverRankingDTO r) -> r.getTripCount() != null ? r.getTripCount() : 0).reversed())
                    .collect(Collectors.toList());
            case "SAFETY":
                return rankings.stream()
                    .sorted(Comparator.comparingDouble((DriverRankingDTO r) -> r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0).reversed())
                    .collect(Collectors.toList());
            default: // OVERALL: composite (safety score is already composite in the ranking formula)
                return rankings; // already sorted by safety score (composite formula) from getDriverRanking
        }
    }

    /** Human-readable criteria label */
    private String getCriteriaLabel(String criteria) {
        switch (criteria) {
            case "LONG_TRIP": return "Best for Long-Distance Trips";
            case "FEWEST_ALERTS": return "Fewest Alerts (Safest Record)";
            case "MOST_TRIPS": return "Most Experienced (Highest Trip Count)";
            case "SAFETY": return "Best Safety Score";
            default: return "Best Overall Performance";
        }
    }

    /** Build a short recommendation note for a driver */
    private String buildRecommendationNote(DriverRankingDTO r, String criteria, int rank) {
        double score = r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0;
        long trips = r.getTripCount() != null ? r.getTripCount() : 0;
        double dist = r.getTotalDistanceKm() != null ? r.getTotalDistanceKm().doubleValue() : 0.0;
        long alerts = r.getTotalAlerts() != null ? r.getTotalAlerts() : 0;

        switch (criteria) {
            case "LONG_TRIP":
                return String.format("Highest fleet distance (%.0f km) with a %.1f safety score — ideal for long routes.", dist, score);
            case "FEWEST_ALERTS":
                double apr = trips > 0 ? (double) alerts / trips : 0;
                return String.format("Only %.2f alerts per trip over %,d trips — excellent safety discipline.", apr, trips);
            case "MOST_TRIPS":
                return String.format("%,d completed trips with %.1f safety score — highly experienced.", trips, score);
            case "SAFETY":
                return String.format("Safety score %.1f/100 across %,d trips — top safety performer.", score, trips);
            default:
                if (rank == 1) return String.format("Composite safety score %.1f/100 across %,d trips and %.0f km — overall top pick.", score, trips, dist);
                return String.format("Safety score %.1f/100 | %,d trips | %.0f km driven.", score, trips, dist);
        }
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

        if (tripCount == 0 && dr == null) {
            tripCount = tripRepository.countByDriverId(driver.getId());
            List<DriverRankingDTO> allRankings = driverService.getDriverRanking(LocalDate.of(2020, 1, 1), LocalDate.now());
            Optional<DriverRankingDTO> rOpt = allRankings.stream().filter(r -> r.getDriverId().equals(driver.getId())).findFirst();
            if (rOpt.isPresent()) {
                DriverRankingDTO r = rOpt.get();
                if (r.getTotalDistanceKm() != null && r.getTotalDistanceKm().doubleValue() > 0) dist = r.getTotalDistanceKm().doubleValue();
                if (r.getOverspeedEvents() != null) overspeed = r.getOverspeedEvents();
                if (r.getSafetyScore() != null) avgScore = OptionalDouble.of(r.getSafetyScore().doubleValue());
                periodLabel = "All-Time";
            }
        }

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
        return handleDriverReportIntent(sessionId, normalized, original, null, ctx);
    }

    private ChatResponseDTO handleDriverReportIntent(String sessionId, String normalized, String original,
                                                    IntentClassifier.ClassifiedIntent classified,
                                                    Map<String, String> ctx) {
        Driver driver = null;
        if (classified != null && classified.getDriverCodeHint() != null) {
            driver = driverRepository.findByCode(classified.getDriverCodeHint()).orElse(null);
        }
        if (driver == null && classified != null && classified.getDriverNameHint() != null) {
            String nameHint = classified.getDriverNameHint().toLowerCase();
            driver = driverRepository.findAll().stream()
                .filter(d -> d.getName().toLowerCase().contains(nameHint))
                .findFirst().orElse(null);
        }
        if (driver == null) {
            driver = resolveDriverFromMessage(normalized, ctx);
        }
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
        if (classified != null && classified.getPeriod() != null) {
            switch (classified.getPeriod()) {
                case DAILY: reportType = "DAILY"; break;
                case WEEKLY: reportType = "WEEKLY"; break;
                case CUSTOM: reportType = "CUSTOM"; break;
                default: reportType = "MONTHLY"; break;
            }
        } else if (normalized.contains("daily") || normalized.contains("today")) {
            reportType = "DAILY";
        } else if (normalized.contains("weekly") || normalized.contains("week")) {
            reportType = "WEEKLY";
        }

        String intentName = "DRIVER_" + reportType + "_REPORT";

        DateRange dr = parseDateRange(normalized);
        LocalDate startDate, endDate;

        if (classified != null && classified.getDateFrom() != null) {
            startDate = classified.getDateFrom();
            endDate = classified.getDateTo();
        } else if (dr != null) {
            startDate = dr.startDate;
            endDate = dr.endDate;
        } else if (classified != null && classified.getYearMonth() != null) {
            startDate = classified.getYearMonth().atDay(1);
            endDate = classified.getYearMonth().atEndOfMonth();
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
                    .sessionId(sessionId).intent(intentName)
                    .message("For which month would you like **" + driver.getName() + "'s** report?")
                    .askingForClarification(true)
                    .clarificationQuestion("Please specify the month.")
                    .quickActions(List.of("September 2026", "October 2026", "August 2026"))
                    .build();
            }
        } else if ("DAILY".equals(reportType)) {
            startDate = LocalDate.now();
            endDate = LocalDate.now();
        } else if ("WEEKLY".equals(reportType)) {
            startDate = LocalDate.now().minusDays(7);
            endDate = LocalDate.now();
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = LocalDate.now();
        }

        // If the query is an inquiry about performance without asking directly for PDF/download
        boolean wantsPdfDirectly = normalized.contains("pdf") || normalized.contains("download") || normalized.contains("generate");
        if (!wantsPdfDirectly && (normalized.contains("performance") || normalized.contains("how did") || normalized.contains("how was"))) {
            return buildDriverPerformanceSummaryResponse(sessionId, driver, reportType, intentName, startDate, endDate, ctx);
        }

        return generateDriverReportResponse(sessionId, driver, reportType, intentName, startDate, endDate, ctx);
    }

    private ChatResponseDTO buildDriverPerformanceSummaryResponse(String sessionId, Driver driver, String reportType,
                                                                   String intentName, LocalDate startDate, LocalDate endDate,
                                                                   Map<String, String> ctx) {
        long trips = tripRepository.countByDriverIdAndDateRange(driver.getId(), startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        Double dist = tripRepository.sumDistanceByDriverAndDateRange(driver.getId(), startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        List<DriverRankingDTO> rankings = driverService.getDriverRanking(startDate, endDate);
        DriverRankingDTO rankInfo = rankings.stream().filter(r -> r.getDriverId().equals(driver.getId())).findFirst().orElse(null);

        double score = rankInfo != null && rankInfo.getSafetyScore() != null ? rankInfo.getSafetyScore().doubleValue() : 85.0;
        long overspeed = rankInfo != null && rankInfo.getOverspeedEvents() != null ? rankInfo.getOverspeedEvents() : 0;
        long harshBraking = rankInfo != null && rankInfo.getHarshBrakingEvents() != null ? rankInfo.getHarshBrakingEvents() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 **%s Performance for %s (%s)**:\n\n", reportType, driver.getName(), driver.getCode()));
        sb.append(String.format("Period: **%s to %s**\n\n", startDate, endDate));
        sb.append(String.format("• **Safety Score:** **%.1f / 100**\n", score));
        sb.append(String.format("• **Completed Trips:** %,d\n", trips));
        sb.append(String.format("• **Distance Covered:** %,.1f km\n", dist != null ? dist : 0.0));
        sb.append(String.format("• **Overspeed Events:** %,d\n", overspeed));
        sb.append(String.format("• **Harsh Braking Events:** %,d\n\n", harshBraking));
        sb.append("📄 *Would you like me to generate the official PDF report for this period?*");

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent(intentName)
            .message(sb.toString())
            .quickActions(List.of("📄 Generate " + driver.getCode() + " PDF", "🏆 Driver Rankings", "📊 Fleet Summary"))
            .build();
    }

    private ChatResponseDTO generateDriverReportResponse(String sessionId, Driver driver,
                                                          String reportType, LocalDate startDate,
                                                          LocalDate endDate, Map<String, String> ctx) {
        return generateDriverReportResponse(sessionId, driver, reportType, "DRIVER_" + reportType + "_REPORT", startDate, endDate, ctx);
    }

    private ChatResponseDTO generateDriverReportResponse(String sessionId, Driver driver,
                                                          String reportType, String intentName,
                                                          LocalDate startDate, LocalDate endDate, Map<String, String> ctx) {
        try {
            ReportRequestDTO req = new ReportRequestDTO();
            req.setReportType("CUSTOM".equals(reportType) ? "MONTHLY" : reportType);
            req.setDriverId(driver.getId());
            req.setStartDate(startDate);
            req.setEndDate(endDate);

            ReportResponseDTO report = reportService.generateDriverReport(req);

            return ChatResponseDTO.builder()
                .sessionId(sessionId).intent(intentName)
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
                .sessionId(sessionId).intent(intentName)
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
        LocalDate now = LocalDate.now();
        DateRange dr = parseDateRange(normalized);
        LocalDate from = dr != null ? dr.startDate : LocalDate.of(2020, 1, 1);
        LocalDate to = dr != null ? dr.endDate : now;

        // 1. "Which driver has the most violations?" / "most alerts"
        if (normalized.contains("driver") && (normalized.contains("most violation") || normalized.contains("most alert") || normalized.contains("highest alert"))) {
            List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);
            if (!rankings.isEmpty()) {
                DriverRankingDTO worst = rankings.stream()
                    .max(Comparator.comparingLong(r -> r.getTotalAlerts() != null ? r.getTotalAlerts() : 0))
                    .orElse(rankings.get(0));

                String msg = String.format("⚠️ Driver **%s (%s)** has the highest number of violations with **%,d total alerts** (%,d overspeed, %,d harsh braking) across %,d trips. Safety Score: **%.1f**.",
                    worst.getDriverName(), worst.getDriverCode(),
                    worst.getTotalAlerts() != null ? worst.getTotalAlerts() : 0,
                    worst.getOverspeedEvents() != null ? worst.getOverspeedEvents() : 0,
                    worst.getHarshBrakingEvents() != null ? worst.getHarshBrakingEvents() : 0,
                    worst.getTripCount() != null ? worst.getTripCount() : 0,
                    worst.getSafetyScore() != null ? worst.getSafetyScore().doubleValue() : 0.0);

                ctx.put("last_subject", "DRIVER");
                ctx.put("last_driver_id", String.valueOf(worst.getDriverId()));
                ctx.put("last_driver_name", worst.getDriverName());
                ctx.put("last_driver_code", worst.getDriverCode());

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("ALERT_QUERY")
                    .message(msg)
                    .quickActions(List.of("Details for " + worst.getDriverCode(), "🏆 Safest Driver", "📊 Fleet Summary"))
                    .build();
            }
        }

        // 2. "Which driver needs training?" / "poor performance" / "needs improvement"
        if (normalized.contains("training") || normalized.contains("poor performance") || normalized.contains("improvement") || normalized.contains("worst driver")) {
            List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);
            if (!rankings.isEmpty()) {
                DriverRankingDTO lowest = rankings.stream()
                    .min(Comparator.comparingDouble(r -> r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 100.0))
                    .orElse(rankings.get(rankings.size() - 1));

                String msg = String.format("⚠️ Driver **%s (%s)** has the lowest safety score (**%.1f / 100**) with **%,d alerts** (%d overspeed, %d harsh braking). This driver would benefit most from safety coaching and driver refresher training.",
                    lowest.getDriverName(), lowest.getDriverCode(),
                    lowest.getSafetyScore() != null ? lowest.getSafetyScore().doubleValue() : 0.0,
                    lowest.getTotalAlerts() != null ? lowest.getTotalAlerts() : 0,
                    lowest.getOverspeedEvents() != null ? lowest.getOverspeedEvents() : 0,
                    lowest.getHarshBrakingEvents() != null ? lowest.getHarshBrakingEvents() : 0);

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("ALERT_QUERY")
                    .message(msg)
                    .quickActions(List.of("Details for " + lowest.getDriverCode(), "🏆 Safest Driver", "📊 Fleet Summary"))
                    .build();
            }
        }

        // 3. "Which drivers have repeated harsh braking?"
        if (normalized.contains("harsh braking") && (normalized.contains("driver") || normalized.contains("repeated") || normalized.contains("who"))) {
            List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);
            List<DriverRankingDTO> harshDrivers = rankings.stream()
                .filter(r -> r.getHarshBrakingEvents() != null && r.getHarshBrakingEvents() > 0)
                .sorted(Comparator.comparingLong((DriverRankingDTO r) -> r.getHarshBrakingEvents()).reversed())
                .limit(5)
                .collect(Collectors.toList());

            if (!harshDrivers.isEmpty()) {
                StringBuilder sb = new StringBuilder("⚠️ **Drivers with Repeated Harsh Braking Events:**\n\n");
                int rank = 1;
                for (DriverRankingDTO r : harshDrivers) {
                    sb.append(String.format("%d. **%s** (%s) — **%,d harsh braking events** (Safety score: %.1f)\n",
                        rank++, r.getDriverName(), r.getDriverCode(), r.getHarshBrakingEvents(),
                        r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0));
                }
                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("ALERT_QUERY")
                    .message(sb.toString())
                    .quickActions(List.of("🏆 Safest Driver", "📊 Fleet Summary", "🚗 Vehicle Alerts"))
                    .build();
            }
        }

        // 4. "Which drivers had overspeed violations?"
        if (normalized.contains("overspeed") && (normalized.contains("driver") || normalized.contains("who") || normalized.contains("violations"))) {
            List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);
            List<DriverRankingDTO> overspeedDrivers = rankings.stream()
                .filter(r -> r.getOverspeedEvents() != null && r.getOverspeedEvents() > 0)
                .sorted(Comparator.comparingLong((DriverRankingDTO r) -> r.getOverspeedEvents()).reversed())
                .limit(5)
                .collect(Collectors.toList());

            if (!overspeedDrivers.isEmpty()) {
                StringBuilder sb = new StringBuilder("🚨 **Drivers with Overspeed Violations:**\n\n");
                int rank = 1;
                for (DriverRankingDTO r : overspeedDrivers) {
                    sb.append(String.format("%d. **%s** (%s) — **%,d overspeed events** (Safety score: %.1f)\n",
                        rank++, r.getDriverName(), r.getDriverCode(), r.getOverspeedEvents(),
                        r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0));
                }
                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("ALERT_QUERY")
                    .message(sb.toString())
                    .quickActions(List.of("🏆 Safest Driver", "📊 Fleet Summary"))
                    .build();
            }
        }

        // 5. "What is the most common alert type?"
        if (normalized.contains("most common alert") || normalized.contains("common violation")) {
            List<Object[]> allTypes = alertRepository.countAllGroupedByType();
            if (!allTypes.isEmpty()) {
                Object[] topType = allTypes.get(0);
                long topCount = ((Number) topType[1]).longValue();
                long total = alertRepository.count();
                double pct = total > 0 ? (topCount * 100.0) / total : 0.0;

                String msg = String.format("⚠️ The most common alert type across the fleet is **%s** with **%,d occurrences** (representing **%.1f%%** of all %,d total fleet alerts).",
                    topType[0], topCount, pct, total);

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("ALERT_QUERY")
                    .message(msg)
                    .quickActions(List.of("📊 Fleet Summary", "🚗 Vehicle Alerts", "🏆 Safest Driver"))
                    .build();
            }
        }

        // 6. "How many critical alerts occurred?" / "unresolved critical alerts" / "open alerts"
        if (normalized.contains("critical") || normalized.contains("unresolved")) {
            long totalCrit = alertRepository.countBySeverity("CRITICAL");
            long openCrit = alertRepository.countOpenCriticalAlerts();
            List<Alert> openCritList = alertRepository.findOpenCriticalAlerts();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("🚨 **Critical Alerts Overview**:\n\n"));
            sb.append(String.format("• **Total Historical Critical Alerts:** **%,d**\n", totalCrit));
            sb.append(String.format("• **Currently Unresolved / Open:** **%,d**\n\n", openCrit));

            if (!openCritList.isEmpty()) {
                sb.append("**Active Critical Incidents:**\n");
                for (Alert a : openCritList.stream().limit(3).collect(Collectors.toList())) {
                    String vCode = a.getVehicle() != null ? a.getVehicle().getCode() : "Unknown";
                    String dName = a.getDriver() != null ? a.getDriver().getName() : "Unassigned";
                    sb.append(String.format("• **%s** on vehicle **%s** (Driver: %s) — *%s*\n",
                        a.getAlertType(), vCode, dName, a.getOccurredAt() != null ? a.getOccurredAt().toString().replace('T', ' ') : "Recent"));
                }
            } else {
                sb.append("✅ *There are currently no open critical alerts in the fleet.*");
            }

            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("ALERT_QUERY")
                .message(sb.toString())
                .quickActions(List.of("📊 Fleet Summary", "🚗 Vehicle Alerts", "🏆 Safest Driver"))
                .build();
        }

        // 7. "How many warnings occurred?"
        if (normalized.contains("warning")) {
            long warnCount = alertRepository.countBySeverity("WARNING");
            String msg = String.format("⚠️ There have been **%,d warning alerts** recorded across the fleet.", warnCount);
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("ALERT_QUERY")
                .message(msg)
                .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver"))
                .build();
        }

        // 8. Specific type filter e.g. "overspeed", "harsh braking", "gps disconnect", "night driving", "fatigue"
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
        LocalDate from, to;
        if (dr != null) {
            from = dr.startDate;
            to = dr.endDate;
        } else if (normalized.contains("week")) {
            from = LocalDate.now().minusDays(7);
            to = LocalDate.now();
        } else if (normalized.contains("month")) {
            from = LocalDate.now().withDayOfMonth(1);
            to = LocalDate.now();
        } else {
            from = LocalDate.now();
            to = LocalDate.now();
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        long entries = tripRepository.countDistinctVehiclesWithTripStarted(fromDt, toDt);
        long exits = tripRepository.countDistinctVehiclesWithTripCompleted(fromDt, toDt);

        String msg;
        if (normalized.contains("entered") && !normalized.contains("exit")) {
            msg = String.format("🚪 **Vehicles Entered (Trips Started) (%s to %s):** **%,d** active vehicles.", from, to, entries);
        } else if (normalized.contains("exited") && !normalized.contains("enter")) {
            msg = String.format("🚪 **Vehicles Exited (Trips Completed) (%s to %s):** **%,d** completed trips.", from, to, exits);
        } else {
            msg = String.format(
                "🚪 **Entry / Exit Activity (%s to %s)**:\n\n" +
                "• **Vehicle Entries (Trips Started):** **%,d** active vehicles\n" +
                "• **Vehicle Exits (Trips Completed):** **%,d** completed trips\n\n" +
                "Trips start when vehicle ignition is turned ON and finish when the engine is turned OFF.\n" +
                "Full timestamped logs are accessible on the Trips page.",
                from, to, entries, exits);
        }

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("ENTRY_EXIT")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🚗 Vehicle Alerts", "🏆 Safest Driver"))
            .build();
    }

    /** COMPARE DRIVERS INTENT */
    private ChatResponseDTO handleCompareDrivers(String sessionId, String normalized,
                                                 IntentClassifier.ClassifiedIntent classified,
                                                 Map<String, String> ctx) {
        List<Driver> allDrivers = driverRepository.findAll();
        List<Driver> matched = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        // Check for DRV codes
        Matcher m = Pattern.compile("drv0*(\\d+)").matcher(normalized);
        while (m.find()) {
            String code = "DRV" + String.format("%03d", Integer.parseInt(m.group(1)));
            driverRepository.findByCode(code).ifPresent(d -> {
                if (seenIds.add(d.getId())) matched.add(d);
            });
        }

        // Check for driver names in text
        for (Driver d : allDrivers) {
            String[] parts = d.getName().toLowerCase().split("\\s+");
            for (String part : parts) {
                if (part.length() >= 3 && normalized.contains(part)) {
                    if (seenIds.add(d.getId())) matched.add(d);
                    break;
                }
            }
        }

        // If user says "compare top 5" or "compare top drivers"
        if (matched.size() < 2 && (normalized.contains("top") || matched.isEmpty())) {
            List<DriverRankingDTO> rankings = driverService.getDriverRanking(LocalDate.of(2020, 1, 1), LocalDate.now());
            int limit = classified != null && classified.getRankingLimit() > 0 ? classified.getRankingLimit() : 5;
            List<DriverRankingDTO> topList = rankings.stream().limit(limit).collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("⚖️ **Driver Comparison (Top %d Drivers)**\n\n", topList.size()));
            sb.append("| Rank | Driver | Safety Score | Trips | Distance | Overspeed | Harsh Braking |\n");
            sb.append("|:---:|:---|:---:|---:|---:|---:|---:|\n");
            int rank = 1;
            for (DriverRankingDTO r : topList) {
                sb.append(String.format("| %d | **%s** (%s) | **%.1f** | %,d | %,.1f km | %,d | %,d |\n",
                    rank++, r.getDriverName(), r.getDriverCode(),
                    r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 0.0,
                    r.getTripCount() != null ? r.getTripCount() : 0,
                    r.getTotalDistanceKm() != null ? r.getTotalDistanceKm().doubleValue() : 0.0,
                    r.getOverspeedEvents() != null ? r.getOverspeedEvents() : 0,
                    r.getHarshBrakingEvents() != null ? r.getHarshBrakingEvents() : 0));
            }
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("COMPARE_DRIVERS")
                .message(sb.toString())
                .quickActions(List.of("🏆 Safest Driver", "🌟 Recommend Drivers", "📊 Fleet Summary"))
                .build();
        }

        if (matched.size() < 2) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("COMPARE_DRIVERS")
                .message("Which two drivers would you like to compare? Please mention two driver names or codes (e.g. *Compare Rohan and Priya* or *Compare DRV001 and DRV002*).")
                .askingForClarification(true)
                .clarificationQuestion("Please specify two drivers to compare.")
                .quickActions(List.of("Compare Rohan and Deepak", "Compare DRV001 and DRV002", "Compare Top 5 Drivers"))
                .build();
        }

        Driver d1 = matched.get(0);
        Driver d2 = matched.get(1);

        List<DriverRankingDTO> rankings = driverService.getDriverRanking(LocalDate.of(2020, 1, 1), LocalDate.now());
        DriverRankingDTO r1 = rankings.stream().filter(r -> r.getDriverId().equals(d1.getId())).findFirst().orElse(null);
        DriverRankingDTO r2 = rankings.stream().filter(r -> r.getDriverId().equals(d2.getId())).findFirst().orElse(null);

        double score1 = r1 != null && r1.getSafetyScore() != null ? r1.getSafetyScore().doubleValue() : 0.0;
        double score2 = r2 != null && r2.getSafetyScore() != null ? r2.getSafetyScore().doubleValue() : 0.0;
        long trips1 = r1 != null && r1.getTripCount() != null ? r1.getTripCount() : 0;
        long trips2 = r2 != null && r2.getTripCount() != null ? r2.getTripCount() : 0;
        double dist1 = r1 != null && r1.getTotalDistanceKm() != null ? r1.getTotalDistanceKm().doubleValue() : 0.0;
        double dist2 = r2 != null && r2.getTotalDistanceKm() != null ? r2.getTotalDistanceKm().doubleValue() : 0.0;
        long alerts1 = r1 != null && r1.getTotalAlerts() != null ? r1.getTotalAlerts() : 0;
        long alerts2 = r2 != null && r2.getTotalAlerts() != null ? r2.getTotalAlerts() : 0;
        long overspeed1 = r1 != null && r1.getOverspeedEvents() != null ? r1.getOverspeedEvents() : 0;
        long overspeed2 = r2 != null && r2.getOverspeedEvents() != null ? r2.getOverspeedEvents() : 0;
        long harsh1 = r1 != null && r1.getHarshBrakingEvents() != null ? r1.getHarshBrakingEvents() : 0;
        long harsh2 = r2 != null && r2.getHarshBrakingEvents() != null ? r2.getHarshBrakingEvents() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("⚖️ **Driver Comparison: %s vs %s**\n\n", d1.getName(), d2.getName()));
        sb.append(String.format("| Metric | %s (%s) | %s (%s) |\n", d1.getName(), d1.getCode(), d2.getName(), d2.getCode()));
        sb.append("|:---|:---:|:---:|\n");
        sb.append(String.format("| **Safety Score** | **%.1f** | **%.1f** |\n", score1, score2));
        sb.append(String.format("| **Total Trips** | %,d | %,d |\n", trips1, trips2));
        sb.append(String.format("| **Total Distance** | %,.1f km | %,.1f km |\n", dist1, dist2));
        sb.append(String.format("| **Total Alerts** | %,d | %,d |\n", alerts1, alerts2));
        sb.append(String.format("| **Overspeed Events** | %,d | %,d |\n", overspeed1, overspeed2));
        sb.append(String.format("| **Harsh Braking** | %,d | %,d |\n\n", harsh1, harsh2));

        if (score1 > score2) {
            sb.append(String.format("🏆 **Verdict:** **%s** has a stronger safety record with a safety score of **%.1f** (vs %.1f) and fewer violations relative to trips.",
                d1.getName(), score1, score2));
        } else if (score2 > score1) {
            sb.append(String.format("🏆 **Verdict:** **%s** has a stronger safety record with a safety score of **%.1f** (vs %.1f) and fewer violations relative to trips.",
                d2.getName(), score2, score1));
        } else {
            sb.append(String.format("🏆 **Verdict:** Both drivers are tied with an identical safety score of **%.1f**.", score1));
        }

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("COMPARE_DRIVERS")
            .message(sb.toString())
            .quickActions(List.of("📄 " + d1.getCode() + " Report", "📄 " + d2.getCode() + " Report", "🏆 Safest Driver"))
            .build();
    }

    /** TRIP QUERY INTENT */
    private ChatResponseDTO handleTripQuery(String sessionId, String normalized,
                                           IntentClassifier.ClassifiedIntent classified,
                                           Map<String, String> ctx) {
        LocalDate now = LocalDate.now();
        DateRange dr = parseDateRange(normalized);
        LocalDate from = dr != null ? dr.startDate : LocalDate.of(2020, 1, 1);
        LocalDate to = dr != null ? dr.endDate : now;
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        // 1. Check if specific driver mentioned e.g. "How many trips did Rohan complete?"
        Driver d = resolveDriverFromMessage(normalized, ctx);
        if (d != null) {
            long driverTrips = tripRepository.countByDriverIdAndDateRange(d.getId(), fromDt, toDt);
            if (driverTrips == 0 && dr == null) {
                driverTrips = tripRepository.countByDriverId(d.getId());
            }
            Double driverDist = tripRepository.sumDistanceByDriverAndDateRange(d.getId(), fromDt, toDt);
            if (driverDist == null || (driverDist == 0.0 && dr == null)) {
                driverDist = 0.0;
            }

            ctx.put("last_subject", "DRIVER");
            ctx.put("last_driver_id", String.valueOf(d.getId()));
            ctx.put("last_driver_name", d.getName());
            ctx.put("last_driver_code", d.getCode());

            String msg = String.format("🚗 Driver **%s (%s)** completed **%,d trips** covering **%,.1f km**%s.",
                d.getName(), d.getCode(), driverTrips, driverDist != null ? driverDist : 0.0,
                dr != null ? " (" + dr.label + ")" : " overall");

            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("TRIP_QUERY")
                .message(msg)
                .quickActions(List.of("Details for " + d.getCode(), "📄 " + d.getCode() + " Report", "🏆 Safest Driver"))
                .build();
        }

        // 2. "Which driver completed the most trips?"
        if (normalized.contains("most trip") || normalized.contains("completed the most")) {
            List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);
            if (!rankings.isEmpty()) {
                DriverRankingDTO topTrips = rankings.stream()
                    .max(Comparator.comparingLong(r -> r.getTripCount() != null ? r.getTripCount() : 0))
                    .orElse(rankings.get(0));

                String msg = String.format("🏆 Driver **%s (%s)** completed the most trips with **%,d trips** (%,.1f km total).",
                    topTrips.getDriverName(), topTrips.getDriverCode(),
                    topTrips.getTripCount() != null ? topTrips.getTripCount() : 0,
                    topTrips.getTotalDistanceKm() != null ? topTrips.getTotalDistanceKm().doubleValue() : 0.0);

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("TRIP_QUERY")
                    .message(msg)
                    .quickActions(List.of("Details for " + topTrips.getDriverCode(), "🏆 Safest Driver", "📊 Fleet Summary"))
                    .build();
            }
        }

        // 3. "Which driver travelled the most distance?" / "who travelled the most"
        if (normalized.contains("travelled the most") || normalized.contains("traveled the most") ||
            normalized.contains("most distance") || normalized.contains("longest distance")) {
            List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);
            if (!rankings.isEmpty()) {
                DriverRankingDTO topDist = rankings.stream()
                    .max(Comparator.comparingDouble(r -> r.getTotalDistanceKm() != null ? r.getTotalDistanceKm().doubleValue() : 0.0))
                    .orElse(rankings.get(0));

                String msg = String.format("🛣️ Driver **%s (%s)** travelled the most distance with **%,.1f km** across %,d completed trips.",
                    topDist.getDriverName(), topDist.getDriverCode(),
                    topDist.getTotalDistanceKm() != null ? topDist.getTotalDistanceKm().doubleValue() : 0.0,
                    topDist.getTripCount() != null ? topDist.getTripCount() : 0);

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("TRIP_QUERY")
                    .message(msg)
                    .quickActions(List.of("Details for " + topDist.getDriverCode(), "🏆 Safest Driver", "📊 Fleet Summary"))
                    .build();
            }
        }

        // 4. "What was the longest trip?"
        if (normalized.contains("longest trip")) {
            List<Trip> allTrips = tripRepository.findByStartTimeBetween(fromDt, toDt);
            if (allTrips.isEmpty() && dr == null) {
                allTrips = tripRepository.findAll();
            }
            Optional<Trip> longest = allTrips.stream()
                .filter(t -> t.getDistanceKm() != null)
                .max(Comparator.comparingDouble(t -> t.getDistanceKm().doubleValue()));

            if (longest.isPresent()) {
                Trip t = longest.get();
                String driverName = t.getDriver() != null ? t.getDriver().getName() : "Unknown";
                String vCode = t.getVehicle() != null ? t.getVehicle().getCode() : "Unknown";
                String msg = String.format("📏 The longest recorded trip was **%,.1f km**, driven by **%s** in vehicle **%s** on %s.",
                    t.getDistanceKm(), driverName, vCode,
                    t.getStartTime() != null ? t.getStartTime().toLocalDate() : "N/A");

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("TRIP_QUERY")
                    .message(msg)
                    .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver"))
                    .build();
            }
        }

        // 5. "What is the average trip distance?"
        if (normalized.contains("average trip distance") || normalized.contains("average distance")) {
            long totalTrips = dr != null ? tripRepository.countInDateRange(fromDt, toDt) : tripRepository.count();
            Double totalDist = dr != null ? tripRepository.sumDistanceInDateRange(fromDt, toDt) : tripRepository.sumTotalDistance();
            double avg = (totalTrips > 0 && totalDist != null) ? totalDist / totalTrips : 0.0;

            String msg = String.format("📏 The average trip distance across the fleet is **%.1f km** (based on %,d completed trips).",
                avg, totalTrips);

            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("TRIP_QUERY")
                .message(msg)
                .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver"))
                .build();
        }

        // 6. "What is the average trip duration?"
        if (normalized.contains("average trip duration") || normalized.contains("trip duration") || normalized.contains("duration")) {
            List<Trip> trips = tripRepository.findByStartTimeBetween(fromDt, toDt);
            if (trips.isEmpty() && dr == null) {
                trips = tripRepository.findAll();
            }
            long totalMinutes = 0;
            long finishedCount = 0;
            for (Trip t : trips) {
                if (t.getStartTime() != null && t.getEndTime() != null) {
                    long mins = java.time.Duration.between(t.getStartTime(), t.getEndTime()).toMinutes();
                    if (mins > 0 && mins < 1440) {
                        totalMinutes += mins;
                        finishedCount++;
                    }
                }
            }
            long avgMins = finishedCount > 0 ? totalMinutes / finishedCount : 45;
            String msg = String.format("⏱️ The average trip duration across the fleet is approximately **%d minutes** (based on %,d finished trips).",
                avgMins, finishedCount);

            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("TRIP_QUERY")
                .message(msg)
                .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver"))
                .build();
        }

        // 7. General trip query / "show today's trips"
        long count = tripRepository.countInDateRange(fromDt, toDt);
        Double dist = tripRepository.sumDistanceInDateRange(fromDt, toDt);
        String label = dr != null ? dr.label : "Today (" + now + ")";
        String msg = String.format("🛣️ **Trip Activity (%s)**:\n\n" +
            "• **Trips Started/Completed:** **%,d**\n" +
            "• **Total Distance Covered:** **%,.1f km**\n" +
            "• **Average Distance per Trip:** **%.1f km**\n\n" +
            "Full per-trip GPS breadcrumbs, routes, and speed graphs are viewable in the Trips dashboard.",
            label, count, dist != null ? dist : 0.0,
            count > 0 && dist != null ? dist / count : 0.0);

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("TRIP_QUERY")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "⚠️ View Alerts"))
            .build();
    }

    /** OVERALL REPORT INTENT */
    private ChatResponseDTO handleOverallReport(String sessionId, String normalized,
                                               IntentClassifier.ClassifiedIntent classified,
                                               Map<String, String> ctx) {
        LocalDate now = LocalDate.now();
        IntentClassifier.Period period = classified != null ? classified.getPeriod() : null;
        DateRange dr = parseDateRange(normalized);
        LocalDate from, to;
        String intentName;
        String periodLabel;

        if (period == IntentClassifier.Period.DAILY || normalized.contains("today") || normalized.contains("daily")) {
            from = now;
            to = now;
            intentName = "OVERALL_DAILY_REPORT";
            periodLabel = "Today (" + now + ")";
        } else if (period == IntentClassifier.Period.WEEKLY || normalized.contains("week")) {
            if (normalized.contains("last week")) {
                LocalDate end = now.with(TemporalAdjusters.previous(java.time.DayOfWeek.SUNDAY));
                from = end.minusDays(6);
                to = end;
                periodLabel = "Last Week (" + from + " to " + to + ")";
            } else {
                from = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                to = now;
                periodLabel = "This Week (" + from + " to " + to + ")";
            }
            intentName = "OVERALL_WEEKLY_REPORT";
        } else if (period == IntentClassifier.Period.MONTHLY || normalized.contains("month")) {
            YearMonth ym = extractYearMonth(normalized);
            if (ym != null) {
                from = ym.atDay(1);
                to = ym.atEndOfMonth();
                periodLabel = ym.getMonth().name() + " " + ym.getYear();
            } else {
                from = now.withDayOfMonth(1);
                to = now;
                periodLabel = "This Month (" + from + " to " + to + ")";
            }
            intentName = "OVERALL_MONTHLY_REPORT";
        } else if (dr != null) {
            from = dr.startDate;
            to = dr.endDate;
            intentName = "OVERALL_CUSTOM_REPORT";
            periodLabel = dr.label;
        } else {
            from = now.withDayOfMonth(1);
            to = now;
            intentName = "OVERALL_MONTHLY_REPORT";
            periodLabel = "Current Month (" + from + " to " + to + ")";
        }

        // If user explicitly asked for PDF report generation
        if (normalized.contains("pdf") || normalized.contains("download") || normalized.contains("generate")) {
            try {
                ReportRequestDTO req = new ReportRequestDTO();
                req.setReportType("MONTHLY");
                req.setStartDate(from);
                req.setEndDate(to);
                ReportResponseDTO report = reportService.generateFleetReport(req);

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent(intentName)
                    .message(String.format("✅ **Overall Fleet Report (%s)** is ready!\n\nClick below to view or download the comprehensive fleet PDF report.", periodLabel))
                    .reportId(report.getReportId())
                    .pdfUrl(report.getPdfUrl())
                    .downloadUrl(report.getDownloadUrl())
                    .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "⚠️ View Alerts"))
                    .build();
            } catch (Exception e) {
                log.warn("Error generating fleet PDF, falling back to summary", e);
            }
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        long totalVehicles = vehicleRepository.count();
        long activeVehicles = vehicleRepository.findByActiveTrue().size();
        long entries = tripRepository.countDistinctVehiclesWithTripStarted(fromDt, toDt);
        long exits = tripRepository.countDistinctVehiclesWithTripCompleted(fromDt, toDt);
        long tripCount = tripRepository.countInDateRange(fromDt, toDt);
        Double totalDistance = tripRepository.sumDistanceInDateRange(fromDt, toDt);
        long totalAlerts = alertRepository.countInDateRange(fromDt, toDt);
        long critAlerts = alertRepository.countBySeverityInDateRange("CRITICAL", fromDt, toDt);
        long openAlerts = alertRepository.countOpenAlerts();

        List<Object[]> alertTypes = alertRepository.countByTypeInDateRange(fromDt, toDt);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 **Overall Fleet Performance Report (%s)**\n\n", periodLabel));
        sb.append(String.format("• **Vehicles Active:** %d / %d total vehicles\n", activeVehicles, totalVehicles));
        sb.append(String.format("• **Vehicles Entered (Trips Started):** %,d\n", entries));
        sb.append(String.format("• **Vehicles Exited (Trips Completed):** %,d\n", exits));
        sb.append(String.format("• **Trips Completed:** %,d\n", tripCount));
        sb.append(String.format("• **Total Distance Covered:** %,.1f km\n", totalDistance != null ? totalDistance : 0.0));
        sb.append(String.format("• **Alerts Generated:** %,d (%,d critical, %,d open)\n\n", totalAlerts, critAlerts, openAlerts));

        if (!alertTypes.isEmpty()) {
            sb.append("⚠️ **Top Alert Types in Period:**\n");
            for (Object[] r : alertTypes.stream().limit(4).collect(Collectors.toList())) {
                sb.append(String.format("• %s: %,d\n", r[0], ((Number) r[1]).longValue()));
            }
            sb.append("\n");
        }

        List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);
        if (!rankings.isEmpty()) {
            DriverRankingDTO topDriver = rankings.get(0);
            sb.append(String.format("🏆 **Top Driver:** **%s (%s)** with a safety score of **%.1f / 100**.\n\n",
                topDriver.getDriverName(), topDriver.getDriverCode(),
                topDriver.getSafetyScore() != null ? topDriver.getSafetyScore().doubleValue() : 0.0));
        }

        sb.append("📄 *Would you like me to generate the official PDF report for this period?*");

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent(intentName)
            .message(sb.toString())
            .quickActions(List.of("📄 Generate Fleet PDF Report", "🏆 Safest Driver", "🚗 Vehicle Alerts", "📊 Fleet Summary"))
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
        String msg = "I can help with driver rankings, fleet summaries, trips, alerts, and daily/weekly/monthly reports. What would you like to know?\n\n" +
            "Here are some examples of what you can ask:\n" +
            "• **Fleet Overview**: *\"Give me today's fleet summary\"* or *\"How did the fleet perform today?\"*\n" +
            "• **Drivers**: *\"Who is the safest driver?\"* or *\"Recommend 3 drivers for an important trip\"*\n" +
            "• **Comparison**: *\"Compare Rohan and Deepak\"* or *\"Compare the top 5 drivers\"*\n" +
            "• **Vehicles**: *\"Which vehicle has the most alerts?\"* or *\"Tell me about VH003\"*\n" +
            "• **Trips**: *\"Show today's trips\"* or *\"Which driver completed the most trips?\"*\n" +
            "• **Alerts**: *\"Show unresolved critical alerts\"* or *\"Which driver has the most violations?\"*\n" +
            "• **Reports**: *\"Generate fleet report for September 2026\"* or *\"Rohan's September report\"*";

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("UNKNOWN")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🌟 Recommend Drivers", "🏆 Safest Driver", "⚠️ Top Alerts", "📄 Fleet Report"))
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
