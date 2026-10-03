package com.gps.tracking.controller;

import com.gps.tracking.dto.*;
import com.gps.tracking.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chatbot", description = "Fleet management chatbot for natural-language queries")
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/message")
    @Operation(summary = "Send a message to the fleet chatbot")
    public ResponseEntity<ApiResponse<ChatResponseDTO>> sendMessage(@RequestBody ChatMessageDTO msg) {
        ChatResponseDTO response = chatService.processMessage(msg.getSessionId(), msg.getMessage());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
