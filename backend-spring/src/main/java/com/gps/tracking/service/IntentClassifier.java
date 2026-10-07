package com.gps.tracking.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IntentClassifier — Intelligent Natural Language Intent + Entity Extractor for TrackFleet chatbot.
 *
 * Grounded in semantic concepts, keywords, entities, and date/time expressions rather than exact string matching.
 *
 * Supported intents:
 *   GREETING, FLEET_SUMMARY, DRIVER_RANKING, DRIVER_RECOMMENDATION,
 *   DRIVER_STATS, DRIVER_REPORT, VEHICLE_STATS, VEHICLE_ALERT_ANALYSIS,
 *   OVERALL_REPORT, FLEET_REPORT, REPORT_CLARIFICATION, ALERT_QUERY,
 *   TRIP_QUERY, ENTRY_EXIT, COMPARE_DRIVERS, UNKNOWN
 *
 * Extracted entities:
 *   - scope: DRIVER, FLEET, VEHICLE, UNKNOWN
 *   - period: DAILY, WEEKLY, MONTHLY, CUSTOM, ALL_TIME
 *   - driver code & name hints
 *   - vehicle code & registration number
 *   - alert type
 *   - concrete date ranges (Asia/Kolkata timezone)
 *   - ranking limit
 */
@Component
@Slf4j
public class IntentClassifier {

    public static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");

    // =====================================================================
    // ENUMS
    // =====================================================================
    public enum Intent {
        GREETING,
        FLEET_SUMMARY,
        DRIVER_RANKING,
        DRIVER_RECOMMENDATION,
        DRIVER_STATS,
        DRIVER_REPORT,
        VEHICLE_STATS,
        VEHICLE_ALERT_ANALYSIS,
        OVERALL_REPORT,
        FLEET_REPORT,
        REPORT_CLARIFICATION,
        ALERT_QUERY,
        TRIP_QUERY,
        ENTRY_EXIT,
        COMPARE_DRIVERS,
        UNKNOWN
    }

    public enum Scope {
        DRIVER, FLEET, VEHICLE, UNKNOWN
    }

    public enum Period {
        DAILY, WEEKLY, MONTHLY, CUSTOM, ALL_TIME
    }

    // =====================================================================
    // CLASSIFIED INTENT RESULT
    // =====================================================================
    @Getter
    public static class ClassifiedIntent {
        private final Intent intent;
        private final Scope scope;
        private final Period period;
        private final String driverCodeHint;   // e.g. "DRV001"
        private final String driverNameHint;   // e.g. "Rohan"
        private final String vehicleCodeHint;  // e.g. "VH003"
        private final String vehicleRegHint;   // e.g. "MH03AC4582"
        private final String alertTypeHint;    // e.g. "Overspeed Alert"
        private final LocalDate dateFrom;
        private final LocalDate dateTo;
        private final YearMonth yearMonth;
        private final int rankingLimit;        // e.g. 5, 17
        private final double score;

        public ClassifiedIntent(Intent intent, Scope scope, Period period,
                                String driverCodeHint, String driverNameHint,
                                String vehicleCodeHint, String vehicleRegHint,
                                String alertTypeHint,
                                LocalDate dateFrom, LocalDate dateTo, YearMonth yearMonth,
                                int rankingLimit, double score) {
            this.intent = intent;
            this.scope = scope;
            this.period = period;
            this.driverCodeHint = driverCodeHint;
            this.driverNameHint = driverNameHint;
            this.vehicleCodeHint = vehicleCodeHint;
            this.vehicleRegHint = vehicleRegHint;
            this.alertTypeHint = alertTypeHint;
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
            this.yearMonth = yearMonth;
            this.rankingLimit = rankingLimit;
            this.score = score;
        }

        public boolean hasPeriod() { return period != null && period != Period.ALL_TIME; }
        public boolean hasDriver() { return driverCodeHint != null || driverNameHint != null; }
        public boolean hasVehicle() { return vehicleCodeHint != null || vehicleRegHint != null; }
    }

    // =====================================================================
    // KEYWORD GROUPS
    // =====================================================================
    private static final Map<String, Map<Intent, Double>> KEYWORD_SCORES = new LinkedHashMap<>();

