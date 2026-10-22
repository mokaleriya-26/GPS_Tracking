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
    private final LlmService llmService;
    private final MaintenanceRepository maintenanceRepository;
    private final CostRepository costRepository;
    private final PenaltyService penaltyService;
    private final DriverPenaltyRepository penaltyRepository;
    private final SimulationService simulationService;

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

        // Handle explicit cancellation
        if (normalized.equals("cancel") || normalized.equals("stop") || normalized.equals("reset") || normalized.equals("nevermind") || normalized.equals("never mind")) {
            ctx.remove("awaiting_report_type");
            ctx.remove("awaiting_driver");
            ctx.remove("awaiting_vehicle");
            ctx.remove("awaiting_month");
            ctx.remove("pending_intent");
            ctx.remove("pending_driver_id");
            ctx.remove("pending_vehicle_id");
            ctx.remove("pending_report_type");
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("GENERAL_QUESTION")
                .message("Understood! The previous request has been cancelled. What else would you like to know?")
                .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "⚠️ Show Open Alerts"))
                .build();
        }

        // If user is asking an explicit new query, cancel any stale clarification state so it is never hijacked!
        boolean isExplicitNewQuery = normalized.matches("^(which|who|what|how|rank|show|list|tell|top|safest|compare|recommend|give|generate|create).*")
            || normalized.contains("?")
            || normalized.contains("alert")
            || normalized.contains("violation")
            || normalized.contains("ranking");

        if (isExplicitNewQuery) {
            ctx.remove("awaiting_report_type");
            ctx.remove("awaiting_driver");
            ctx.remove("awaiting_vehicle");
            ctx.remove("awaiting_month");
            ctx.remove("pending_intent");
            ctx.remove("pending_driver_id");
            ctx.remove("pending_vehicle_id");
            ctx.remove("pending_report_type");
        }

        // =========================================================
        // 0. LIVE SIMULATION QUERIES
        // =========================================================
        if (normalized.contains("simulat") || normalized.contains("simulation")) {
            return handleSimulationQuery(sessionId, normalized, message);
        }

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
        // 1.5. CONVERSATIONAL FOLLOW-UP UNDERSTANDING ("What about yesterday?", "Generate a report for that")
        // =========================================================
        if (normalized.matches(".*(what about|how about|and what about)\\s+(yesterday|today|last week|this week|this month|last month).*")) {
            DateRange dr = parseDateRange(normalized);
            if (dr != null) {
                String lastSubject = ctx.get("last_subject");
                if ("VEHICLE".equals(lastSubject) && ctx.containsKey("last_vehicle_id")) {
                    return handleVehicleAlertAnalysis(sessionId, normalized, ctx);
                } else if ("DRIVER".equals(lastSubject) && ctx.containsKey("last_driver_id")) {
                    return handleDriverStats(sessionId, normalized, ctx);
                } else {
                    return handleFleetSummary(sessionId, normalized, ctx);
                }
            }
        }
        if (normalized.matches(".*(generate|create|download|make)\\s+(a\\s+)?report\\s+(for\\s+)?(that|it|this).*")) {
            String lastSubject = ctx.get("last_subject");
            if ("VEHICLE".equals(lastSubject) && ctx.containsKey("last_vehicle_id")) {
                return handleVehicleReportIntent(sessionId, normalized, message, ctx);
            } else if ("DRIVER".equals(lastSubject) && ctx.containsKey("last_driver_id")) {
                return handleDriverReportIntent(sessionId, normalized, message, ctx);
            } else {
                return handleFleetReport(sessionId, normalized, ctx);
            }
        }

        // =========================================================
        // 2. OPTIONAL LLM ANALYSIS (If enabled via config / API key)
        // =========================================================
        IntentClassifier.ClassifiedIntent classified = null;
        if (llmService.isLlmEnabled()) {
            Optional<com.gps.tracking.dto.LlmIntentResponseDTO> llmResult = llmService.analyzeMessage(message, ctx);
            if (llmResult.isPresent()) {
                com.gps.tracking.dto.LlmIntentResponseDTO res = llmResult.get();
                if (res.getConversationalResponse() != null && !res.getConversationalResponse().isBlank() &&
                    ("SMALL_TALK".equalsIgnoreCase(res.getIntent()) || "GREETING".equalsIgnoreCase(res.getIntent()) ||
                     "CAPABILITIES".equalsIgnoreCase(res.getIntent()) || "EXPLANATION".equalsIgnoreCase(res.getIntent()) ||
                     "CLARIFICATION_NEEDED".equalsIgnoreCase(res.getIntent()))) {
                    return ChatResponseDTO.builder()
                        .sessionId(sessionId)
                        .intent(res.getIntent().toUpperCase())
                        .message(res.getConversationalResponse())
                        .quickActions(res.getQuickActions() != null && !res.getQuickActions().isEmpty()
                            ? res.getQuickActions()
                            : List.of("📊 Fleet Summary", "🏆 Safest Driver", "🌟 Recommend Drivers", "⚠️ Top Alerts"))
                        .build();
                }

                // If LLM recognized a structured fleet intent, bridge it to a ClassifiedIntent
                classified = mapLlmResponseToClassifiedIntent(res);
            }
        }

        // =========================================================
        // 3. INTENT CLASSIFICATION VIA NLP ENGINE (Fallback or Primary)
        // =========================================================
        if (classified == null || classified.getIntent() == IntentClassifier.Intent.UNKNOWN
                || normalized.contains("penalt") || normalized.contains("warning-only") || normalized.contains("fine") || normalized.contains("monetary") || normalized.contains("fines")) {
            classified = intentClassifier.classify(message);
        }
        IntentClassifier.Intent intent = classified.getIntent();
        log.info("Classified intent: {} (score={}) for msg: {}", intent, classified.getScore(), message);

        switch (intent) {
            case GREETING:
                return handleGreetingOrHelp(sessionId);

            case SMALL_TALK:
                return handleSmallTalk(sessionId, normalized);

            case CAPABILITIES:
                return handleCapabilities(sessionId);

            case EXPLANATION:
                return handleExplanation(sessionId, normalized, ctx);

            case COMPARE_DRIVERS:
                return handleCompareDrivers(sessionId, normalized, classified, ctx);

            case DRIVER_RECOMMENDATION:
                return handleDriverRecommendation(sessionId, normalized, ctx);

            case DRIVER_RANKING:
                return handleDriverRanking(sessionId, normalized, classified, ctx);

            case DRIVER_ALERT_COUNT:
                return handleDriverAlertCount(sessionId, normalized, classified, ctx);

            case DRIVER_REPORT:
                return handleDriverReportIntent(sessionId, normalized, message, classified, ctx);

            case DRIVER_STATS:
                return handleDriverStats(sessionId, normalized, ctx);

            case VEHICLE_RANKING:
                return handleVehicleRanking(sessionId, normalized, classified, ctx);

            case VEHICLE_ALERT_ANALYSIS:
                return handleVehicleAlertAnalysis(sessionId, normalized, ctx);

            case VEHICLE_STATS:
                return handleVehicleQueryOrStats(sessionId, normalized, ctx);

            case MAINTENANCE_QUERY:
                return handleMaintenanceQuery(sessionId, normalized, ctx);

            case COST_QUERY:
                return handleCostQuery(sessionId, normalized, ctx);

            case PENALTY_QUERY:
                return handlePenaltyQuery(sessionId, normalized, classified, ctx);

            case OVERALL_REPORT:
                return handleOverallReport(sessionId, normalized, classified, ctx);

            case FLEET_REPORT:
                return handleFleetReport(sessionId, normalized, ctx);

            case ALERT_QUERY:
                return handleAlertQuery(sessionId, normalized, ctx);

            case ALERT_REPORT:
                return handleAlertReportIntent(sessionId, normalized, classified, ctx);

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

    /** Casual / Small Talk Handler */
    private ChatResponseDTO handleSmallTalk(String sessionId, String normalized) {
        String reply;
        if (normalized.contains("how are you") || normalized.contains("how r u") || normalized.contains("how are u") || normalized.contains("how's it going")) {
            reply = "😊 **I'm doing great, thank you!** All fleet telemetry systems, vehicle tracking feeds, and driver databases are online and operating smoothly.\n\nHow can I help you manage your fleet today?";
        } else if (normalized.contains("thanks") || normalized.contains("thank") || normalized.contains("thx") || normalized.contains("appreciate")) {
            reply = "🙏 **You're very welcome!** Always happy to help keep your fleet running safely and efficiently. Let me know if you need any more stats, driver recommendations, or reports!";
        } else if (normalized.contains("good job") || normalized.contains("great job") || normalized.contains("nice work") || normalized.contains("cool")) {
            reply = "🚀 **Thank you!** I strive to deliver accurate, real-time insights for your fleet operations. What would you like to explore next?";
        } else {
            reply = "🤖 I'm your AI Fleet Assistant, connected directly to your vehicles and drivers. Let me know whatever you need!";
        }

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("SMALL_TALK")
            .message(reply)
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "🌟 Recommend Drivers", "⚠️ Top Alerts"))
            .build();
    }

    /** Capabilities Handler */
    private ChatResponseDTO handleCapabilities(String sessionId) {
        String msg = "🛠️ **Here is what I can do for you:**\n\n" +
            "• **📊 Fleet Overview:** Real-time active vehicle counts, trip distances, entry/exit numbers, and open alerts.\n" +
            "• **🏆 Driver Rankings & Safety:** Identify the safest or riskiest drivers, view safety scores (0-100), and inspect driver profiles.\n" +
            "• **🌟 Smart Driver Recommendations:** Suggest the best drivers for urgent, long-distance, or high-value assignments based on real driving history.\n" +
            "• **⚖️ Driver Comparison:** Compare safety scores, trip counts, and violation rates between two drivers side-by-side.\n" +
            "• **🚗 Vehicle Diagnostics:** Check vehicle registration, make/model, fuel capacity, current driver, and top speed.\n" +
            "• **⚠️ Alert Analytics:** Analyze overspeed events, harsh braking, GPS disconnects, and night driving violations.\n" +
            "• **🛣️ Trip Activity:** Look up total distance, longest trips, and completed routes for any period.\n" +
            "• **📄 PDF Reports:** Generate and download official monthly and periodic fleet/driver reports on demand.";

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("CAPABILITIES")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "🌟 Recommend Drivers", "🚗 VH001 Details", "📄 Generate Report"))
            .build();
    }

    /** Simple Explanation Handler */
    private ChatResponseDTO handleExplanation(String sessionId, String normalized, Map<String, String> ctx) {
        String msg;
        String lastSubject = ctx.get("last_subject");

        if ("DRIVER".equals(lastSubject) && ctx.containsKey("last_driver_name")) {
            String driverName = ctx.get("last_driver_name");
            msg = String.format("💡 **In simple terms for %s:**\n\n" +
                "The safety score starts at **100 points**. Points are deducted for risky events:\n" +
                "• **-5 pts** for each Overspeeding incident\n" +
                "• **-4 pts** for each Harsh Braking event\n" +
                "• **-4 pts** for each Harsh Acceleration\n" +
                "• **-8 pts** for Fatigue events\n" +
                "• **+1 bonus pt** for each trip completed without any incidents.\n\n" +
                "Higher scores mean safer driving and lower accident risk.", driverName);
        } else {
            msg = "💡 **How the Fleet System Works Simply:**\n\n" +
                "• **GPS Trackers** on each vehicle send speed, ignition, and location updates every few seconds.\n" +
                "• If a driver speeds or brakes harshly, the system records an **Alert** instantly.\n" +
                "• Clean trips raise driver safety scores; violations lower them.\n" +
                "• All metrics are stored securely in PostgreSQL so you can inspect live fleet health at any time.";
        }

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("EXPLANATION")
            .message(msg)
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver", "🌟 Recommend Drivers"))
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
            .success(true)
            .intent("VEHICLE_ALERT_ANALYSIS")
            .source("POSTGRESQL")
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

    public static class DriverAlertRanking {
        private final Long driverId;
        private final String driverCode;
        private final String driverName;
        private final long totalAlerts;

        public DriverAlertRanking(Object[] row) {
            this.driverId = ((Number) row[0]).longValue();
            this.driverCode = String.valueOf(row[1]);
            this.driverName = String.valueOf(row[2]);
            this.totalAlerts = ((Number) row[3]).longValue();
        }

        public Long getDriverId() { return driverId; }
        public String getDriverCode() { return driverCode; }
        public String getDriverName() { return driverName; }
        public long getTotalAlerts() { return totalAlerts; }
    }

    private ChatResponseDTO handleDriverRanking(String sessionId, String normalized,
                                                IntentClassifier.ClassifiedIntent classified,
                                                Map<String, String> ctx) {
        DateRange dr = parseDateRange(normalized);
        boolean isAlertRanking = normalized.contains("alert") || normalized.contains("violation")
            || normalized.contains("braking") || normalized.contains("overspeed") || normalized.contains("critical")
            || normalized.contains("disconnect") || normalized.contains("highest number")
            || (normalized.contains("rank") && !normalized.contains("safety"));

        // =========================================================================
        // A. Factual Alert-Based Ranking via PostgreSQL parameterized queries
        // =========================================================================
        if (isAlertRanking) {
            String alertType = null;
            if (normalized.contains("harsh braking") || normalized.contains("braking")) alertType = "harsh braking";
            else if (normalized.contains("overspeed") || normalized.contains("speed")) alertType = "overspeed";
            else if (normalized.contains("disconnect") || normalized.contains("gps")) alertType = "disconnect";
            else if (normalized.contains("night")) alertType = "night";
            else if (normalized.contains("fatigue")) alertType = "fatigue";

            String severity = null;
            if (normalized.contains("critical")) severity = "CRITICAL";
            else if (normalized.contains("warning")) severity = "WARNING";

            LocalDateTime startDt = dr != null ? dr.startDateTime() : null;
            LocalDateTime endDt = dr != null ? dr.endDateTime() : null;
            String periodLabel = dr != null ? dr.label : "All-Time";

            boolean isFewest = normalized.matches(".*(fewest|least|minimum|lowest alert|lowest number).*");

            List<DriverAlertRanking> rankings = (isFewest
                ? alertRepository.findDriverAlertRankingsAsc(alertType, severity, startDt, endDt)
                : alertRepository.findDriverAlertRankings(alertType, severity, startDt, endDt))
                .stream().map(DriverAlertRanking::new).collect(Collectors.toList());

            if (rankings.isEmpty()) {
                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .success(true)
                    .intent("DRIVER_RANKING")
                    .source("POSTGRESQL")
                    .message(String.format("No driver alert records found in the database for the given criteria (%s).", periodLabel))
                    .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver"))
                    .build();
            }

            int rankingLimit = (classified != null && classified.getRankingLimit() > 0) ? classified.getRankingLimit() : 0;
            if (normalized.contains("top 10")) rankingLimit = 10;
            else if (normalized.contains("top 5")) rankingLimit = 5;
            else if (normalized.contains("top 3")) rankingLimit = 3;

            boolean isSingle = rankingLimit == 1 || (rankingLimit == 0 && (
                normalized.contains("which driver") || normalized.contains("who is") ||
                normalized.contains("show the driver") || normalized.contains("tell me the driver") ||
                normalized.contains("highest number") || normalized.contains("maximum alerts") ||
                (normalized.contains("most alerts") && !normalized.contains("top") && !normalized.contains("rank"))
            ));

            if (isSingle) {
                DriverAlertRanking top = rankings.get(0);

                ctx.put("last_subject", "DRIVER");
                ctx.put("last_driver_id", String.valueOf(top.getDriverId()));
                ctx.put("last_driver_name", top.getDriverName());
                ctx.put("last_driver_code", top.getDriverCode());

                String typeQualifier = alertType != null ? alertType + " " : (severity != null ? severity.toLowerCase() + " " : "");
                String msg = String.format("The driver with the %s number of %salerts%s is **%s (%s)** with **%,d alerts**.",
                    isFewest ? "lowest" : "highest",
                    typeQualifier,
                    dr != null ? " in " + dr.label : "",
                    top.getDriverName(), top.getDriverCode(), top.getTotalAlerts());

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("driverCode", top.getDriverCode());
                data.put("driverName", top.getDriverName());
                data.put("totalAlerts", top.getTotalAlerts());

                Map<String, Object> filters = new LinkedHashMap<>();
                filters.put("alertType", alertType);
                filters.put("severity", severity);
                filters.put("startDate", startDt);
                filters.put("endDate", endDt);

                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .success(true)
                    .intent("DRIVER_RANKING")
                    .source("POSTGRESQL")
                    .message(msg)
                    .data(data)
                    .filters(filters)
                    .quickActions(List.of("Show all alerts for " + top.getDriverCode(), "🏆 Top 5 Drivers by Alerts", "📊 Fleet Summary"))
                    .build();
            }

            int limit = rankingLimit > 0 ? rankingLimit : 5;
            List<DriverAlertRanking> topN = rankings.stream()
                .limit(limit)
                .collect(Collectors.toList());

            StringBuilder table = new StringBuilder();
            String titleType = alertType != null ? alertType.toUpperCase() + " " : (severity != null ? severity.toUpperCase() + " " : "");
            table.append(String.format("⚠️ **Top %d Drivers by %sAlert Count%s**\n\n",
                topN.size(), titleType, dr != null ? " (" + dr.label + ")" : ""));
            table.append("| Rank | Driver Code | Driver Name | Total Alerts |\n");
            table.append("|:---:|:---:|:---|---:|\n");

            int rIdx = 1;
            for (DriverAlertRanking p : topN) {
                table.append(String.format("| %d | **%s** | %s | %,d |\n",
                    rIdx++, p.getDriverCode(), p.getDriverName(), p.getTotalAlerts()));
            }

            DriverAlertRanking best = topN.get(0);
            ctx.put("last_subject", "DRIVER");
            ctx.put("last_driver_id", String.valueOf(best.getDriverId()));
            ctx.put("last_driver_name", best.getDriverName());
            ctx.put("last_driver_code", best.getDriverCode());

            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("alertType", alertType);
            filters.put("severity", severity);
            filters.put("startDate", startDt);
            filters.put("endDate", endDt);

            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .success(true)
                .intent("DRIVER_RANKING")
                .source("POSTGRESQL")
                .message(table.toString())
                .data(topN)
                .filters(filters)
                .quickActions(List.of("Details for " + best.getDriverCode(), "🏆 Safest Driver", "📊 Fleet Summary"))
                .build();
        }

        // =========================================================================
        // B. Safety Score Rankings via DriverService
        // =========================================================================
        LocalDate from = dr != null ? dr.startDate : LocalDate.now().minusMonths(1).withDayOfMonth(1);
        LocalDate to = dr != null ? dr.endDate : LocalDate.now();
        String periodLabel = dr != null ? dr.label : "Current Period";

        List<DriverRankingDTO> rankings = driverService.getDriverRanking(from, to);
        if (rankings.isEmpty()) {
            from = LocalDate.of(2020, 1, 1);
            rankings = driverService.getDriverRanking(from, to);
            periodLabel = "All-Time";
        }

        boolean lowestScore = normalized.contains("lowest safety") || normalized.contains("worst safety") || normalized.contains("lowest score");
        if (lowestScore) {
            rankings = rankings.stream()
                .sorted(Comparator.comparingDouble((DriverRankingDTO r) -> r.getSafetyScore() != null ? r.getSafetyScore().doubleValue() : 100.0))
                .collect(Collectors.toList());
        }

        boolean askingForSingleSafest = (normalized.contains("who is safest") || normalized.contains("who is the safest") ||
            normalized.contains("which driver is safest") || normalized.contains("who has the best safety") ||
            normalized.contains("who has the highest safety") || normalized.contains("safest driver") ||
            normalized.contains("lowest safety score") ||
            (normalized.contains("top driver") && !normalized.contains("drivers")) ||
            (normalized.contains("best driver") && !normalized.contains("drivers")))
            && !normalized.contains("top 5") && !normalized.contains("top 3") && !normalized.contains("top 10")
            && !normalized.contains("rank") && !normalized.contains("leaderboard");

        if (askingForSingleSafest && !rankings.isEmpty()) {
            DriverRankingDTO top = rankings.get(0);
            double score = top.getSafetyScore() != null ? top.getSafetyScore().doubleValue() : 0.0;
            String singleMsg = String.format("The driver is **%s (%s)** with a safety score of **%.1f / 100**, currently ranked **#1** (%s).\n\n" +
                "• **Completed Trips:** %,d\n" +
                "• **Distance:** %,.1f km\n" +
                "• **Total Alerts:** %,d (%d overspeed, %d harsh braking)",
                top.getDriverName(), top.getDriverCode(), score, periodLabel,
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
                .success(true)
                .intent("DRIVER_RANKING")
                .source("POSTGRESQL")
                .message(singleMsg)
                .data(top)
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
                .success(true)
                .intent("DRIVER_RANKING")
                .source("POSTGRESQL")
                .message(String.format("No driver rankings available for **%s**.", periodLabel))
                .quickActions(List.of("📊 Fleet Summary", "👨 All Drivers"))
                .build();
        }

        StringBuilder table = new StringBuilder();
        table.append(String.format("🏆 **Top %d Drivers by Safety Score (%s):**\n\n", topN.size(), periodLabel));
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
        ctx.put("last_subject", "DRIVER");
        ctx.put("last_driver_id", String.valueOf(best.getDriverId()));
        ctx.put("last_driver_name", best.getDriverName());
        ctx.put("last_driver_code", best.getDriverCode());

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .success(true)
            .intent("DRIVER_RANKING")
            .source("POSTGRESQL")
            .message(table.toString())
            .data(topN)
            .quickActions(List.of("Show details for " + best.getDriverName(), "📊 Fleet Summary", "🌟 Recommend Drivers"))
            .build();
    }

    /** DRIVER ALERT COUNT HANDLER */
    private ChatResponseDTO handleDriverAlertCount(String sessionId, String normalized,
                                                   IntentClassifier.ClassifiedIntent classified,
                                                   Map<String, String> ctx) {
        Driver driver = null;
        if (classified != null && classified.getDriverCodeHint() != null) {
            driver = driverRepository.findByCode(classified.getDriverCodeHint()).orElse(null);
        }
        if (driver == null) {
            driver = resolveDriverFromMessage(normalized, ctx);
        }
        if (driver == null) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .success(false)
                .intent("DRIVER_ALERT_COUNT")
                .source("POSTGRESQL")
                .message("Please specify which driver you'd like to check (for example: **DRV008** or **DRV009**).")
                .askingForClarification(true)
                .quickActions(List.of("DRV008 Alerts", "DRV009 Alerts", "🏆 Driver Rankings"))
                .build();
        }

        ctx.put("last_subject", "DRIVER");
        ctx.put("last_driver_id", String.valueOf(driver.getId()));
        ctx.put("last_driver_name", driver.getName());
        ctx.put("last_driver_code", driver.getCode());

        String alertType = null;
        if (normalized.contains("harsh braking") || normalized.contains("braking")) alertType = "harsh braking";
        else if (normalized.contains("overspeed") || normalized.contains("speed")) alertType = "overspeed";
        else if (normalized.contains("disconnect") || normalized.contains("gps")) alertType = "disconnect";

        String severity = null;
        if (normalized.contains("critical")) severity = "CRITICAL";
        else if (normalized.contains("warning")) severity = "WARNING";

        DateRange dr = parseDateRange(normalized);
        LocalDateTime startDt = dr != null ? dr.startDateTime() : null;
        LocalDateTime endDt = dr != null ? dr.endDateTime() : null;

        long alertCount = alertRepository.countAlertsByDriverWithFilters(
            driver.getId(), alertType, severity, startDt, endDt
        );

        String typeQualifier = alertType != null ? alertType + " " : (severity != null ? severity.toLowerCase() + " " : "");
        String msg = String.format("Driver **%s (%s)** has generated **%,d %salerts**%s.",
            driver.getName(), driver.getCode(), alertCount,
            typeQualifier,
            dr != null ? " in " + dr.label : "");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("driverCode", driver.getCode());
        data.put("driverName", driver.getName());
        data.put("totalAlerts", alertCount);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("alertType", alertType);
        filters.put("severity", severity);
        filters.put("startDate", startDt);
        filters.put("endDate", endDt);

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .success(true)
            .intent("DRIVER_ALERT_COUNT")
            .source("POSTGRESQL")
            .message(msg)
            .data(data)
            .filters(filters)
            .quickActions(List.of("Details for " + driver.getCode(), "🏆 Driver Rankings", "📊 Fleet Summary"))
            .build();
    }

    /** VEHICLE RANKING HANDLER */
    private ChatResponseDTO handleVehicleRanking(String sessionId, String normalized,
                                                 IntentClassifier.ClassifiedIntent classified,
                                                 Map<String, String> ctx) {
        DateRange dr = parseDateRange(normalized);
        List<Object[]> topVehicles;
        String periodLabel = "All-Time";
        if (dr != null) {
            topVehicles = alertRepository.findTopVehiclesByAlertCountInDateRange(dr.startDateTime(), dr.endDateTime());
            periodLabel = dr.label;
        } else {
            topVehicles = alertRepository.findTopVehiclesByAlertCount();
        }

        if (topVehicles.isEmpty()) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .success(true)
                .intent("VEHICLE_RANKING")
                .source("POSTGRESQL")
                .message("No vehicle alerts recorded for " + periodLabel + ".")
                .build();
        }

        Object[] top = topVehicles.get(0);
        String vCode = String.valueOf(top[1]);
        String regNum = String.valueOf(top[2]);
        long alertCount = ((Number) top[3]).longValue();

        boolean isSingle = normalized.matches(".*(which vehicle|who is|show the vehicle|top vehicle).*") && !normalized.contains("top 5");
        if (isSingle) {
            String msg = String.format("The vehicle with the highest number of alerts is **%s (%s)** with **%,d alerts** (%s).",
                regNum, vCode, alertCount, periodLabel);
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .success(true)
                .intent("VEHICLE_RANKING")
                .source("POSTGRESQL")
                .message(msg)
                .data(Map.of("vehicleCode", vCode, "registrationNumber", regNum, "totalAlerts", alertCount))
                .quickActions(List.of("Details for " + vCode, "📊 Fleet Summary"))
                .build();
        }

        int limit = 5;
        if (normalized.contains("top 10")) limit = 10;
        else if (normalized.contains("top 3")) limit = 3;

        StringBuilder table = new StringBuilder();
        table.append(String.format("🚗 **Top Vehicles by Alert Count (%s)**\n\n", periodLabel));
        table.append("| Rank | Vehicle Code | Registration | Alert Count |\n");
        table.append("|:---:|:---:|:---|---:|\n");
        int rIdx = 1;
        for (int i = 0; i < Math.min(limit, topVehicles.size()); i++) {
            Object[] row = topVehicles.get(i);
            table.append(String.format("| %d | **%s** | %s | %,d |\n",
                rIdx++, row[1], row[2], ((Number) row[3]).longValue()));
        }

        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .success(true)
            .intent("VEHICLE_RANKING")
            .source("POSTGRESQL")
            .message(table.toString())
            .data(topVehicles.subList(0, Math.min(limit, topVehicles.size())))
            .quickActions(List.of("📊 Fleet Summary", "🏆 Safest Driver"))
            .build();
    }

    /** MAINTENANCE QUERY HANDLER */
    private ChatResponseDTO handleMaintenanceQuery(String sessionId, String normalized, Map<String, String> ctx) {
        List<MaintenanceRecord> records = maintenanceRepository.findAll();
        long total = records.size();
        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .success(true)
            .intent("MAINTENANCE_QUERY")
            .source("POSTGRESQL")
            .message(String.format("🔧 There are **%,d maintenance service records** on file for the fleet.", total))
            .quickActions(List.of("🚗 Vehicles", "📊 Fleet Summary"))
            .build();
    }

    /** COST QUERY HANDLER */
    private ChatResponseDTO handleCostQuery(String sessionId, String normalized, Map<String, String> ctx) {
        List<CostRecord> records = costRepository.findAll();
        double totalCost = records.stream()
            .mapToDouble(c -> c.getAmountInr() != null ? c.getAmountInr().doubleValue() : 0.0)
            .sum();
        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .success(true)
            .intent("COST_QUERY")
            .source("POSTGRESQL")
            .message(String.format("💰 Total fleet operating costs recorded: **₹%,.2f** across %,d cost entries.", totalCost, records.size()))
            .quickActions(List.of("📊 Fleet Summary", "🚗 Vehicles"))
            .build();
    }

    /** PROGRESSIVE PENALTY QUERY HANDLER (Grounded in PostgreSQL) */
    private ChatResponseDTO handlePenaltyQuery(String sessionId, String normalized, IntentClassifier.ClassifiedIntent classified, Map<String, String> ctx) {
        try {
            // 1. Rules query: "what are the penalty rules for overspeeding?", "what is the penalty for a third harsh braking alert?", etc.
            if (normalized.contains("rule") || normalized.contains("formula") || normalized.contains("how does") || normalized.contains("how do")
                    || normalized.contains("penalty for a") || normalized.contains("fine for a") || normalized.contains("penalty for the")
                    || (normalized.contains("penalty for") && !normalized.contains("driver") && !normalized.matches(".*\\bdrv\\d{3}\\b.*"))) {
                String targetType = "Over-speeding Alert";
                if (normalized.contains("brak")) targetType = "Harsh Braking Alert";
                else if (normalized.contains("accel")) targetType = "Harsh Acceleration Alert";
                else if (normalized.contains("fatigue")) targetType = "Fatigue Alert";
                else if (normalized.contains("night")) targetType = "Night Driving Alert";
                else if (normalized.contains("gps") || normalized.contains("disconnect")) targetType = "GPS Disconnect Alert";
                else if (normalized.contains("ignition")) targetType = "Ignition Alert";

                AlertPenaltyConfig config = penaltyService.resolveConfigForType(targetType);
                BigDecimal base = config.getDefaultFineAmount();
                BigDecimal inc = config.getPenaltyIncrement() != null ? config.getPenaltyIncrement() : BigDecimal.valueOf(30);
                int thresh = config.getWarningThreshold() != null ? config.getWarningThreshold() : 2;

                String response = String.format("""
                    ⚖️ **Progressive Penalty Rules for %s**:

                    • **Occurrence 1 & 2**: Official Warning Notification (₹0.00 penalty)
                    • **Occurrence 3**: First Monetary Fine = Base Penalty of **₹%,.2f**
                    • **Occurrence 4**: Base Penalty + ₹%,.2f = **₹%,.2f**
                    • **Occurrence 5+**: Incrementally increases by **₹%,.2f** per subsequent violation

                    *Formula*: Penalty for occurrence *n* (where *n* ≥ 3) = Base (₹%.0f) + (*n* - 3) × ₹%.0f.
                    Occurrences and penalties are tracked strictly and independently per driver and alert type.
                    """, targetType, base.doubleValue(), inc.doubleValue(), base.add(inc).doubleValue(),
                    inc.doubleValue(), base.doubleValue(), inc.doubleValue());

                return ChatResponseDTO.builder()
                        .sessionId(sessionId)
                        .success(true)
                        .intent("PENALTY_QUERY")
                        .source("POSTGRESQL")
                        .message(response)
                        .quickActions(List.of("🏆 Top Penalties", "⚠️ Warning Alerts", "📊 Fleet Penalties"))
                        .build();
            }

            // 2. Highest penalized driver: "which driver has the highest total penalties?"
            if (normalized.contains("highest") || normalized.contains("most penalized") || normalized.contains("maximum penalt")) {
                FleetPenaltySummaryDTO fleet = penaltyService.getFleetPenaltySummary();
                if (fleet.getHighestPenalizedDriverCode() == null) {
                    return ChatResponseDTO.builder()
                            .sessionId(sessionId)
                            .success(true)
                            .intent("PENALTY_QUERY")
                            .source("POSTGRESQL")
                            .message("✅ Currently, no driver has accumulated active financial penalties across the fleet.")
                            .quickActions(List.of("📊 Fleet Summary", "👥 Drivers"))
                            .build();
                }

                String msg = String.format("🚨 **Driver with Highest Cumulative Penalties**:\n\n" +
                        "• **Driver**: %s (%s)\n" +
                        "• **Total Accumulated Penalties**: **₹%,.2f**\n" +
                        "• **Fleet Total Penalties**: ₹%,.2f across %,d penalty-bearing alerts.",
                        fleet.getHighestPenalizedDriverName(), fleet.getHighestPenalizedDriverCode(),
                        fleet.getHighestDriverPenaltyTotal().doubleValue(),
                        fleet.getTotalFleetPenalties().doubleValue(),
                        fleet.getTotalPenaltyBearingAlerts());

                return ChatResponseDTO.builder()
                        .sessionId(sessionId)
                        .success(true)
                        .intent("PENALTY_QUERY")
                        .source("POSTGRESQL")
                        .message(msg)
                        .quickActions(List.of("🏆 Top 5 Drivers", "📊 Fleet Summary"))
                        .build();
            }

            // 3. Top five drivers by penalties: "show the top five drivers by cumulative penalties", "list the top 5 penalized drivers", etc.
            if (normalized.contains("top") && (normalized.contains("penalt") || normalized.contains("penaliz") || normalized.contains("fine"))) {
                List<Object[]> rankings = penaltyRepository.findDriverPenaltyRankings();
                if (rankings.isEmpty()) {
                    return ChatResponseDTO.builder()
                            .sessionId(sessionId)
                            .success(true)
                            .intent("PENALTY_QUERY")
                            .source("POSTGRESQL")
                            .message("✅ No driver penalty records found in PostgreSQL ledger.")
                            .build();
                }

                StringBuilder sb = new StringBuilder("🏆 **Top Drivers by Cumulative Penalties**:\n\n");
                sb.append("| Rank | Driver Code | Driver Name | Cumulative Penalties |\n");
                sb.append("| :--- | :--- | :--- | :--- |\n");
                int limit = Math.min(5, rankings.size());
                for (int i = 0; i < limit; i++) {
                    Object[] row = rankings.get(i);
                    String code = (String) row[1];
                    String name = (String) row[2];
                    BigDecimal amt = (BigDecimal) row[3];
                    sb.append(String.format("| %d | **%s** | %s | **₹%,.2f** |\n", i + 1, code, name, amt != null ? amt.doubleValue() : 0.0));
                }

                return ChatResponseDTO.builder()
                        .sessionId(sessionId)
                        .success(true)
                        .intent("PENALTY_QUERY")
                        .source("POSTGRESQL")
                        .message(sb.toString())
                        .quickActions(List.of("📊 Fleet Summary", "⚠️ Warning Alerts"))
                        .build();
            }

            // 4. Latest alert penalty: "what penalty was applied to the latest alert?"
            if (normalized.contains("latest alert") || normalized.contains("recent alert") || normalized.contains("last alert")) {
                Optional<DriverPenalty> latest = penaltyRepository.findFirstByOrderByDateAppliedDesc();
                if (latest.isEmpty()) {
                    return ChatResponseDTO.builder()
                            .sessionId(sessionId)
                            .success(true)
                            .intent("PENALTY_QUERY")
                            .source("POSTGRESQL")
                            .message("No penalties have been recorded for alerts yet.")
                            .build();
                }
                DriverPenalty p = latest.get();
                String driverInfo = p.getDriver() != null ? p.getDriver().getName() + " (" + p.getDriver().getCode() + ")" : "Unknown Driver";
                String alertRef = p.getAlert() != null && p.getAlert().getReference() != null ? p.getAlert().getReference() : "N/A";

                String msg = String.format("🚨 **Latest Alert Penalty Record**:\n\n" +
                        "• **Alert Reference**: `%s`\n" +
                        "• **Driver**: %s\n" +
                        "• **Alert Type**: %s\n" +
                        "• **Occurrence**: #%d (%s)\n" +
                        "• **Applied Penalty Fine**: **₹%,.2f**\n" +
                        "• **Applied Date**: %s\n" +
                        "• **Status**: %s",
                        alertRef, driverInfo, p.getAlertType(), p.getOccurrenceNumber(),
                        p.getClassification(), p.getMonetaryAmount().doubleValue(),
                        p.getDateApplied() != null ? p.getDateApplied().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : "N/A",
                        p.getStatus());

                return ChatResponseDTO.builder()
                        .sessionId(sessionId)
                        .success(true)
                        .intent("PENALTY_QUERY")
                        .source("POSTGRESQL")
                        .message(msg)
                        .quickActions(List.of("📊 Fleet Penalties", "👥 Drivers"))
                        .build();
            }

            // 5. Zero monetary penalty count: "how many alerts have no monetary penalty?", "how many zero-monetary penalties have been issued?"
            if (normalized.contains("no monetary") || normalized.contains("zero monetary") || normalized.contains("zero-monetary")
                    || normalized.contains("no penalt") || normalized.contains("zero penalt") || normalized.contains("zero-penalt")
                    || normalized.contains("without fine") || normalized.contains("without penalt")) {
                long warnings = penaltyRepository.countByClassificationAndStatus("WARNING", "ACTIVE")
                        + penaltyRepository.countByClassificationAndStatus("WARNING", "COMPLETED");
                long totalAlerts = alertRepository.count();
                long penaltyBearing = penaltyRepository.countByClassificationAndStatus("PENALTY", "ACTIVE");
                long zeroPenaltyAlerts = totalAlerts - penaltyBearing;

                String msg = String.format("🛡️ **Zero Monetary Penalty Analysis**:\n\n" +
                        "• **Total Alerts with ₹0 Penalty**: **%,d** alerts (including %,d warning-only occurrences and historical records)\n" +
                        "• **Penalty-Bearing Alerts**: %,d alerts\n" +
                        "• **Total Alerts Recorded**: %,d",
                        zeroPenaltyAlerts, warnings, penaltyBearing, totalAlerts);

                return ChatResponseDTO.builder()
                        .sessionId(sessionId)
                        .success(true)
                        .intent("PENALTY_QUERY")
                        .source("POSTGRESQL")
                        .message(msg)
                        .quickActions(List.of("⚠️ Warning Alerts", "📊 Fleet Penalties"))
                        .build();
            }

            // 6. Penalty history for a date / month: "show penalty history for September 2026"
            YearMonth ym = extractYearMonth(normalized);
            DateRange dr = parseDateRange(normalized);
            if (ym != null || dr != null) {
                LocalDateTime start = ym != null ? ym.atDay(1).atStartOfDay() : dr.startDateTime();
                LocalDateTime end = ym != null ? ym.atEndOfMonth().plusDays(1).atStartOfDay() : dr.endDateTime();
                String periodLabel = ym != null ? ym.getMonth().name() + " " + ym.getYear() : dr.label;

                List<DriverPenalty> pList = penaltyRepository.findAll().stream()
                        .filter(p -> p.getDateApplied() != null && !p.getDateApplied().isBefore(start) && p.getDateApplied().isBefore(end))
                        .sorted((a, b) -> b.getDateApplied().compareTo(a.getDateApplied()))
                        .collect(Collectors.toList());

                double totalFine = pList.stream()
                        .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                        .mapToDouble(p -> p.getMonetaryAmount() != null ? p.getMonetaryAmount().doubleValue() : 0.0)
                        .sum();

                long warningOnly = pList.stream().filter(p -> "WARNING".equalsIgnoreCase(p.getClassification())).count();
                long penaltyBearing = pList.stream().filter(p -> "PENALTY".equalsIgnoreCase(p.getClassification())).count();

                StringBuilder sb = new StringBuilder(String.format("📅 **Penalty History for %s**:\n\n" +
                        "• Total Events: %,d (%d warnings, %d financial penalties)\n" +
                        "• Total Penalties Accumulated: **₹%,.2f**\n\n",
                        periodLabel, pList.size(), warningOnly, penaltyBearing, totalFine));

                if (!pList.isEmpty()) {
                    sb.append("| Date | Driver | Alert Type | Occur # | Classification | Fine |\n");
                    sb.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
                    for (DriverPenalty p : pList.stream().limit(8).collect(Collectors.toList())) {
                        String dCode = p.getDriver() != null ? p.getDriver().getCode() : "N/A";
                        String dt = p.getDateApplied() != null ? p.getDateApplied().format(DateTimeFormatter.ofPattern("dd-MMM HH:mm")) : "N/A";
                        sb.append(String.format("| %s | %s | %s | #%d | %s | ₹%,.2f |\n",
                                dt, dCode, p.getAlertType(), p.getOccurrenceNumber(), p.getClassification(),
                                p.getMonetaryAmount() != null ? p.getMonetaryAmount().doubleValue() : 0.0));
                    }
                }

                return ChatResponseDTO.builder()
                        .sessionId(sessionId)
                        .success(true)
                        .intent("PENALTY_QUERY")
                        .source("POSTGRESQL")
                        .message(sb.toString())
                        .quickActions(List.of("📊 Fleet Summary", "👥 Drivers"))
                        .build();
            }

            // 7. Driver-specific queries: "What is the total penalty for DRV009?", "How many warning-only alerts has DRV009 received?", etc.
            Driver d = resolveDriverFromMessage(normalized, ctx);
            if (d == null) {
                Matcher dm = Pattern.compile("drv\\d{3}").matcher(normalized);
                if (dm.find()) {
                    String code = dm.group().toUpperCase();
                    d = driverRepository.findByCode(code).orElse(null);
                }
            }

            if (d != null) {
                ctx.put("last_driver_id", String.valueOf(d.getId()));
                ctx.put("last_driver_code", d.getCode());
                ctx.put("last_driver_name", d.getName());
                ctx.put("last_subject", "DRIVER");

                DriverPenaltySummaryDTO summary = penaltyService.getDriverPenaltySummary(d.getId());

                // Warning-only specific: "How many warning-only alerts has DRV009 received?"
                if (normalized.contains("warning") || normalized.contains("warning-only") || normalized.contains("warnings")) {
                    String msg = String.format("⚠️ **Driver %s (%s)** has received **%,d warning-only alert(s)** (₹0.00 fine applied) out of %,d total qualifying alert events.",
                            d.getName(), d.getCode(), summary.getWarningOnlyCount(), summary.getTotalQualifyingAlerts());
                    return ChatResponseDTO.builder()
                            .sessionId(sessionId)
                            .success(true)
                            .intent("PENALTY_QUERY")
                            .source("POSTGRESQL")
                            .message(msg)
                            .quickActions(List.of("💰 Total Penalties", "📊 Driver Stats"))
                            .build();
                }

                // Specific alert type: "How many penalties has DRV009 accumulated for harsh braking?"
                String specificType = null;
                if (normalized.contains("brak")) specificType = "Harsh Braking Alert";
                else if (normalized.contains("overspeed") || normalized.contains("speed")) specificType = "Over-speeding Alert";
                else if (normalized.contains("accel")) specificType = "Harsh Acceleration Alert";
                else if (normalized.contains("fatigue")) specificType = "Fatigue Alert";
                else if (normalized.contains("night")) specificType = "Night Driving Alert";
                else if (normalized.contains("gps") || normalized.contains("disconnect")) specificType = "GPS Disconnect Alert";

                if (specificType != null) {
                    BigDecimal fineForType = summary.getPenaltiesByAlertType().getOrDefault(specificType, BigDecimal.ZERO);
                    long occurrencesForType = summary.getOccurrencesByAlertType().getOrDefault(specificType, 0L);

                    String msg = String.format("🚨 **%s Penalties for Driver %s (%s)**:\n\n" +
                            "• **Total Violations for %s**: %,d occurrence(s)\n" +
                            "• **Penalties Accumulated for %s**: **₹%,.2f**\n" +
                            "• **Driver Cumulative Penalties across all types**: ₹%,.2f",
                            specificType, d.getName(), d.getCode(),
                            specificType, occurrencesForType,
                            specificType, fineForType.doubleValue(),
                            summary.getTotalPenaltiesAccumulated().doubleValue());

                    return ChatResponseDTO.builder()
                            .sessionId(sessionId)
                            .success(true)
                            .intent("PENALTY_QUERY")
                            .source("POSTGRESQL")
                            .message(msg)
                            .quickActions(List.of("💰 Cumulative Total", "📊 Driver Stats"))
                            .build();
                }

                // General driver penalty summary: "What is the total penalty for DRV009?"
                String msg = String.format("💰 **Progressive Penalty Summary for %s (%s)**:\n\n" +
                        "• **Cumulative Total Penalties**: **₹%,.2f**\n" +
                        "• **Penalty-Bearing Alerts**: %,d\n" +
                        "• **Warning-Only Alerts**: %,d (₹0.00)\n" +
                        "• **Total Qualifying Alerts**: %,d",
                        d.getName(), d.getCode(),
                        summary.getTotalPenaltiesAccumulated().doubleValue(),
                        summary.getPenaltyBearingCount(),
                        summary.getWarningOnlyCount(),
                        summary.getTotalQualifyingAlerts());

                return ChatResponseDTO.builder()
                        .sessionId(sessionId)
                        .success(true)
                        .intent("PENALTY_QUERY")
                        .source("POSTGRESQL")
                        .message(msg)
                        .quickActions(List.of("⚠️ Warning Alerts", "📊 Driver Stats", "🏆 Driver Ranking"))
                        .build();
            }

            // Fallback: fleet penalty summary
            FleetPenaltySummaryDTO fleet = penaltyService.getFleetPenaltySummary();
            String msg = String.format("📊 **TrackFleet Progressive Penalty Overview**:\n\n" +
                    "• **Total Fleet Penalties**: **₹%,.2f**\n" +
                    "• **Penalty-Bearing Alerts**: %,d\n" +
                    "• **Warning-Only Alerts**: %,d\n" +
                    "• **Highest Penalized Driver**: %s (%s) — ₹%,.2f",
                    fleet.getTotalFleetPenalties().doubleValue(),
                    fleet.getTotalPenaltyBearingAlerts(),
                    fleet.getTotalWarningOnlyAlerts(),
                    fleet.getHighestPenalizedDriverName() != null ? fleet.getHighestPenalizedDriverName() : "None",
                    fleet.getHighestPenalizedDriverCode() != null ? fleet.getHighestPenalizedDriverCode() : "N/A",
                    fleet.getHighestDriverPenaltyTotal().doubleValue());

            return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .success(true)
                    .intent("PENALTY_QUERY")
                    .source("POSTGRESQL")
                    .message(msg)
                    .quickActions(List.of("🏆 Top 5 Drivers", "⚖️ Penalty Rules", "👥 Drivers"))
                    .build();

        } catch (Exception e) {
            log.error("Error handling progressive penalty query: {}", e.getMessage(), e);
            return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .success(false)
                    .intent("PENALTY_QUERY")
                    .source("POSTGRESQL")
                    .message("⚠️ An error occurred while retrieving penalty records from the database: " + e.getMessage())
                    .build();
        }
    }

    /** Clear in-memory session context */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionContext.remove(sessionId);
        }
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

    // =========================================================================
    // ALERT-BASED REPORT
    // =========================================================================

    /**
     * Handles ALERT_REPORT intent.
     * Resolves scope (Driver vs Fleet), builds a ReportRequestDTO with alert filters
     * (alertTypes, severity, status, datePreset/range), invokes ReportService, and
     * returns a ChatResponseDTO with inline PDF + download links.
     */
    private ChatResponseDTO handleAlertReportIntent(String sessionId, String normalized,
                                                    IntentClassifier.ClassifiedIntent classified,
                                                    Map<String, String> ctx) {
        // --- 1. Resolve date range ---
        LocalDate now = LocalDate.now();
        LocalDate startDate, endDate;
        String datePreset = null;
        String periodLabel;

        if (classified != null && classified.getDateFrom() != null) {
            startDate = classified.getDateFrom();
            endDate = classified.getDateTo() != null ? classified.getDateTo() : startDate;
            periodLabel = startDate + " to " + endDate;
        } else {
            DateRange dr = parseDateRange(normalized);
            if (dr != null) {
                startDate = dr.startDate;
                endDate = dr.endDate;
                periodLabel = dr.label;
            } else {
                // fall back to month-name or today
                YearMonth ym = (classified != null) ? classified.getYearMonth() : null;
                if (ym == null) ym = extractYearMonth(normalized);
                if (ym != null) {
                    startDate = ym.atDay(1);
                    endDate = ym.atEndOfMonth();
                    periodLabel = ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + ym.getYear();
                } else if (normalized.contains("this week")) {
                    startDate = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    endDate = now;
                    periodLabel = "This Week";
                    datePreset = "THIS_WEEK";
                } else if (normalized.contains("last week")) {
                    LocalDate end = now.with(java.time.temporal.TemporalAdjusters.previous(java.time.DayOfWeek.SUNDAY));
                    startDate = end.minusDays(6);
                    endDate = end;
                    periodLabel = "Last Week";
                    datePreset = "LAST_WEEK";
                } else if (normalized.contains("this month")) {
                    startDate = now.withDayOfMonth(1);
                    endDate = now;
                    periodLabel = "This Month";
                    datePreset = "THIS_MONTH";
                } else if (normalized.contains("last month")) {
                    LocalDate lm = now.minusMonths(1);
                    startDate = lm.withDayOfMonth(1);
                    endDate = lm.withDayOfMonth(lm.lengthOfMonth());
                    periodLabel = "Last Month";
                    datePreset = "LAST_MONTH";
                } else if (normalized.contains("yesterday")) {
                    startDate = now.minusDays(1);
                    endDate = startDate;
                    periodLabel = "Yesterday";
                    datePreset = "YESTERDAY";
                } else if (normalized.contains("today")) {
                    startDate = now;
                    endDate = now;
                    periodLabel = "Today";
                    datePreset = "TODAY";
                } else {
                    // No date specified — default to last 30 days
                    startDate = now.minusDays(29);
                    endDate = now;
                    periodLabel = startDate + " to " + endDate;
                }
            }
        }

        // --- 2. Resolve alert type filter ---
        String alertTypeHint = (classified != null) ? classified.getAlertTypeHint() : null;
        List<String> alertTypes = (alertTypeHint != null) ? List.of(alertTypeHint) : null;

        // --- 3. Resolve severity/status ---
        String severity = (classified != null) ? classified.getSeverityHint() : null;
        String status   = (classified != null) ? classified.getStatusHint()   : null;

        // --- 4. Resolve scope: Driver vs Fleet ---
        IntentClassifier.Scope scope = (classified != null) ? classified.getScope() : IntentClassifier.Scope.UNKNOWN;
        Driver driver = null;
        if (scope == IntentClassifier.Scope.DRIVER || classified == null ||
                scope == IntentClassifier.Scope.UNKNOWN) {
            // Try to extract driver
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
        }

        // Fleet scope: no specific driver, or explicitly fleet
        boolean isFleet = (scope == IntentClassifier.Scope.FLEET) || (driver == null &&
            (normalized.contains("fleet") || normalized.contains("all drivers") || normalized.contains("overall") || normalized.contains("company")));

        // --- 5. Build request & generate ---
        try {
            ReportRequestDTO req = new ReportRequestDTO();
            req.setReportType("ALERT_BASED");
            req.setStartDate(startDate);
            req.setEndDate(endDate);
            if (datePreset != null) req.setDatePreset(datePreset);
            if (alertTypes != null) req.setAlertTypes(alertTypes);
            if (severity != null) req.setSeverity(severity);
            if (status != null) req.setStatus(status);

            String intentName;
            ReportResponseDTO report;
            String successMsg;

            if (isFleet || driver == null) {
                // Fleet-wide alert report
                intentName = "FLEET_ALERT_REPORT";
                report = reportService.generateFleetAlertReport(req);

                // Build human-friendly filter summary
                StringBuilder filterSummary = new StringBuilder();
                if (alertTypeHint != null) filterSummary.append("• **Alert Type:** ").append(alertTypeHint).append("\n");
                if (severity != null)      filterSummary.append("• **Severity:** ").append(severity).append("\n");
                if (status != null)        filterSummary.append("• **Status:** ").append(status).append("\n");
                filterSummary.append("• **Period:** ").append(periodLabel);

                successMsg = String.format(
                    "✅ **Fleet Alert-Based Report** is ready!\n\n%s\n\nClick below to view or download the PDF.",
                    filterSummary);
            } else {
                // Driver-specific alert report
                intentName = "DRIVER_ALERT_REPORT";
                req.setDriverId(driver.getId());
                report = reportService.generateDriverAlertReport(req);

                StringBuilder filterSummary = new StringBuilder();
                filterSummary.append("• **Driver:** ").append(driver.getName()).append(" (").append(driver.getCode()).append(")\n");
                if (alertTypeHint != null) filterSummary.append("• **Alert Type:** ").append(alertTypeHint).append("\n");
                if (severity != null)      filterSummary.append("• **Severity:** ").append(severity).append("\n");
                if (status != null)        filterSummary.append("• **Status:** ").append(status).append("\n");
                filterSummary.append("• **Period:** ").append(periodLabel);

                successMsg = String.format(
                    "✅ **Alert-Based Report for %s** is ready!\n\n%s\n\nClick below to view or download the PDF.",
                    driver.getName(), filterSummary);

                ctx.put("last_subject", "DRIVER");
                ctx.put("last_driver_id", String.valueOf(driver.getId()));
                ctx.put("last_driver_name", driver.getName());
                ctx.put("last_driver_code", driver.getCode());
            }

            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent(intentName)
                .message(successMsg)
                .reportId(report.getReportId())
                .pdfUrl(report.getPdfUrl())
                .downloadUrl(report.getDownloadUrl())
                .data(report.getReportData())
                .quickActions(List.of(
                    "📊 Fleet Summary",
                    "🏆 Driver Rankings",
                    "⚠️ All Open Alerts"
                ))
                .build();

        } catch (Exception e) {
            log.error("Error generating alert-based report", e);
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("ALERT_REPORT")
                .message("Sorry, I encountered an error generating the alert-based report: " + e.getMessage() +
                    "\n\nYou can generate the report directly from the **Reports** page by selecting \"Alert-Based Report\".")
                .quickActions(List.of("📊 Fleet Summary", "⚠️ View Open Alerts", "🏆 Driver Rankings"))
                .build();
        }
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
        if (message == null || message.isBlank()) return null;
        String lower = message.toLowerCase().trim();

        // 1. Direct DRV code (e.g. DRV001, DRV008, DRV009)
        Matcher m = Pattern.compile("\\bdrv\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(lower);
        if (m.find()) {
            String code = m.group().toUpperCase();
            Optional<Driver> d = driverRepository.findByCode(code);
            if (d.isPresent()) return d.get();
        }

        // Common stopwords that must never match as driver names
        Set<String> blacklistedTokens = Set.of(
            "driver", "drivers", "vehicle", "vehicles", "fleet", "alert", "alerts",
            "report", "reports", "trip", "trips", "test", "email", "mail", "number",
            "count", "maximum", "minimum", "highest", "lowest", "most", "fewest",
            "best", "worst", "safest", "generated", "occurred", "today", "yesterday",
            "september", "october", "august", "month", "year", "week", "details",
            "summary", "show", "give", "tell", "which", "what", "find"
        );

        // 2. Name match (case-insensitive with word boundaries)
        List<Driver> all = driverRepository.findAll();

        // 2a. Full name exact match (e.g. "vikram singh", "deepak joshi")
        for (Driver d : all) {
            if (d.getName() == null) continue;
            String dName = d.getName().trim().toLowerCase();
            if (dName.contains("no email") || dName.contains("test") || (d.getCode() != null && d.getCode().startsWith("DRV_NO_MAIL"))) {
                continue; // Skip dummy / test drivers
            }
            Pattern fullPat = Pattern.compile("\\b" + Pattern.quote(dName) + "\\b");
            if (fullPat.matcher(lower).find()) {
                return d;
            }
        }

        // 2b. First/last name match with word boundaries (ignoring blacklisted tokens)
        for (Driver d : all) {
            if (d.getName() == null) continue;
            String dName = d.getName().trim().toLowerCase();
            if (dName.contains("no email") || dName.contains("test") || (d.getCode() != null && d.getCode().startsWith("DRV_NO_MAIL"))) {
                continue;
            }
            String[] parts = dName.split("\\s+");
            for (String part : parts) {
                if (part.length() >= 3 && !blacklistedTokens.contains(part)) {
                    Pattern partPat = Pattern.compile("\\b" + Pattern.quote(part) + "\\b");
                    if (partPat.matcher(lower).find()) {
                        return d;
                    }
                }
            }
        }

        // 3. Pronoun reference ONLY if an explicit pronoun is present
        if (lower.matches(".*\\b(he|him|his|she|her)\\b.*")) {
            if (ctx != null && ctx.containsKey("last_driver_id")) {
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

    /** Map structured LLM response to ClassifiedIntent with validated types */
    private IntentClassifier.ClassifiedIntent mapLlmResponseToClassifiedIntent(com.gps.tracking.dto.LlmIntentResponseDTO res) {
        if (res == null || res.getIntent() == null) return null;
        try {
            IntentClassifier.Intent intent = IntentClassifier.Intent.valueOf(res.getIntent().toUpperCase().trim());
            IntentClassifier.Scope scope = IntentClassifier.Scope.UNKNOWN;
            if (res.getScope() != null) {
                try {
                    scope = IntentClassifier.Scope.valueOf(res.getScope().toUpperCase().trim());
                } catch (IllegalArgumentException ignored) {}
            }

            IntentClassifier.Period period = null;
            if (res.getPeriod() != null) {
                try {
                    period = IntentClassifier.Period.valueOf(res.getPeriod().toUpperCase().trim());
                } catch (IllegalArgumentException ignored) {}
            }

            LocalDate dateFrom = null;
            if (res.getDateFrom() != null && !res.getDateFrom().isBlank()) {
                try { dateFrom = LocalDate.parse(res.getDateFrom().trim()); } catch (Exception ignored) {}
            }
            LocalDate dateTo = null;
            if (res.getDateTo() != null && !res.getDateTo().isBlank()) {
                try { dateTo = LocalDate.parse(res.getDateTo().trim()); } catch (Exception ignored) {}
            }

            YearMonth yearMonth = null;
            if (res.getYearMonth() != null && !res.getYearMonth().isBlank()) {
                try { yearMonth = YearMonth.parse(res.getYearMonth().trim()); } catch (Exception ignored) {}
            }

            int limit = (res.getLimit() != null && res.getLimit() > 0) ? res.getLimit() : 5;

            return new IntentClassifier.ClassifiedIntent(
                intent, scope, period,
                res.getDriverCode(), res.getDriverName(),
                res.getVehicleCode(), res.getVehicleRegistration(),
                res.getAlertType(),
                res.getSeverityHint(), res.getStatusHint(),
                dateFrom, dateTo, yearMonth,
                limit, 10.0
            );
        } catch (IllegalArgumentException ex) {
            log.debug("LLM suggested intent '{}' is not recognized in local enum: {}", res.getIntent(), ex.getMessage());
            return null;
        }
    }

    private ChatResponseDTO handleSimulationQuery(String sessionId, String normalized, String originalMessage) {
        com.gps.tracking.dto.SimulationStateDTO state = simulationService.getCurrentState();

        // 1. Current Speed Query
        if (normalized.contains("speed") && (normalized.contains("current") || normalized.contains("now") || normalized.contains("truck's speed") || normalized.contains("truck speed"))) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("SIMULATION_QUERY")
                .message(String.format("🚚 **Simulated Truck Current Speed**\n\n"
                    + "• **Vehicle:** %s (%s)\n"
                    + "• **Current Speed:** **%.1f km/h**\n"
                    + "• **Speed Limit:** %.1f km/h\n"
                    + "• **Road Section:** %s\n"
                    + "• **Status:** %s\n\n"
                    + "ℹ️ *Data Source: Active TrackFleet Simulation Engine*",
                    state.getVehicleRegistrationNumber(), state.getMakeModel(),
                    state.getCurrentSpeedKmph() != null ? state.getCurrentSpeedKmph().doubleValue() : 0.0,
                    state.getSpeedLimitKmph() != null ? state.getSpeedLimitKmph().doubleValue() : 60.0,
                    state.getRoadName(), state.getStatus()))
                .quickActions(List.of("📈 Distance Travelled", "⚠️ Simulation Alerts", "⚡ Max Speed"))
                .build();
        }

        // 2. Distance Travelled Query
        if (normalized.contains("far") || normalized.contains("distance") || normalized.contains("travelled") || normalized.contains("traveled")) {
            long mins = state.getElapsedSeconds() / 60;
            long secs = state.getElapsedSeconds() % 60;
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("SIMULATION_QUERY")
                .message(String.format("📍 **Simulated Journey Progress**\n\n"
                    + "• **Distance Travelled:** **%.2f km**\n"
                    + "• **Elapsed Time:** %02d min %02d sec\n"
                    + "• **Route:** %s (%.1f%% completed)\n"
                    + "• **Current Location:** %s (%.4f, %.4f)\n"
                    + "• **Destination:** %s\n\n"
                    + "ℹ️ *Data Source: Active TrackFleet Simulation Engine*",
                    state.getDistanceTravelledKm() != null ? state.getDistanceTravelledKm().doubleValue() : 0.0,
                    mins, secs,
                    state.getRouteName(), state.getProgressPercent(),
                    state.getRoadName(),
                    state.getLatitude() != null ? state.getLatitude().doubleValue() : 0.0,
                    state.getLongitude() != null ? state.getLongitude().doubleValue() : 0.0,
                    state.getDestination()))
                .quickActions(List.of("🚚 Current Speed", "⚠️ Simulation Alerts", "💰 Simulated Penalties"))
                .build();
        }

        // 3. Max Speed Query
        if (normalized.contains("max") || normalized.contains("highest speed") || normalized.contains("top speed")) {
            BigDecimal maxSpd = (state.getSummary() != null && state.getSummary().getMaxSpeedKmph() != null)
                    ? state.getSummary().getMaxSpeedKmph()
                    : (state.getCurrentSpeedKmph() != null ? state.getCurrentSpeedKmph() : BigDecimal.ZERO);
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("SIMULATION_QUERY")
                .message(String.format("⚡ **Simulated Maximum Speed**\n\n"
                    + "• **Maximum Speed Recorded:** **%.1f km/h**\n"
                    + "• **Current Speed:** %.1f km/h\n"
                    + "• **Vehicle:** %s\n"
                    + "• **Driver:** %s\n\n"
                    + "ℹ️ *Data Source: Active TrackFleet Simulation Engine*",
                    maxSpd.doubleValue(),
                    state.getCurrentSpeedKmph() != null ? state.getCurrentSpeedKmph().doubleValue() : 0.0,
                    state.getVehicleRegistrationNumber(), state.getDriverName()))
                .quickActions(List.of("🚚 Current Speed", "⚠️ Simulation Alerts", "📊 Journey Summary"))
                .build();
        }

        // 4. Overspeeding alerts count
        if (normalized.contains("overspeed") || normalized.contains("over-speed")) {
            long overspeedCount = (state.getAllAlerts() != null) ? state.getAllAlerts().stream()
                .filter(a -> a.getAlertType() != null && a.getAlertType().toLowerCase().contains("overspeed"))
                .count() : 0;
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("SIMULATION_QUERY")
                .message(String.format("⚠️ **Simulated Overspeeding Alerts**\n\n"
                    + "• **Total Overspeeding Alerts:** **%d**\n"
                    + "• **Configured Limit:** %.1f km/h\n"
                    + "• **Status:** %s\n\n"
                    + "ℹ️ *Data Source: Active TrackFleet Simulation Engine*",
                    overspeedCount,
                    state.getSpeedLimitKmph() != null ? state.getSpeedLimitKmph().doubleValue() : 60.0,
                    state.getStatus()))
                .quickActions(List.of("⚠️ All Simulation Alerts", "💰 Simulated Penalties", "🚚 Current Speed"))
                .build();
        }

        // 5. Penalties Query
        if (normalized.contains("penalt") || normalized.contains("fine")) {
            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("SIMULATION_QUERY")
                .message(String.format("💰 **Simulated Driver Penalties**\n\n"
                    + "• **Driver:** %s (%s)\n"
                    + "• **Total Cumulative Simulated Penalty:** **₹%.2f**\n"
                    + "• **Total Alerts in Simulation:** %d\n"
                    + "• **Note:** Progressive system generates warnings for occurrences #1 and #2 (₹0.00), and increasing fines for occurrences #3+.\n\n"
                    + "ℹ️ *Data Source: TrackFleet Progressive Penalty Engine (Simulation Mode)*",
                    state.getDriverName(), state.getDriverCode(),
                    state.getTotalPenaltyAmount() != null ? state.getTotalPenaltyAmount().doubleValue() : 0.0,
                    state.getTotalAlertsCount()))
                .quickActions(List.of("⚠️ All Simulation Alerts", "📈 Distance Travelled", "🚚 Current Speed"))
                .build();
        }

        // 6. Which Alerts Occurred / All alerts
        if (normalized.contains("alert") || normalized.contains("violation")) {
            List<com.gps.tracking.dto.AlertDTO> alerts = state.getAllAlerts();
            if (alerts == null || alerts.isEmpty()) {
                return ChatResponseDTO.builder()
                    .sessionId(sessionId)
                    .intent("SIMULATION_QUERY")
                    .message(String.format("🛡️ **Simulation Alert Status**\n\n"
                        + "No alerts have been generated yet in the current simulation session (%s).\n"
                        + "The truck is driving safely at **%.1f km/h** on **%s**.\n\n"
                        + "💡 *Tip: Use the 'Trigger Test Event' controls on the Live Simulation page to demonstrate Overspeeding or Harsh Braking alert generation.*",
                        state.getRouteName(),
                        state.getCurrentSpeedKmph() != null ? state.getCurrentSpeedKmph().doubleValue() : 0.0,
                        state.getRoadName()))
                    .quickActions(List.of("🚚 Current Speed", "📈 Distance Travelled", "⚡ Max Speed"))
                    .build();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("⚠️ **Alerts Occurred During Simulation (%d Total)**\n\n", alerts.size()));
            for (int i = 0; i < Math.min(alerts.size(), 8); i++) {
                com.gps.tracking.dto.AlertDTO a = alerts.get(i);
                sb.append(String.format("%d. **%s** [%s]\n", i + 1, a.getAlertType(), a.getSeverity()));
                sb.append(String.format("   • Location: %s (%.1f km/h)\n", a.getRoadName(), a.getSpeedKmph() != null ? a.getSpeedKmph().doubleValue() : 0.0));
                if (a.getOccurrenceNumber() != null) {
                    sb.append(String.format("   • Progressive Occurrence: #%d (%s - ₹%.2f)\n",
                        a.getOccurrenceNumber(), a.getPenaltyClassification(), a.getPenaltyAmount() != null ? a.getPenaltyAmount().doubleValue() : 0.0));
                }
            }
            if (alerts.size() > 8) {
                sb.append(String.format("\n*...and %d more alerts in simulation log.*", alerts.size() - 8));
            }
            sb.append("\n\nℹ️ *Data Source: Active TrackFleet Simulation Engine*");

            return ChatResponseDTO.builder()
                .sessionId(sessionId)
                .intent("SIMULATION_QUERY")
                .message(sb.toString())
                .quickActions(List.of("💰 Simulated Penalties", "🚚 Current Speed", "📊 Journey Summary"))
                .build();
        }

        // 7. Default Simulation Overview
        return ChatResponseDTO.builder()
            .sessionId(sessionId)
            .intent("SIMULATION_QUERY")
            .message(String.format("🚚 **TrackFleet Live Simulation Status**\n\n"
                + "• **Status:** **%s**\n"
                + "• **Route:** %s\n"
                + "• **Vehicle:** %s (%s)\n"
                + "• **Driver:** %s (%s)\n"
                + "• **Current Speed:** %.1f km/h (Limit: %.1f km/h)\n"
                + "• **Distance Travelled:** %.2f km (%.1f%% complete)\n"
                + "• **GPS Status:** %s\n"
                + "• **Total Alerts:** %d (Penalties: ₹%.2f)\n\n"
                + "ℹ️ *Data Source: Active TrackFleet Simulation Engine*",
                state.getStatus(), state.getRouteName(),
                state.getVehicleRegistrationNumber(), state.getMakeModel(),
                state.getDriverName(), state.getDriverCode(),
                state.getCurrentSpeedKmph() != null ? state.getCurrentSpeedKmph().doubleValue() : 0.0,
                state.getSpeedLimitKmph() != null ? state.getSpeedLimitKmph().doubleValue() : 60.0,
                state.getDistanceTravelledKm() != null ? state.getDistanceTravelledKm().doubleValue() : 0.0,
                state.getProgressPercent(),
                state.isGpsConnected() ? "CONNECTED 🟢" : "DISCONNECTED 🔴",
                state.getTotalAlertsCount(),
                state.getTotalPenaltyAmount() != null ? state.getTotalPenaltyAmount().doubleValue() : 0.0))
            .quickActions(List.of("🚚 Current Speed", "📈 Distance Travelled", "⚠️ Simulation Alerts", "💰 Simulated Penalties"))
            .build();
    }
}
