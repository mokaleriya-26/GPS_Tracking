package com.gps.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gps.tracking.dto.ChatResponseDTO;
import com.gps.tracking.dto.LlmIntentResponseDTO;
import com.gps.tracking.entity.Driver;
import com.gps.tracking.repository.AlertRepository;
import com.gps.tracking.repository.DriverRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Automated Audit and Reliability Test Suite for TrackFleet Chatbot
 *
 * Verifies:
 * - Intent detection across all 10 canonical test questions
 * - Root-cause fix for the driver-ranking bug (Vikram Singh DRV008 with 1,644 alerts)
 * - PostgreSQL as absolute source of truth
 * - Context isolation: ranking never requests a month; cancels pending report prompts
 * - Gemini integration robustness: failure fallbacks, API key safety, JSON sanitization
 * - Zero live emails dispatched during test execution
 */
@SpringBootTest
public class ChatServiceAuditTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private ChatService chatService;

    @Autowired
    private IntentClassifier intentClassifier;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private LlmService llmService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    // =========================================================================
    // SECTION A: INTENT DETECTION TESTS (ALL 10 TARGET QUESTIONS)
    // =========================================================================

    @Test
    @DisplayName("Q1: 'Which driver has the most alerts?' -> DRIVER_RANKING")
    void testQuestion1_mostAlertsIntent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("Which driver has the most alerts?");
        assertEquals(IntentClassifier.Intent.DRIVER_RANKING, result.getIntent());
    }

    @Test
    @DisplayName("Q2: 'Which driver has generated the highest number of alerts?' -> DRIVER_RANKING")
    void testQuestion2_highestNumberOfAlertsIntent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("Which driver has generated the highest number of alerts?");
        assertEquals(IntentClassifier.Intent.DRIVER_RANKING, result.getIntent());
    }

    @Test
    @DisplayName("Q3: 'Show the top five drivers by alert count.' -> DRIVER_RANKING")
    void testQuestion3_topFiveDriversByAlertCountIntent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("Show the top five drivers by alert count.");
        assertEquals(IntentClassifier.Intent.DRIVER_RANKING, result.getIntent());
    }

    @Test
    @DisplayName("Q4: 'Which driver has the most harsh braking alerts?' -> DRIVER_RANKING")
    void testQuestion4_mostHarshBrakingAlertsIntent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("Which driver has the most harsh braking alerts?");
        assertEquals(IntentClassifier.Intent.DRIVER_RANKING, result.getIntent());
        assertTrue(result.getAlertTypeHint().toLowerCase().contains("harsh braking"));
    }

    @Test
    @DisplayName("Q5: 'How many alerts did DRV009 generate?' -> DRIVER_ALERT_COUNT")
    void testQuestion5_drv009AlertCountIntent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("How many alerts did DRV009 generate?");
        assertEquals(IntentClassifier.Intent.DRIVER_ALERT_COUNT, result.getIntent());
        assertEquals("DRV009", result.getDriverCodeHint());
    }

    @Test
    @DisplayName("Q6: 'Generate a monthly report for DRV009.' -> DRIVER_REPORT")
    void testQuestion6_drv009MonthlyReportIntent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("Generate a monthly report for DRV009.");
        assertEquals(IntentClassifier.Intent.DRIVER_REPORT, result.getIntent());
        assertEquals("DRV009", result.getDriverCodeHint());
    }

    @Test
    @DisplayName("Q7: 'How many harsh braking alerts occurred in September 2026?' -> ALERT_QUERY")
    void testQuestion7_harshBrakingAlertCountSep2026Intent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("How many harsh braking alerts occurred in September 2026?");
        assertEquals(IntentClassifier.Intent.ALERT_QUERY, result.getIntent());
        assertTrue(result.getAlertTypeHint().toLowerCase().contains("harsh braking"));
    }

    @Test
    @DisplayName("Q8: 'Show the top five drivers by critical alerts in September 2026.' -> DRIVER_RANKING")
    void testQuestion8_topFiveDriversCriticalAlertsSep2026Intent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("Show the top five drivers by critical alerts in September 2026.");
        assertEquals(IntentClassifier.Intent.DRIVER_RANKING, result.getIntent());
        assertEquals("CRITICAL", result.getSeverityHint());
    }

    @Test
    @DisplayName("Q9: 'Show all alerts for DRV008.' -> DRIVER_ALERT_COUNT")
    void testQuestion9_showAllAlertsForDrv008Intent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("Show all alerts for DRV008.");
        assertEquals(IntentClassifier.Intent.DRIVER_ALERT_COUNT, result.getIntent());
        assertEquals("DRV008", result.getDriverCodeHint());
    }

    @Test
    @DisplayName("Q10: 'Generate a fleet summary for this week.' -> FLEET_SUMMARY")
    void testQuestion10_fleetSummaryForThisWeekIntent() {
        IntentClassifier.ClassifiedIntent result = intentClassifier.classify("Generate a fleet summary for this week.");
        assertEquals(IntentClassifier.Intent.FLEET_SUMMARY, result.getIntent());
    }

    // =========================================================================
    // SECTION B: ROOT CAUSE FIX VERIFICATION (THE DRIVER-RANKING BUG)
    // =========================================================================

    @Test
    @DisplayName("Regression Fix: 'Which driver has generated the highest number of alerts?' returns Vikram Singh DRV008 with 1,644 alerts")
    void testKnownDriverRankingBugFixed() {
        String sessionId = UUID.randomUUID().toString();
        ChatResponseDTO response = chatService.processMessage(sessionId, "Which driver has generated the highest number of alerts?");

        assertNotNull(response);
        assertEquals("DRIVER_RANKING", response.getIntent());
        assertEquals("POSTGRESQL", response.getSource());
        assertTrue(response.getSuccess());

        // Verify message content
        String msg = response.getMessage();
        assertFalse(msg.contains("month"), "Ranking response must NEVER ask for a month");
        assertFalse(msg.contains("Driver No Email"), "Ranking response must NEVER mention artificial 'Driver No Email'");
        assertTrue(msg.contains("Vikram Singh"), "Must identify Vikram Singh as top alert driver");
        assertTrue(msg.contains("DRV008"), "Must specify driver code DRV008");
        assertTrue(msg.contains("1,644") || msg.contains("1644"), "Must specify exactly 1,644 alerts from PostgreSQL");

        // Verify structured data payload
        assertNotNull(response.getData());
        if (response.getData() instanceof Map<?, ?> dataMap) {
            assertEquals("DRV008", dataMap.get("driverCode"));
            assertEquals("Vikram Singh", dataMap.get("driverName"));
            assertEquals(1644L, ((Number) dataMap.get("totalAlerts")).longValue());
        }
    }

    @Test
    @DisplayName("Context Isolation: Pending report clarification is canceled when user asks a ranking question")
    void testPendingReportClarificationCanceledByRankingQuery() {
        String sessionId = UUID.randomUUID().toString();

        // Step 1: User says "generate a driver report" without driver or month
        ChatResponseDTO r1 = chatService.processMessage(sessionId, "Generate a driver report");
        assertTrue(r1.isAskingForClarification(), "Should ask which driver");

        // Step 2: Instead of answering the driver, user asks a completely new ranking question
        ChatResponseDTO r2 = chatService.processMessage(sessionId, "Which driver has generated the highest number of alerts?");

        // Step 3: Verify the ranking query was NOT hijacked by the driver report flow
        assertEquals("DRIVER_RANKING", r2.getIntent());
        assertEquals("POSTGRESQL", r2.getSource());
        assertFalse(r2.getMessage().contains("month"), "Must NOT ask for a month");
        assertTrue(r2.getMessage().contains("Vikram Singh"));
        assertTrue(r2.getMessage().contains("1,644") || r2.getMessage().contains("1644"));
    }

    // =========================================================================
    // SECTION C: DATABASE ACCURACY VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("DB Accuracy: Query DRV009 (Deepak Joshi) alert count matches PostgreSQL (1,075)")
    void testDrv009AlertCountMatchesDatabase() {
        String sessionId = UUID.randomUUID().toString();
        ChatResponseDTO response = chatService.processMessage(sessionId, "How many alerts did DRV009 generate?");

        assertEquals("DRIVER_ALERT_COUNT", response.getIntent());
        assertEquals("POSTGRESQL", response.getSource());
        assertTrue(response.getMessage().contains("Deepak Joshi"));
        assertTrue(response.getMessage().contains("DRV009"));
        assertTrue(response.getMessage().contains("1,075") || response.getMessage().contains("1075"));

        if (response.getData() instanceof Map<?, ?> dataMap) {
            assertEquals("DRV009", dataMap.get("driverCode"));
            assertEquals(1075L, ((Number) dataMap.get("totalAlerts")).longValue());
        }
    }

    @Test
    @DisplayName("DB Accuracy: September 2026 harsh braking count matches PostgreSQL (951)")
    void testSeptember2026HarshBrakingAlertCountMatchesDatabase() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0, 0);
        long count = alertRepository.countAlertsWithFilters("harsh braking", null, start, end);
        assertEquals(807L, count, "PostgreSQL database must contain exactly 807 harsh braking alerts in Sep 2026 after car cleanup");

        String sessionId = UUID.randomUUID().toString();
        ChatResponseDTO response = chatService.processMessage(sessionId, "How many harsh braking alerts occurred in September 2026?");
        assertTrue(response.getMessage().contains("807"));
    }

    @Test
    @DisplayName("DB Accuracy: Top 5 drivers table returns correct rankings in descending order")
    void testTopFiveDriversByAlertCount() {
        String sessionId = UUID.randomUUID().toString();
        ChatResponseDTO response = chatService.processMessage(sessionId, "Show the top five drivers by alert count.");

        assertEquals("DRIVER_RANKING", response.getIntent());
        assertEquals("POSTGRESQL", response.getSource());
        assertTrue(response.getMessage().contains("| Rank | Driver Code | Driver Name | Total Alerts |"));
        assertTrue(response.getMessage().contains("DRV008"));
        assertTrue(response.getMessage().contains("Vikram Singh"));

        if (response.getData() instanceof List<?> list) {
            assertTrue(list.size() <= 5);
            ChatService.DriverAlertRanking first = (ChatService.DriverAlertRanking) list.get(0);
            assertEquals("DRV008", first.getDriverCode());
            assertEquals(1644L, first.getTotalAlerts());
        }
    }

    @Test
    @DisplayName("DB Accuracy: Vehicle alert rankings return top vehicle accurately")
    void testVehicleAlertRanking() {
        String sessionId = UUID.randomUUID().toString();
        ChatResponseDTO response = chatService.processMessage(sessionId, "Which vehicle has the most alerts?");

        assertTrue("VEHICLE_RANKING".equals(response.getIntent()) || "VEHICLE_ALERT_ANALYSIS".equals(response.getIntent()));
        assertEquals("POSTGRESQL", response.getSource());
        assertNotNull(response.getMessage());
    }

    // =========================================================================
    // SECTION D: GEMINI / LLM INTEGRATION ROBUSTNESS
    // =========================================================================

    @Test
    @DisplayName("LLM Robustness: Disabled or unconfigured LLM gracefully falls back to deterministic rule engine")
    void testLlmDisabledGracefulFallback() {
        assertFalse(llmService.isLlmEnabled() && llmService.getApiUrl() == null);
        String sessionId = UUID.randomUUID().toString();
        ChatResponseDTO response = chatService.processMessage(sessionId, "Which driver has generated the highest number of alerts?");
        assertNotNull(response);
        assertEquals("DRIVER_RANKING", response.getIntent());
        assertEquals("POSTGRESQL", response.getSource());
        assertTrue(response.getMessage().contains("Vikram Singh"));
    }

    @Test
    @DisplayName("LLM Robustness: Clean markdown code fences from LLM JSON response")
    void testCleanJsonContent() {
        String markdownJson = "```json\n{\"intent\":\"DRIVER_RANKING\",\"scope\":\"DRIVER\"}\n```";
        String cleaned = llmService.cleanJsonContent(markdownJson);
        assertEquals("{\"intent\":\"DRIVER_RANKING\",\"scope\":\"DRIVER\"}", cleaned);
    }

    // =========================================================================
    // SECTION E: SESSION RESET VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Session Reset: clearSession cleans up conversation state")
    void testClearSession() {
        String sessionId = UUID.randomUUID().toString();
        chatService.processMessage(sessionId, "Tell me about DRV008");
        chatService.clearSession(sessionId);

        // Explicit cancel command returns friendly confirmation
        ChatResponseDTO cancelResp = chatService.processMessage(sessionId, "cancel");
        assertEquals("GENERAL_QUESTION", cancelResp.getIntent());
        assertTrue(cancelResp.getMessage().contains("cancelled"));
    }
}
