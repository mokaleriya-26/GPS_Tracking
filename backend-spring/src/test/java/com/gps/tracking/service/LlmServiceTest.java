package com.gps.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gps.tracking.dto.LlmIntentResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LlmServiceTest {

    private ObjectMapper objectMapper;
    private LlmService llmService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        llmService = new LlmService(objectMapper);
        // Configure with default Gemini settings
        llmService.setConfig(
            true,
            "gemini",
            "test-gemini-key-987654",
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "gemini-1.5-flash",
            0.1,
            15
        );
    }

    @Test
    @DisplayName("Verify default Gemini provider, endpoint, and model settings")
    void testGeminiConfigurationSettings() {
        assertThat(llmService.getProvider()).isEqualTo("gemini");
        assertThat(llmService.resolveApiUrl())
            .isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
        assertThat(llmService.getModel()).isEqualTo("gemini-1.5-flash");
        assertThat(llmService.getTemperature()).isEqualTo(0.1);
        assertThat(llmService.getTimeoutSeconds()).isEqualTo(15);
    }

    @Test
    @DisplayName("Verify isLlmEnabled conditions (null key, placeholder, disabled)")
    void testIsLlmEnabledConditions() {
        assertThat(llmService.isLlmEnabled()).isTrue();

        // Disabled when enabled flag is false
        llmService.setConfig(false, "gemini", "valid-key", "", "gemini-1.5-flash", 0.1, 15);
        assertThat(llmService.isLlmEnabled()).isFalse();

        // Disabled when apiKey is null or empty
        llmService.setConfig(true, "gemini", "", "", "gemini-1.5-flash", 0.1, 15);
        assertThat(llmService.isLlmEnabled()).isFalse();

        llmService.setConfig(true, "gemini", null, "", "gemini-1.5-flash", 0.1, 15);
        assertThat(llmService.isLlmEnabled()).isFalse();

        // Disabled when apiKey is still template placeholder
        llmService.setConfig(true, "gemini", "your_gemini_api_key_here", "", "gemini-1.5-flash", 0.1, 15);
        assertThat(llmService.isLlmEnabled()).isFalse();
    }

    @Test
    @DisplayName("Verify WebClient request sets Bearer authorization and Gemini OpenAI endpoint")
    void testAnalyzeMessageWithMockWebClient() {
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();

        String mockResponseBody = """
            {
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "{\\"intent\\": \\"FLEET_SUMMARY\\", \\"scope\\": \\"FLEET\\", \\"conversationalResponse\\": null}"
                  }
                }
              ]
            }
            """;

        ExchangeFunction exchangeFunction = request -> {
            capturedAuthHeader.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            capturedContentType.set(request.headers().getFirst(HttpHeaders.CONTENT_TYPE));
            return Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(mockResponseBody)
                    .build()
            );
        };

        WebClient mockWebClient = WebClient.builder()
            .baseUrl(llmService.resolveApiUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-gemini-key-987654")
            .exchangeFunction(exchangeFunction)
            .build();

        llmService.setWebClient(mockWebClient);

        Optional<LlmIntentResponseDTO> result = llmService.analyzeMessage("Give me a fleet summary", Map.of("last_subject", "FLEET"));

        assertThat(result).isPresent();
        assertThat(result.get().getIntent()).isEqualTo("FLEET_SUMMARY");
        assertThat(result.get().getScope()).isEqualTo("FLEET");

        // Verify Bearer authorization and content-type headers
        assertThat(capturedAuthHeader.get()).isEqualTo("Bearer test-gemini-key-987654");
        assertThat(capturedContentType.get()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("Verify markdown code blocks are safely stripped from LLM JSON responses")
    void testCleanJsonContent() {
        String wrappedJson = "```json\n{\"intent\": \"DRIVER_RANKING\", \"scope\": \"DRIVER\"}\n```";
        String cleaned = llmService.cleanJsonContent(wrappedJson);
        assertThat(cleaned).isEqualTo("{\"intent\": \"DRIVER_RANKING\", \"scope\": \"DRIVER\"}");

        String plainJson = "{\"intent\": \"SMALL_TALK\"}";
        assertThat(llmService.cleanJsonContent(plainJson)).isEqualTo(plainJson);

        assertThat(llmService.cleanJsonContent(null)).isNull();
    }

    @Test
    @DisplayName("Verify graceful fallback when Gemini API returns HTTP error")
    void testGracefulFallbackOnHttpError() {
        ExchangeFunction errorExchange = request -> Mono.just(
            ClientResponse.create(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": {\"message\": \"API key not valid\"}}")
                .build()
        );

        WebClient errorWebClient = WebClient.builder()
            .baseUrl(llmService.resolveApiUrl())
            .exchangeFunction(errorExchange)
            .build();

        llmService.setWebClient(errorWebClient);

        Optional<LlmIntentResponseDTO> result = llmService.analyzeMessage("Hello chatbot", Map.of());
        assertThat(result).isEmpty(); // Falls back gracefully
    }

    @Test
    @DisplayName("Verify buildSystemPrompt incorporates active session context")
    void testBuildSystemPromptContext() {
        Map<String, String> ctx = Map.of(
            "last_subject", "DRIVER_PERFORMANCE",
            "last_driver_name", "Merin Joys",
            "last_vehicle_code", "VH001"
        );

        String prompt = llmService.buildSystemPrompt(ctx);
        assertThat(prompt).contains("Active Session Context");
        assertThat(prompt).contains("Last Driver: Merin Joys");
        assertThat(prompt).contains("Last Vehicle: VH001");
        assertThat(prompt).contains("Last Subject: DRIVER_PERFORMANCE");
        assertThat(prompt).contains("Return ONLY valid JSON");
    }
}