    static {
        register("hi hello hey greetings howdy assist help", Intent.GREETING, 5.0);
        register("compare comparison versus vs difference", Intent.COMPARE_DRIVERS, 6.0);
        register("recommend recommendation suggest suggestion trustworthy assign pick choose", Intent.DRIVER_RECOMMENDATION, 6.0);
        register("safest ranking ranked leaderboard leaders risk risky safety score cleanest", Intent.DRIVER_RANKING, 4.0);
        register("rank ranks", Intent.DRIVER_RANKING, 3.5);
        register("overview health operational status", Intent.FLEET_SUMMARY, 4.0);
        register("entered exited entry exit", Intent.ENTRY_EXIT, 6.0);
        register("overall fleet", Intent.OVERALL_REPORT, 4.0);
        register("report pdf generate", Intent.DRIVER_REPORT, 2.5);
        register("profile stats performance details", Intent.DRIVER_STATS, 3.0);
        register("malfunction trouble breakdown issue", Intent.VEHICLE_ALERT_ANALYSIS, 4.0);
        register("inactive speedometer fastest speed", Intent.VEHICLE_STATS, 3.0);
        register("alert alerts overspeed speeding violation violations incidents incident warning warnings unresolved critical", Intent.ALERT_QUERY, 4.0);
        register("braking acceleration ignition coaching", Intent.ALERT_QUERY, 3.0);
        register("trip trips kilometre kilometres km distance travelled traveled duration route", Intent.TRIP_QUERY, 3.5);
    }

    private static void register(String keywords, Intent intent, double weight) {
        for (String kw : keywords.split("\\s+")) {
            KEYWORD_SCORES.computeIfAbsent(kw, k -> new EnumMap<>(Intent.class))
                          .merge(intent, weight, Double::sum);
        }
    }

    // =====================================================================
    // MAIN CLASSIFY METHOD
    // =====================================================================
    public ClassifiedIntent classify(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return makeResult(Intent.UNKNOWN, Scope.UNKNOWN, null, null, null, null, null, null, null, null, null, 5, 0.0);
        }

        String lower = rawMessage.toLowerCase().trim();

        // Greeting short-circuit
        if (lower.matches("^(hi|hello|hey|help|howdy|good\\s+(morning|afternoon|evening))$")) {
            return makeResult(Intent.GREETING, Scope.UNKNOWN, null, null, null, null, null, null, null, null, null, 5, 10.0);
        }

        // ---- Extract entities first ----------------------------------------
        String driverCode   = extractDriverCode(lower);
        String vehicleCode  = extractVehicleCode(lower);
        String vehicleReg   = extractVehicleReg(lower);
        String alertType    = extractAlertType(lower);
        Period period       = extractPeriod(lower);
        LocalDate dateFrom  = null;
        LocalDate dateTo    = null;
        YearMonth yearMonth = extractYearMonth(lower);
        int limit           = extractLimit(lower);

        DateRange dr = extractDateRange(lower);
        if (dr != null) {
            dateFrom = dr.from;
            dateTo   = dr.to;
            if (period == null) period = dr.suggestedPeriod;
        }
        if (yearMonth != null && period == null) period = Period.MONTHLY;

        String driverNameHint = (driverCode == null) ? extractDriverNameHint(lower) : null;

        // ---- Determine Scope -----------------------------------------------
        Scope scope = Scope.UNKNOWN;
        if (lower.contains("fleet") || lower.contains("overall") || lower.contains("company") ||
            lower.contains("entire") || lower.contains("all vehicles") || lower.contains("all drivers")) {
            scope = Scope.FLEET;
        } else if (driverCode != null || driverNameHint != null || lower.contains("driver") || lower.contains("drivers")) {
            scope = Scope.DRIVER;
        } else if (vehicleCode != null || vehicleReg != null || lower.contains("vehicle") || lower.contains("vehicles") || lower.contains("truck")) {
            scope = Scope.VEHICLE;
        }

        // ---- Score tokens --------------------------------------------------
        Map<Intent, Double> scores = new EnumMap<>(Intent.class);
        String[] tokens = lower.replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        for (String token : tokens) {
            Map<Intent, Double> kw = KEYWORD_SCORES.get(token);
            if (kw != null) {
                kw.forEach((intent, w) -> scores.merge(intent, w, Double::sum));
            }
        }

        // ---- Phrase boosts (bigrams/trigrams/domain questions) --------------
        phraseBoost(lower, scores);

        // ---- High-Priority Intent Overrides & Refinements -------------------
        boolean hasReportKw = lower.contains("report") || lower.contains("pdf") || lower.contains("generate");
        boolean hasDriver = driverCode != null || driverNameHint != null;
        boolean hasVehicle = vehicleCode != null || vehicleReg != null;
        boolean hasFleet = scope == Scope.FLEET;

