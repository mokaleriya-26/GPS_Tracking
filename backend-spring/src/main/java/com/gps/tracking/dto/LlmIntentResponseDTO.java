package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured output received from the conversational LLM.
 * Used for routing to existing backend database queries or delivering natural dialogue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmIntentResponseDTO {

    /**
     * Classified intent name:
     * GREETING, SMALL_TALK, CAPABILITIES, EXPLANATION,
     * FLEET_SUMMARY, DRIVER_RANKING, DRIVER_RECOMMENDATION,
     * DRIVER_STATS, DRIVER_REPORT, VEHICLE_STATS, VEHICLE_ALERT_ANALYSIS,
     * OVERALL_REPORT, FLEET_REPORT, REPORT_CLARIFICATION,
     * ALERT_QUERY, TRIP_QUERY, ENTRY_EXIT, COMPARE_DRIVERS,
     * CLARIFICATION_NEEDED, UNKNOWN
     */
    private String intent;

    /**
     * Scope: FLEET, DRIVER, VEHICLE, GENERAL, UNKNOWN
     */
    private String scope;

    /**
     * Conversational direct reply for greetings, chit-chat, explanations, or clarifications.
     */
    private String conversationalResponse;

    /**
     * Extracted driver code if mentioned (e.g. "DRV001")
     */
    private String driverCode;

    /**
     * Extracted driver name hint if mentioned (e.g. "Rohan")
     */
    private String driverName;

    /**
     * Extracted vehicle code if mentioned (e.g. "VH003")
     */
    private String vehicleCode;

    /**
     * Extracted registration if mentioned (e.g. "MH03AC4582")
     */
    private String vehicleRegistration;

    /**
     * Period: DAILY, WEEKLY, MONTHLY, CUSTOM, ALL_TIME
     */
    private String period;

    /**
     * Date from (YYYY-MM-DD)
     */
    private String dateFrom;

    /**
     * Date to (YYYY-MM-DD)
     */
    private String dateTo;

    /**
     * Year-month (YYYY-MM)
     */
    private String yearMonth;

    /**
     * Specific alert type if mentioned (e.g. "Overspeed", "Harsh Braking")
     */
    private String alertType;

    /**
     * Ranking or recommendation limit if specified (e.g. 3, 5, 10)
     */
    private Integer limit;

    /**
     * Whether clarification is requested from the user.
     */
    private Boolean needsClarification;

    /**
     * Suggested quick action buttons.
     */
    private List<String> quickActions;
}
