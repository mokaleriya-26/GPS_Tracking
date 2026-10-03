package com.gps.tracking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponseDTO {
    private String sessionId;
    private String message;
    private String intent;
    private String reportId;
    private String pdfUrl;
    private String downloadUrl;
    private Object data; // Structured data (tables, rankings, etc.)
    private boolean askingForClarification;
    private String clarificationQuestion;
}