        // 1. Report queries (when report/pdf/generate is present, OR explicitly asking fleet daily/weekly performance, OR driver performance with a month/period)
        if (hasReportKw || (hasDriver && (lower.contains("perform") || lower.contains("performance")) && yearMonth != null)) {
            if (hasFleet || lower.contains("today's fleet report") || lower.contains("weekly fleet report") ||
                lower.contains("october fleet report") || lower.contains("show today's overall") ||
                lower.contains("overall report")) {
                scores.put(Intent.OVERALL_REPORT, 35.0);
            } else if (hasDriver) {
                scores.put(Intent.DRIVER_REPORT, 35.0);
            } else if (lower.contains("driver report") || lower.contains("driver's report")) {
                scores.put(Intent.DRIVER_REPORT, 35.0);
            } else {
                // Ambiguous report scope e.g. "Show me the monthly report", "Give me today's report", "Give me this week's report", "Give me the report from September 1 to September 15"
                scores.put(Intent.REPORT_CLARIFICATION, 35.0);
            }
        } else if (hasFleet && (lower.contains("how did the fleet") || lower.contains("how was the fleet") || lower.contains("fleet performance") || lower.contains("what happened today"))) {
            scores.put(Intent.OVERALL_REPORT, 28.0);
        }

        // 2. Alert / Training / Coaching queries
        if (lower.contains("needs training") || lower.contains("need training") || lower.contains("coaching") ||
            lower.contains("most violations") || lower.contains("repeated violations") ||
            lower.contains("repeated harsh braking") || lower.contains("which day had the most alerts") ||
            lower.contains("what alerts happened today") || lower.contains("show today's alerts") ||
            lower.contains("how many overspeed alerts") || lower.contains("how many alerts were generated today") ||
            lower.contains("show open alerts") || lower.contains("critical alerts") || lower.contains("unresolved") ||
            lower.contains("most common alert") || lower.contains("how many warnings") ||
            lower.contains("night driving alerts")) {
            scores.put(Intent.ALERT_QUERY, 32.0);
        }

        // 3. Trip queries
        if (lower.contains("completed the most trips") || lower.contains("most trips") ||
            lower.contains("travelled the most") || lower.contains("traveled the most") ||
            lower.contains("how many trips") || lower.contains("trips did") ||
            lower.contains("longest trip") || lower.contains("average trip") ||
            lower.contains("today's trips") || lower.contains("trips for") ||
            lower.contains("trips between") || lower.contains("kilometres did") || lower.contains("km did") ||
            lower.contains("today's trip summary")) {
            scores.put(Intent.TRIP_QUERY, 30.0);
        }

        // 4. Recommendation overrides
        if (lower.contains("recommend") || lower.contains("suggest") || lower.contains("who should i assign") ||
            lower.contains("who to assign") || lower.contains("who should handle") || lower.contains("trustworthy") ||
            lower.contains("assign an important trip") || lower.contains("important route")) {
            scores.put(Intent.DRIVER_RECOMMENDATION, 30.0);
        }

        // 5. Comparison overrides
        if (lower.contains("compare") || lower.contains("versus") || lower.contains(" vs ")) {
            scores.put(Intent.COMPARE_DRIVERS, 30.0);
        }

        // 6. Driver ranking queries
        if (lower.contains("who is safest") || lower.contains("who is the safest") || lower.contains("which driver is safest") ||
            lower.contains("who has the best safety") || lower.contains("who has the highest safety") ||
            lower.contains("lowest risk") || lower.contains("cleanest record") ||
            lower.contains("rank drivers") || lower.contains("rank the drivers") || lower.contains("rank all drivers") ||
            lower.contains("give me driver rankings") || lower.contains("driver rankings") ||
            lower.contains("who has the lowest safety") || lower.contains("which drivers are risky") ||
            lower.contains("who needs improvement") || lower.contains("who should i avoid assigning") ||
            lower.contains("fewest alerts") || lower.contains("fewest violations") ||
            (lower.contains("safest") && !lower.contains("vehicle")) ||
            (lower.contains("top driver") && !lower.contains("vehicle"))) {
            scores.put(Intent.DRIVER_RANKING, 28.0);
        }

        // 7. Entry / exit queries
        if (lower.contains("entered") || lower.contains("exited") || lower.contains("entry") || lower.contains("exit")) {
            scores.put(Intent.ENTRY_EXIT, 28.0);
        }

