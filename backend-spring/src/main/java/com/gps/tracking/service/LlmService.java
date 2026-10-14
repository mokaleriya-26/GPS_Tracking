package com.gps.tracking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gps.tracking.dto.LlmIntentResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * LlmService — Communicates with optional LLM API endpoints (OpenAI / Groq / Ollama / compatible APIs).
 * 
 * Translates natural language questions into structured intents and entity parameters,
 * handles chit-chat, explanations, and casual conversation.
 * Always fails gracefully to rule-based fallback if disabled, timed out, or unconfigured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final ObjectMapper objectMapper;

    @Value("${ai.llm.enabled:false}")
    private boolean enabled;

    @Value("${ai.llm.api-key:}")
    private String apiKey;

    @Value("${ai.llm.api-url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.llm.model:gpt-4o-mini}")
    private String model;

    @Value("${ai.llm.temperature:0.2}")
    private double temperature;

    @Value("${ai.llm.timeout:15}")
    private int timeoutSeconds;

    public boolean isLlmEnabled() {
        return enabled && apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * Ask the LLM to understand the message in context and return a structured analysis.
     */
    public Optional<LlmIntentResponseDTO> analyzeMessage(String message, Map<String, String> sessionContext) {
        if (!isLlmEnabled() || message == null || message.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            WebClient webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .build();

            String systemPrompt = buildSystemPrompt(sessionContext);

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "temperature", temperature,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", message)
                ),
                "response_format", Map.of("type", "json_object")
            );

            String responseBody = webClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText();
                if (content != null && !content.isBlank()) {
                    LlmIntentResponseDTO parsed = objectMapper.readValue(content, LlmIntentResponseDTO.class);
                    log.info("LLM classified message '{}' to intent: {}", message, parsed.getIntent());
                    return Optional.of(parsed);
                }
            }
        } catch (Exception e) {
            log.warn("LLM API call failed or timed out (falling back to rule-based engine): {}", e.getMessage());
        }

        return Optional.empty();
    }

    private String buildSystemPrompt(Map<String, String> ctx) {
        String lastSubject = ctx.getOrDefault("last_subject", "NONE");
        String lastDriver = ctx.getOrDefault("last_driver_name", ctx.getOrDefault("last_driver_code", "NONE"));
        String lastVehicle = ctx.getOrDefault("last_vehicle_code", "NONE");
        LocalDate today = LocalDate.now();

        return "You are an intelligent NLP router and conversational assistant for an enterprise GPS Vehicle Tracking & Fleet System.\n" +
            "Current Date: " + today + " (Timezone: Asia/Kolkata).\n" +
            "Active Session Context:\n" +
            "- Last Subject: " + lastSubject + "\n" +
            "- Last Driver: " + lastDriver + "\n" +
            "- Last Vehicle: " + lastVehicle + "\n\n" +
            "Your task is to classify the user's intent and extract entities into JSON format.\n" +
            "Supported INTENTS:\n" +
            "1. GREETING: User says hello, hi, hey, howdy, good morning.\n" +
            "2. SMALL_TALK: Casual talk ('how are you', 'thanks bro', 'tell me a joke', 'nice work'). Include 'conversationalResponse' with a polite, natural reply.\n" +
            "3. CAPABILITIES: User asks what you can do or your features ('what can you do?', 'help me', 'who are you?'). Provide helpful explanation in 'conversationalResponse'.\n" +
            "4. EXPLANATION: User asks general or fleet conceptual questions ('explain that simply', 'how is safety score calculated?'). Provide clear answer in 'conversationalResponse'.\n" +
            "5. FLEET_SUMMARY: General overview of fleet status, operations, active vehicles, today's summary.\n" +
            "6. DRIVER_RANKING: Rank drivers, safest driver, riskiest driver, lowest score, top drivers, leaderboard.\n" +
            "7. DRIVER_RECOMMENDATION: Suggest or recommend drivers for a trip/route or best driver to assign.\n" +
            "8. DRIVER_STATS: Specific statistics or profile for a driver.\n" +
            "9. DRIVER_REPORT: Generate or view a driver's PDF report or performance report.\n" +
            "10. VEHICLE_STATS: Check vehicle details, registration, speed, active/inactive status.\n" +
            "11. VEHICLE_ALERT_ANALYSIS: Vehicles causing trouble, vehicles with most alerts/overspeeding.\n" +
            "12. OVERALL_REPORT: Fleet overall performance report or daily/weekly summary.\n" +
            "13. FLEET_REPORT: Generate monthly or periodic fleet PDF report.\n" +
            "14. REPORT_CLARIFICATION: User asks for 'the report' without specifying whether for driver or fleet.\n" +
            "15. ALERT_QUERY: Questions about alerts, open alerts, critical violations, overspeed, harsh braking.\n" +
            "16. TRIP_QUERY: Distance covered, trip counts, longest trip, average trip duration.\n" +
            "17. ENTRY_EXIT: Vehicles entering or exiting geofences or trips starting/ending.\n" +
            "18. COMPARE_DRIVERS: Compare two drivers or top drivers.\n" +
            "19. CLARIFICATION_NEEDED: Ambiguous request where crucial entity is missing.\n" +
            "20. UNKNOWN: Completely unrelated or unsupported request.\n\n" +
            "Return ONLY valid JSON matching this schema:\n" +
            "{\n" +
            "  \"intent\": \"<INTENT_NAME>\",\n" +
            "  \"scope\": \"FLEET | DRIVER | VEHICLE | GENERAL | UNKNOWN\",\n" +
            "  \"conversationalResponse\": \"<text if intent is GREETING, SMALL_TALK, CAPABILITIES, EXPLANATION, or CLARIFICATION_NEEDED, else null>\",\n" +
            "  \"driverCode\": \"<e.g. DRV001 or null>\",\n" +
            "  \"driverName\": \"<e.g. Rohan or null>\",\n" +
            "  \"vehicleCode\": \"<e.g. VH003 or null>\",\n" +
            "  \"vehicleRegistration\": \"<e.g. MH03AC4582 or null>\",\n" +
            "  \"period\": \"DAILY | WEEKLY | MONTHLY | CUSTOM | ALL_TIME | null\",\n" +
            "  \"dateFrom\": \"YYYY-MM-DD or null\",\n" +
            "  \"dateTo\": \"YYYY-MM-DD or null\",\n" +
            "  \"yearMonth\": \"YYYY-MM or null\",\n" +
            "  \"alertType\": \"<alert type name or null>\",\n" +
            "  \"limit\": <integer limit or null>,\n" +
            "  \"needsClarification\": <true | false>,\n" +
            "  \"quickActions\": [\"<action 1>\", \"<action 2>\"]\n" +
            "}";
    }
}
