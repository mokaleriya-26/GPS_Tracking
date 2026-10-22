package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponseDTO {
    @Builder.Default
    private Boolean success = true;
    private String sessionId;
    private String message;
    private String intent;
    private String source; // e.g. "POSTGRESQL", "LLM", "SYSTEM"
    private String reportId;
    private String pdfUrl;
    private String downloadUrl;
    private Object data; // Structured data (tables, rankings, driver details, etc.)
    private Map<String, Object> filters; // Applied filters: alertType, severity, date range, etc.
    private boolean askingForClarification;
    private String clarificationQuestion;
    private List<String> quickActions;
}