        // 8. Fleet summary queries
        if (lower.contains("fleet summary") || lower.contains("how is the fleet") || lower.contains("current fleet status") ||
            lower.contains("fleet overview") || lower.contains("how many vehicles do we have") ||
            lower.contains("how many vehicles are active") || lower.contains("today's fleet statistics") ||
            lower.contains("operational summary") || lower.contains("fleet health") ||
            lower.contains("management summary") || (lower.contains("summary") && (hasFleet || lower.contains("today")))) {
            if (!hasReportKw && !scores.containsKey(Intent.TRIP_QUERY)) {
                scores.put(Intent.FLEET_SUMMARY, 25.0);
            }
        }

        // 9. Vehicle specific queries vs alert queries
        if (lower.contains("which vehicle has the most alerts") || lower.contains("vehicle causes the most trouble") ||
            lower.contains("vehicles had gps") || lower.contains("most overspeed incidents") ||
            (hasVehicle && (lower.contains("trouble") || (lower.contains("alert") && !lower.contains("driver"))))) {
            scores.put(Intent.VEHICLE_ALERT_ANALYSIS, 26.0);
        } else if (hasVehicle && !lower.contains("trip") && (lower.contains("assigned") || lower.contains("who drives") || lower.contains("show vehicle") || lower.contains("inactive") || lower.contains("highest speed") || lower.contains("speedometer") || lower.contains("details") || lower.contains("performance") || lower.contains("statistics") || lower.contains("active"))) {
            scores.put(Intent.VEHICLE_STATS, 26.0);
        }

        // 10. Driver profile / stats (e.g. "tell me about Rohan", "show Rohan's performance", "what's Rohan's safety score")
        if (hasDriver && !scores.containsKey(Intent.DRIVER_REPORT) && !scores.containsKey(Intent.DRIVER_RECOMMENDATION) && !scores.containsKey(Intent.COMPARE_DRIVERS) && !scores.containsKey(Intent.TRIP_QUERY) && !scores.containsKey(Intent.ALERT_QUERY)) {
            if (lower.contains("tell me about") || lower.contains("how did") || lower.contains("how was") ||
                lower.contains("score") || lower.contains("performance") || lower.contains("profile") ||
                lower.contains("stats") || lower.contains("drive") || lower.contains("drives")) {
                scores.put(Intent.DRIVER_STATS, 25.0);
            }
        }

        // ---- Pick winner ---------------------------------------------------
        Intent best = Intent.UNKNOWN;
        double bestScore = 0.8;
        for (Map.Entry<Intent, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }

        log.debug("IntentClassifier: '{}' → {} (score={}) scope={}, driver={}/{}, vehicle={}/{}, period={}, alertType={}",
            rawMessage, best, String.format("%.1f", bestScore), scope,
            driverCode, driverNameHint, vehicleCode, vehicleReg, period, alertType);

