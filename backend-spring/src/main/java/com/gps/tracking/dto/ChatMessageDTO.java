package com.gps.tracking.dto;

import lombok.Data;

@Data
public class ChatMessageDTO {
    private String sessionId;
    private String message;
    private String context; // Optional JSON context from previous messages
}
