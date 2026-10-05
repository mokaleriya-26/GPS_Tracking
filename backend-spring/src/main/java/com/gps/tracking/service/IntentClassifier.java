package com.gps.tracking.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IntentClassifier — Natural Language Intent + Entity Extractor for TrackFleet chatbot.
 *
 * Instead of exact-string matching, this class uses weighted keyword scoring:
 *   - Every semantic concept has a set of synonymous keywords
 *   - The message is tokenised and scored against each intent's keyword groups
 *   - The intent with the highest score wins (above a minimum threshold)
 *
 * Supported intents:
 *   GREETING, FLEET_SUMMARY, DRIVER_RANKING, DRIVER_RECOMMENDATION,
 *   DRIVER_STATS, DRIVER_REPORT, VEHICLE_STATS, VEHICLE_ALERT_ANALYSIS,
 *   OVERALL_REPORT, FLEET_REPORT, ALERT_QUERY, TRIP_QUERY, ENTRY_EXIT,
 *   COMPARE_DRIVERS, UNKNOWN
 *
 * Extracted entities: driver name/ID, vehicle code/reg, alert type,
 * period (daily/weekly/monthly/custom), date range, ranking limit.
 */
@Component
@Slf4j
public class IntentClassifier {

    // =====================================================================
    // INTENT ENUM
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
        ALERT_QUERY,
        TRIP_QUERY,
        ENTRY_EXIT,
        COMPARE_DRIVERS,
        UNKNOWN
    }

    // =====================================================================
    // PERIOD ENUM
    // =====================================================================
    public enum Period {
        DAILY, WEEKLY, MONTHLY, CUSTOM, ALL_TIME
    }

    // =====================================================================
    // EXTRACTED INTENT RESULT
    // =====================================================================
    @Getter
    public static class ClassifiedIntent {
        private final Intent intent;
        private final Period period;
        private final String driverCodeHint;   // e.g. "DRV001" extracted from message
        private final String driverNameHint;   // e.g. "Rohan" extracted from message
        private final String vehicleCodeHint;  // e.g. "VH003"
        private final String vehicleRegHint;   // e.g. "MH04AB1234"
        private final String alertTypeHint;    // e.g. "Overspeed Alert"
        private final LocalDate dateFrom;
        private final LocalDate dateTo;
        private final YearMonth yearMonth;
        private final int rankingLimit;        // e.g. 5 for "top 5 drivers"
        private final double score;

        public ClassifiedIntent(Intent intent, Period period,
                                String driverCodeHint, String driverNameHint,
                                String vehicleCodeHint, String vehicleRegHint,
                                String alertTypeHint,
                                LocalDate dateFrom, LocalDate dateTo, YearMonth yearMonth,
                                int rankingLimit, double score) {
            this.intent = intent;
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
    // KEYWORD GROUPS (Only content words, no stop words!)
    // =====================================================================

    /** Returns scored (Intent -> score) map from a single word/token */
    private static final Map<String, Map<Intent, Double>> KEYWORD_SCORES = new LinkedHashMap<>();

    static {
        // --- GREETING -------------------------------------------------------
        register("hi hello hey greetings howdy", Intent.GREETING, 5.0);

        // --- COMPARE_DRIVERS ------------------------------------------------
        register("compare comparison versus vs", Intent.COMPARE_DRIVERS, 6.0);

        // --- DRIVER_RECOMMENDATION ------------------------------------------
        register("recommend recommendation suggest suggestion trustworthy assign", Intent.DRIVER_RECOMMENDATION, 6.0);

        // --- DRIVER_RANKING -------------------------------------------------
        register("safest ranking ranked leaderboard leaders risk risky safety", Intent.DRIVER_RANKING, 4.0);
        register("rank", Intent.DRIVER_RANKING, 3.5);

        // --- FLEET_SUMMARY --------------------------------------------------
        register("overview health operational", Intent.FLEET_SUMMARY, 4.0);

        // --- ENTRY_EXIT -----------------------------------------------------
        register("entered exited entry exit", Intent.ENTRY_EXIT, 6.0);

        // --- OVERALL_REPORT ------------------------------------------------
        register("overall", Intent.OVERALL_REPORT, 4.0);

        // --- DRIVER_REPORT -------------------------------------------------
        register("report pdf", Intent.DRIVER_REPORT, 2.5);

        // --- DRIVER_STATS --------------------------------------------------
        register("profile stats", Intent.DRIVER_STATS, 3.0);

        // --- VEHICLE_ALERT_ANALYSIS ----------------------------------------
        register("trouble malfunction", Intent.VEHICLE_ALERT_ANALYSIS, 4.0);

        // --- VEHICLE_STATS -------------------------------------------------
        register("inactive speedometer", Intent.VEHICLE_STATS, 3.0);

        // --- ALERT_QUERY ---------------------------------------------------
        register("alert alerts overspeed speeding violation violations incidents incident warning warnings unresolved critical", Intent.ALERT_QUERY, 4.0);
        register("braking acceleration ignition coaching", Intent.ALERT_QUERY, 3.0);

        // --- TRIP_QUERY ----------------------------------------------------
        register("trip trips kilometre kilometres km distance travelled traveled duration", Intent.TRIP_QUERY, 3.5);
    }

    /** Register keywords → intent with weight */
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
            return makeResult(Intent.UNKNOWN, null, null, null, null, null, null, null, null, null, 5, 0.0);
        }

        String lower = rawMessage.toLowerCase().trim();

        // Greeting short-circuit
        if (lower.matches("^(hi|hello|hey|help|howdy|good\\s+(morning|afternoon|evening))$")) {
            return makeResult(Intent.GREETING, null, null, null, null, null, null, null, null, null, 5, 10.0);
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

        // Extract driver name hint
        String driverNameHint = (driverCode == null) ? extractDriverNameHint(lower) : null;

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

        // ---- Contextual adjustments ----------------------------------------
        boolean hasReportKw = lower.contains("report") || lower.contains("pdf") || lower.contains("generate");
        boolean hasPerformKw = lower.contains("perform") || lower.contains("performance");
        boolean hasDriver = driverCode != null || driverNameHint != null;
        boolean hasVehicle = vehicleCode != null || vehicleReg != null;
        boolean hasFleet = lower.contains("fleet") || lower.contains("overall");

        // Driver entity + (report OR performance OR month) -> strong DRIVER_REPORT
        if (hasDriver && (hasReportKw || hasPerformKw || yearMonth != null) && !hasFleet) {
            scores.merge(Intent.DRIVER_REPORT, 12.0, Double::sum);
        }

        // Recommendation keywords override trips/other words
        if (lower.contains("recommend") || lower.contains("suggest") || lower.contains("who should i assign") || lower.contains("who to assign")) {
            scores.merge(Intent.DRIVER_RECOMMENDATION, 15.0, Double::sum);
        }

        // Compare override
        if (lower.contains("compare") || lower.contains("versus") || lower.contains(" vs ")) {
            scores.merge(Intent.COMPARE_DRIVERS, 15.0, Double::sum);
        }

        // Fleet report or overall performance
        if (hasFleet && (hasReportKw || hasPerformKw)) {
            scores.merge(Intent.OVERALL_REPORT, 10.0, Double::sum);
        }

        // Summary queries
        if (lower.contains("summary") && (hasFleet || lower.contains("operational") || lower.contains("today's fleet"))) {
            scores.merge(Intent.FLEET_SUMMARY, 12.0, Double::sum);
        }

        // Vehicle alert issues override generic vehicle stats
        if (lower.contains("gps disconnect") || (hasVehicle && (lower.contains("alert") || lower.contains("trouble")))) {
            scores.merge(Intent.VEHICLE_ALERT_ANALYSIS, 12.0, Double::sum);
        }

        // ---- Pick winner ---------------------------------------------------
        Intent best = Intent.UNKNOWN;
        double bestScore = 0.8; // minimum threshold
        for (Map.Entry<Intent, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }

        log.debug("IntentClassifier: '{}' → {} (score={}) entities: driver={}/{}, vehicle={}/{}, period={}, alertType={}",
            rawMessage, best, String.format("%.1f", bestScore),
            driverCode, driverNameHint, vehicleCode, vehicleReg, period, alertType);

        return makeResult(best, period, driverCode, driverNameHint,
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
            lower.contains("driver leaderboard") || lower.contains("rank all drivers")) {
            scores.merge(Intent.DRIVER_RANKING, 10.0, Double::sum);
        }

        // Recommendation
        if (lower.contains("who should i assign") || lower.contains("who would you") ||
            lower.contains("recommend a driver") || lower.contains("recommend drivers") ||
            lower.contains("suggest a driver") || lower.contains("suggest") ||
            lower.contains("urgent delivery") || lower.contains("long-distance trip") ||
            lower.contains("who can handle") || lower.contains("who to assign") ||
            lower.contains("drivers for important")) {
            scores.merge(Intent.DRIVER_RECOMMENDATION, 10.0, Double::sum);
        }

        // Fleet summary
        if (lower.contains("how is the fleet") || lower.contains("how is our fleet") ||
            lower.contains("fleet health") || lower.contains("fleet overview") ||
            lower.contains("operational summary") || lower.contains("fleet doing") ||
            lower.contains("management summary") || lower.contains("fleet summary")) {
            scores.merge(Intent.FLEET_SUMMARY, 10.0, Double::sum);
        }

        // Overall / fleet report
        if (lower.contains("how did the fleet") || lower.contains("fleet perform") ||
            lower.contains("fleet performance") || lower.contains("how was the fleet") ||
            lower.contains("today's fleet report") || lower.contains("overall report") ||
            lower.contains("fleet report for") || lower.contains("overall fleet report") ||
            lower.contains("show today's overall")) {
            scores.merge(Intent.OVERALL_REPORT, 10.0, Double::sum);
        }

        // Driver report phrases
        if (lower.contains("report for") || lower.contains("report of") ||
            lower.contains("'s report") || lower.contains("s report") ||
            lower.contains("monthly report") || lower.contains("daily report") ||
            lower.contains("weekly report") || lower.contains("performance report")) {
            scores.merge(Intent.DRIVER_REPORT, 8.0, Double::sum);
        }

        // Entry/exit
        if (lower.contains("vehicles entered") || lower.contains("vehicles exited") ||
            lower.contains("how many entered") || lower.contains("how many exited") ||
            lower.contains("entry and exit") || lower.contains("entry exit")) {
            scores.merge(Intent.ENTRY_EXIT, 12.0, Double::sum);
        }

        // Alert queries (including violations and coaching/training)
        if (lower.contains("most common alert") || lower.contains("common violation") ||
            lower.contains("show open alerts") || lower.contains("any critical") ||
            lower.contains("critical alerts") || lower.contains("unresolved") ||
            lower.contains("how many overspeed") || lower.contains("how many alerts") ||
            lower.contains("how many warnings") || lower.contains("warnings occurred") ||
            lower.contains("repeated harsh braking") || lower.contains("overspeed alerts") ||
            lower.contains("most violations") || lower.contains("repeated violations") ||
            lower.contains("needs training") || lower.contains("who needs training")) {
            scores.merge(Intent.ALERT_QUERY, 12.0, Double::sum);
        }

        // Trip queries
        if (lower.contains("how many trips") || lower.contains("trips did") ||
            lower.contains("who travelled the most") || lower.contains("who completed the most trips") ||
            lower.contains("longest trip") || lower.contains("average trip") ||
            lower.contains("today's trips") || lower.contains("trips for") ||
            lower.contains("trips between") || lower.contains("total distance")) {
            scores.merge(Intent.TRIP_QUERY, 10.0, Double::sum);
        }

        // Vehicles with alert issues
        if (lower.contains("most alerts") || lower.contains("causes the most trouble") ||
            lower.contains("gps disconnect") || lower.contains("vehicle trouble")) {
            scores.merge(Intent.VEHICLE_ALERT_ANALYSIS, 12.0, Double::sum);
        }

        // Vehicle stats
        if (lower.contains("inactive") || lower.contains("highest speed") ||
            lower.contains("fastest vehicle") || lower.contains("maximum speed")) {
            scores.merge(Intent.VEHICLE_STATS, 10.0, Double::sum);
        }
    }

    // =====================================================================
    // ENTITY EXTRACTORS
    // =====================================================================

    /** Extract DRV code e.g. DRV001 or DRV-001 */
    public String extractDriverCode(String lower) {
        Matcher m = Pattern.compile("drv-?0*(\\d+)").matcher(lower);
        if (m.find()) return "DRV" + String.format("%03d", Integer.parseInt(m.group(1)));
        return null;
    }

    /** Extract VH code e.g. VH003 or VH-003 */
    public String extractVehicleCode(String lower) {
        Matcher m = Pattern.compile("vh-?0*(\\d+)").matcher(lower);
        if (m.find()) return "VH" + String.format("%03d", Integer.parseInt(m.group(1)));
        return null;
    }

    /** Extract vehicle registration e.g. MH04AB1234 */
    public String extractVehicleReg(String lower) {
        Matcher m = Pattern.compile("mh\\d{2}[a-z]{1,2}\\d{4}").matcher(lower);
        if (m.find()) return m.group().toUpperCase();
        return null;
    }

    /** Extract alert type keyword */
    public String extractAlertType(String lower) {
        if (lower.contains("overspeed") || lower.contains("speeding")) return "Overspeed Alert";
        if (lower.contains("harsh brak") || lower.contains("hard brak")) return "Harsh Braking Alert";
        if (lower.contains("harsh acc")) return "Harsh Acceleration Alert";
        if (lower.contains("gps") && (lower.contains("disconnect") || lower.contains("problem") || lower.contains("issue"))) return "GPS Disconnect Alert";
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
        if (lower.contains("custom") || lower.contains("between") || lower.contains("from") && lower.contains("to")) {
            return Period.CUSTOM;
        }
        return null;
    }

    /** Helper class for date range results */
    private static class DateRange {
        LocalDate from;
        LocalDate to;
        Period suggestedPeriod;
        DateRange(LocalDate f, LocalDate t, Period p) { from = f; to = t; suggestedPeriod = p; }
    }

    /** Extract concrete date range from natural expressions */
    public DateRange extractDateRange(String lower) {
        LocalDate now = LocalDate.now();

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

        // "from X to Y" / "between X and Y"
        Matcher between = Pattern.compile(
            "(?:from|between)\\s+(\\d{1,2})\\s+(\\w+)(?:\\s+\\d{4})?\\s+(?:to|and)\\s+(\\d{1,2})\\s+(\\w+)(?:\\s+(\\d{4}))?")
            .matcher(lower);
        if (between.find()) {
            try {
                int d1 = Integer.parseInt(between.group(1));
                String m1 = between.group(2);
                int d2 = Integer.parseInt(between.group(3));
                String m2 = between.group(4);
                String yr = between.group(5);
                int year = (yr != null) ? Integer.parseInt(yr) : now.getYear();
                Integer mon1 = parseMonthName(m1);
                Integer mon2 = parseMonthName(m2);
                if (mon1 != null && mon2 != null) {
                    return new DateRange(
                        LocalDate.of(year, mon1, d1),
                        LocalDate.of(year, mon2, d2),
                        Period.CUSTOM);
                }
            } catch (Exception ignored) {}
        }

        // Two ISO dates "from YYYY-MM-DD to YYYY-MM-DD" or "YYYY-MM-DD to YYYY-MM-DD"
        Matcher rangeIso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s+(?:to|and|-)\\s+(\\d{4}-\\d{2}-\\d{2})").matcher(lower);
        if (rangeIso.find()) {
            try {
                LocalDate d1 = LocalDate.parse(rangeIso.group(1));
                LocalDate d2 = LocalDate.parse(rangeIso.group(2));
                return new DateRange(d1, d2, Period.CUSTOM);
            } catch (Exception ignored) {}
        }

        // Single "YYYY-MM-DD"
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

        int year = LocalDate.now().getYear();
        Matcher yrMat = Pattern.compile("20(2[0-9])").matcher(lower);
        if (yrMat.find()) year = Integer.parseInt(yrMat.group());

        for (Map.Entry<String, Integer> e : MONTH_NAMES.entrySet()) {
            if (lower.contains(e.getKey())) return YearMonth.of(year, e.getValue());
        }
        return null;
    }

    /** Extract "top N" limit e.g. "top 5 drivers" → 5 */
    public int extractLimit(String lower) {
        // word numbers
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
        return 5; // default
    }

    /**
     * Extract a first-name hint from the message.
     * Filters out common stop words and keeps names ≥4 chars not already a keyword.
     */
    public String extractDriverNameHint(String lower) {
        Set<String> stopWords = Set.of(
            "show", "give", "tell", "what", "when", "where", "which", "who",
            "this", "that", "from", "have", "with", "does", "been", "more",
            "safe", "best", "most", "many", "how", "the", "and", "for",
            "report", "fleet", "trip", "alert", "week", "month", "daily",
            "weekly", "monthly", "today", "yesterday", "driver", "vehicle",
            "about", "last", "overall", "generate", "compare", "ranking"
        );
        for (String token : lower.replaceAll("[^a-z\\s]", " ").split("\\s+")) {
            if (token.length() >= 4 && !stopWords.contains(token) && KEYWORD_SCORES.get(token) == null) {
                // Likely a proper name candidate — return capitalised
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
        return MONTH_NAMES.get(name.toLowerCase());
    }

    private ClassifiedIntent makeResult(Intent intent, Period period,
            String driverCode, String driverName,
            String vehicleCode, String vehicleReg, String alertType,
            LocalDate from, LocalDate to, YearMonth ym,
            int limit, double score) {
        return new ClassifiedIntent(intent, period, driverCode, driverName,
            vehicleCode, vehicleReg, alertType, from, to, ym, limit, score);
    }
}