        return makeResult(best, scope, period, driverCode, driverNameHint,
            vehicleCode, vehicleReg, alertType, dateFrom, dateTo, yearMonth, limit, bestScore);
    }

    // =====================================================================
    // PHRASE BOOSTS
    // =====================================================================
    private void phraseBoost(String lower, Map<Intent, Double> scores) {
        // Driver ranking / safety phrases
        if (lower.contains("safest") || lower.contains("lowest risk") || lower.contains("safety score") ||
            lower.contains("which driver is best") || lower.contains("who has the best") ||
            lower.contains("top driver") || lower.contains("top drivers") || lower.contains("best driver") ||
            lower.contains("show top") || lower.contains("rank drivers") ||
            lower.contains("driver leaderboard") || lower.contains("rank all drivers") ||
            lower.contains("rank the drivers") || lower.contains("give me driver rankings") ||
            lower.contains("who has the lowest safety") || lower.contains("which drivers are risky") ||
            lower.contains("who needs improvement") || lower.contains("fewest alerts") ||
            lower.contains("cleanest record") || lower.contains("who has the most violations") ||
            lower.contains("most overspeed alerts") || lower.contains("most harsh braking incidents")) {
            scores.merge(Intent.DRIVER_RANKING, 12.0, Double::sum);
        }

        // Recommendation
        if (lower.contains("who should i assign") || lower.contains("who would you") ||
            lower.contains("recommend a driver") || lower.contains("recommend drivers") ||
            lower.contains("suggest a driver") || lower.contains("suggest drivers") ||
            lower.contains("urgent delivery") || lower.contains("long-distance trip") ||
            lower.contains("who can handle") || lower.contains("who to assign") ||
            lower.contains("drivers for important") || lower.contains("recommend the best driver") ||
            lower.contains("who should handle a long trip")) {
            scores.merge(Intent.DRIVER_RECOMMENDATION, 12.0, Double::sum);
        }

        // Fleet summary
        if (lower.contains("how is the fleet") || lower.contains("how is our fleet") ||
            lower.contains("fleet health") || lower.contains("fleet overview") ||
            lower.contains("operational summary") || lower.contains("fleet doing") ||
            lower.contains("management summary") || lower.contains("fleet summary") ||
            lower.contains("how many vehicles do we have") || lower.contains("how many vehicles are active") ||
            lower.contains("today's fleet statistics") || lower.contains("current fleet status")) {
            scores.merge(Intent.FLEET_SUMMARY, 12.0, Double::sum);
        }

        // Overall / fleet report
        if (lower.contains("how did the fleet") || lower.contains("fleet perform") ||
            lower.contains("fleet performance") || lower.contains("how was the fleet") ||
            lower.contains("today's fleet report") || lower.contains("overall report") ||
            lower.contains("fleet report for") || lower.contains("overall fleet report") ||
            lower.contains("show today's overall") || lower.contains("generate daily overall report")) {
            scores.merge(Intent.OVERALL_REPORT, 12.0, Double::sum);
        }

        // Driver report phrases
        if (lower.contains("report for") || lower.contains("report of") ||
            lower.contains("'s report") || lower.contains("s report") ||
            lower.contains("driver report") || lower.contains("driver's report")) {
            scores.merge(Intent.DRIVER_REPORT, 10.0, Double::sum);
        }

        // Entry/exit
        if (lower.contains("vehicles entered") || lower.contains("vehicles exited") ||
            lower.contains("how many entered") || lower.contains("how many exited") ||
            lower.contains("entry and exit") || lower.contains("entry exit")) {
            scores.merge(Intent.ENTRY_EXIT, 14.0, Double::sum);
        }

        // Alert queries
        if (lower.contains("most common alert") || lower.contains("common violation") ||
            lower.contains("show open alerts") || lower.contains("any critical") ||
            lower.contains("critical alerts") || lower.contains("unresolved") ||
            lower.contains("how many overspeed") || lower.contains("how many alerts") ||
            lower.contains("how many warnings") || lower.contains("warnings occurred") ||
            lower.contains("repeated harsh braking") || lower.contains("overspeed alerts") ||
            lower.contains("most violations") || lower.contains("repeated violations") ||
            lower.contains("needs training") || lower.contains("who needs training") ||
            lower.contains("which day had the most alerts") || lower.contains("what alerts happened today") ||
            lower.contains("show today's alerts") || lower.contains("how many alerts were generated today") ||
            lower.contains("how many alerts are open") || lower.contains("historical alerts") ||
            lower.contains("night driving alerts") || lower.contains("serious alerts") ||
            lower.contains("alert summary") || lower.contains("show harsh braking alerts") ||
            lower.contains("show gps disconnect alerts")) {
            scores.merge(Intent.ALERT_QUERY, 14.0, Double::sum);
        }

        // Trip queries
        if (lower.contains("how many trips") || lower.contains("trips did") ||
            lower.contains("who travelled the most") || lower.contains("who completed the most trips") ||
            lower.contains("longest trip") || lower.contains("average trip") ||
            lower.contains("today's trips") || lower.contains("trips for") ||
            lower.contains("trips between") || lower.contains("total distance") ||
            lower.contains("how many kilometres did") || lower.contains("how many km did") ||
            lower.contains("today's trip summary") || lower.contains("trips happened today") ||
            lower.contains("trips were completed this month")) {
            scores.merge(Intent.TRIP_QUERY, 12.0, Double::sum);
        }

        // Vehicles with alert issues
        if (lower.contains("which vehicle has the most alerts") || lower.contains("highest number of alerts") ||
            lower.contains("causes the most trouble") || lower.contains("gps disconnect") ||
            lower.contains("vehicle trouble") || lower.contains("vehicles had gps")) {
            scores.merge(Intent.VEHICLE_ALERT_ANALYSIS, 14.0, Double::sum);
        }

        // Vehicle stats
        if (lower.contains("inactive") || lower.contains("highest speed") ||
            lower.contains("fastest vehicle") || lower.contains("maximum speed") ||
            lower.contains("show all vehicles") || lower.contains("which vehicles are currently active") ||
            lower.contains("which driver is assigned to") || lower.contains("who drives") ||
            lower.contains("show vehicle") || lower.contains("vehicle performance") ||
            lower.contains("vehicle statistics")) {
            scores.merge(Intent.VEHICLE_STATS, 12.0, Double::sum);
        }
    }

    // =====================================================================
    // ENTITY EXTRACTORS
    // =====================================================================

    /** Extract DRV code e.g. DRV001 or DRV-001 or DRV1 */
    public String extractDriverCode(String lower) {
        Matcher m = Pattern.compile("drv-?0*(\\d+)").matcher(lower);
        if (m.find()) return "DRV" + String.format("%03d", Integer.parseInt(m.group(1)));
        return null;
    }

    /** Extract VH code e.g. VH003 or VH-003 or VH3 */
    public String extractVehicleCode(String lower) {
        Matcher m = Pattern.compile("vh-?0*(\\d+)").matcher(lower);
        if (m.find()) return "VH" + String.format("%03d", Integer.parseInt(m.group(1)));
        return null;
    }

    /** Extract vehicle registration e.g. MH03AC4582 */
    public String extractVehicleReg(String lower) {
        Matcher m = Pattern.compile("mh-?\\d{2}-?[a-z]{1,2}-?\\d{4}").matcher(lower);
        if (m.find()) return m.group().replaceAll("-", "").toUpperCase();
        return null;
    }

    /** Extract alert type keyword */
    public String extractAlertType(String lower) {
        if (lower.contains("overspeed") || lower.contains("speeding")) return "Overspeed Alert";
        if (lower.contains("harsh brak") || lower.contains("hard brak")) return "Harsh Braking Alert";
        if (lower.contains("harsh acc")) return "Harsh Acceleration Alert";
        if (lower.contains("gps") && (lower.contains("disconnect") || lower.contains("problem") || lower.contains("issue") || lower.contains("offline"))) return "GPS Disconnect Alert";
        if (lower.contains("night driv") || lower.contains("night time")) return "Night Driving Alert";
        if (lower.contains("ignition")) return "Ignition Alert";
        if (lower.contains("fatigue") || lower.contains("drowsy")) return "Fatigue Alert";
        if (lower.contains("critical")) return "CRITICAL";
        if (lower.contains("warning")) return "WARNING";
        return null;
    }

    /** Extract period from natural language */
    public Period extractPeriod(String lower) {
        if (lower.contains("today") || lower.contains("daily") || lower.contains("today's") ||
            lower.contains("this day") || lower.contains("what happened today")) {
            return Period.DAILY;
        }
        if (lower.contains("this week") || lower.contains("weekly") || lower.contains("this week's") ||
            lower.contains("last 7 days") || lower.contains("seven days")) {
            return Period.WEEKLY;
        }
        if (lower.contains("last week")) return Period.WEEKLY;
        if (lower.contains("yesterday")) return Period.DAILY;
        if (lower.contains("this month") || lower.contains("monthly") || lower.contains("this month's")) {
            return Period.MONTHLY;
        }
        if (lower.contains("last month")) return Period.MONTHLY;
        if (lower.contains("custom") || lower.contains("between") || (lower.contains("from") && lower.contains("to"))) {
            return Period.CUSTOM;
        }
        return null;
    }

    public static class DateRange {
        public final LocalDate from;
        public final LocalDate to;
        public final Period suggestedPeriod;
        public DateRange(LocalDate f, LocalDate t, Period p) { from = f; to = t; suggestedPeriod = p; }
    }

    /** Extract concrete date range from natural expressions (Asia/Kolkata timezone) */
    public DateRange extractDateRange(String lower) {
        LocalDate now = LocalDate.now(ZONE_KOLKATA);

        if (lower.contains("today")) return new DateRange(now, now, Period.DAILY);
        if (lower.contains("yesterday")) { LocalDate y = now.minusDays(1); return new DateRange(y, y, Period.DAILY); }
        if (lower.contains("last week") && !lower.contains("last month")) {
            LocalDate end = now.with(TemporalAdjusters.previous(java.time.DayOfWeek.SUNDAY));
            return new DateRange(end.minusDays(6), end, Period.WEEKLY);
        }
        if (lower.contains("this week")) {
            LocalDate start = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            return new DateRange(start, now, Period.WEEKLY);
        }
        if (lower.contains("last month")) {
            LocalDate lm = now.minusMonths(1);
            return new DateRange(lm.withDayOfMonth(1), lm.withDayOfMonth(lm.lengthOfMonth()), Period.MONTHLY);
        }
        if (lower.contains("this month")) {
            return new DateRange(now.withDayOfMonth(1), now, Period.MONTHLY);
        }

        // Pattern 1: "from September 1 to September 15" or "between Oct 1 and Oct 5" or "from September 1 to 15"
        Matcher m1 = Pattern.compile(
            "(?:from|between)\\s+([a-zA-Z]+)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*\\d{4})?\\s+(?:to|and|-)\\s+(?:([a-zA-Z]+)\\s+)?(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?")
            .matcher(lower);
        if (m1.find()) {
            try {
                String mName1 = m1.group(1);
                int d1 = Integer.parseInt(m1.group(2));
                String mName2 = m1.group(3) != null ? m1.group(3) : mName1;
                int d2 = Integer.parseInt(m1.group(4));
                String yr = m1.group(5);
                int year = (yr != null) ? Integer.parseInt(yr) : now.getYear();
                Integer mon1 = parseMonthName(mName1);
                Integer mon2 = parseMonthName(mName2);
                if (mon1 != null && mon2 != null) {
                    return new DateRange(
                        LocalDate.of(year, mon1, d1),
                        LocalDate.of(year, mon2, d2),
                        Period.CUSTOM);
                }
            } catch (Exception ignored) {}
        }

        // Pattern 2: "between 1 October and 5 October" or "from 1 September to 15 September" or "from 1 to 15 September"
        Matcher m2 = Pattern.compile(
            "(?:from|between)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:([a-zA-Z]+)\\s+)?(?:\\d{4}\\s+)?(?:to|and|-)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-zA-Z]+)(?:\\s+(\\d{4}))?")
            .matcher(lower);
        if (m2.find()) {
            try {
                int d1 = Integer.parseInt(m2.group(1));
                String mName1 = m2.group(2);
                int d2 = Integer.parseInt(m2.group(3));
                String mName2 = m2.group(4);
                if (mName1 == null) mName1 = mName2;
                String yr = m2.group(5);
                int year = (yr != null) ? Integer.parseInt(yr) : now.getYear();
                Integer mon1 = parseMonthName(mName1);
                Integer mon2 = parseMonthName(mName2);
                if (mon1 != null && mon2 != null) {
                    return new DateRange(
                        LocalDate.of(year, mon1, d1),
                        LocalDate.of(year, mon2, d2),
                        Period.CUSTOM);
                }
            } catch (Exception ignored) {}
        }

        // Two ISO dates "YYYY-MM-DD to YYYY-MM-DD"
        Matcher rangeIso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s+(?:to|and|-)\\s+(\\d{4}-\\d{2}-\\d{2})").matcher(lower);
        if (rangeIso.find()) {
            try {
                LocalDate d1 = LocalDate.parse(rangeIso.group(1));
                LocalDate d2 = LocalDate.parse(rangeIso.group(2));
                return new DateRange(d1, d2, Period.CUSTOM);
            } catch (Exception ignored) {}
        }

        // Single ISO date "YYYY-MM-DD"
        Matcher iso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(lower);
        if (iso.find()) {
            try {
                LocalDate d = LocalDate.parse(iso.group(1));
                return new DateRange(d, d, Period.DAILY);
            } catch (Exception ignored) {}
        }

        return null;
    }

    /** Extract YearMonth from named month */
    public YearMonth extractYearMonth(String lower) {
        Matcher ymMat = Pattern.compile("(\\d{4})-(\\d{2})").matcher(lower);
        if (ymMat.find()) return YearMonth.of(Integer.parseInt(ymMat.group(1)), Integer.parseInt(ymMat.group(2)));

        int year = LocalDate.now(ZONE_KOLKATA).getYear();
        Matcher yrMat = Pattern.compile("20(2[0-9])").matcher(lower);
        if (yrMat.find()) year = Integer.parseInt(yrMat.group());

        for (Map.Entry<String, Integer> e : MONTH_NAMES.entrySet()) {
            if (lower.contains(e.getKey())) return YearMonth.of(year, e.getValue());
        }
        return null;
    }

    /** Extract limit e.g. "top 5 drivers", "rank all drivers" → 17 */
    public int extractLimit(String lower) {
        if (lower.contains("rank all") || lower.contains("all drivers") || lower.contains("all 17") ||
            lower.contains("rank the drivers") || lower.contains("driver rankings") || lower.contains("rank drivers")) {
            return 17; // fleet size
        }
        String[] words = lower.split("\\s+");
        for (int i = 0; i < words.length - 1; i++) {
            if (words[i].equals("top") || words[i].equals("best")) {
                try { return Integer.parseInt(words[i + 1]); } catch (NumberFormatException ignored) {}
                Integer w = WORD_NUMBERS.get(words[i + 1]);
                if (w != null) return w;
            }
        }
        Matcher m = Pattern.compile("(?:top|best|first|show)\\s+(\\d+)").matcher(lower);
        if (m.find()) return Integer.parseInt(m.group(1));
        return 5;
    }

    /** Extract first-name hint from the message */
    public String extractDriverNameHint(String lower) {
        // Fast match on common fleet driver names
        List<String> knownNames = List.of(
            "rohan", "priya", "amit", "sunita", "rajesh", "kavitha",
            "suresh", "deepak", "vikram", "neha", "anil", "pooja",
            "manoj", "anita", "rahul", "preeti", "ravi", "kunal", "sneha"
        );
        for (String name : knownNames) {
            if (lower.contains(name)) {
                return name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        }

        Set<String> stopWords = Set.of(
            "show", "give", "tell", "what", "when", "where", "which", "who", "whom", "whose",
            "this", "that", "from", "have", "with", "does", "been", "more", "most", "many",
            "safe", "safest", "best", "good", "better", "cleanest", "worst", "risky",
            "how", "the", "and", "for", "are", "were", "was", "will", "would", "could", "should",
            "report", "reports", "fleet", "trip", "trips", "alert", "alerts", "week", "month",
            "daily", "weekly", "monthly", "today", "yesterday", "driver", "drivers", "vehicle", "vehicles",
            "about", "last", "overall", "generate", "compare", "ranking", "rankings", "ranked",
            "performance", "status", "score", "scores", "record", "records",
            "needs", "need", "training", "coaching", "completed", "complete", "travelled", "traveled",
            "distance", "kilometres", "kilometre", "overspeed", "braking", "speeding",
            "unresolved", "critical", "warning", "warnings", "violation", "violations", "incident", "incidents",
            "between", "inactive", "active", "entered", "exited", "entry", "exit", "speed", "fastest",
            "highest", "lowest", "fewest", "least", "trouble", "problems", "problem", "assigned", "drives", "drive"
        );
        for (String token : lower.replaceAll("[^a-z\\s]", " ").split("\\s+")) {
            if (token.length() >= 4 && !stopWords.contains(token) && !MONTH_NAMES.containsKey(token) && KEYWORD_SCORES.get(token) == null) {
                return token.substring(0, 1).toUpperCase() + token.substring(1);
            }
        }
        return null;
    }

    // =====================================================================
    // HELPERS
    // =====================================================================
    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
        Map.entry("january", 1), Map.entry("jan", 1),
        Map.entry("february", 2), Map.entry("feb", 2),
        Map.entry("march", 3), Map.entry("mar", 3),
        Map.entry("april", 4), Map.entry("apr", 4),
        Map.entry("may", 5),
        Map.entry("june", 6), Map.entry("jun", 6),
        Map.entry("july", 7), Map.entry("jul", 7),
        Map.entry("august", 8), Map.entry("aug", 8),
        Map.entry("september", 9), Map.entry("sep", 9), Map.entry("sept", 9),
        Map.entry("october", 10), Map.entry("oct", 10),
        Map.entry("november", 11), Map.entry("nov", 11),
        Map.entry("december", 12), Map.entry("dec", 12)
    );

    private static final Map<String, Integer> WORD_NUMBERS = Map.of(
        "one", 1, "two", 2, "three", 3, "four", 4, "five", 5,
        "six", 6, "seven", 7, "eight", 8, "nine", 9, "ten", 10
    );

    private Integer parseMonthName(String name) {
        if (name == null) return null;
        return MONTH_NAMES.get(name.toLowerCase());
    }

    private ClassifiedIntent makeResult(Intent intent, Scope scope, Period period,
            String driverCode, String driverName,
            String vehicleCode, String vehicleReg, String alertType,
            LocalDate from, LocalDate to, YearMonth ym,
            int limit, double score) {
        return new ClassifiedIntent(intent, scope, period, driverCode, driverName,
            vehicleCode, vehicleReg, alertType, from, to, ym, limit, score);
    }
}
