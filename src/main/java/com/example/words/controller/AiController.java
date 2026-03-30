package com.example.words.controller;

import com.example.words.dto.AiChatRequest;
import com.example.words.dto.AiChatResponse;
import com.example.words.dto.GenerateReadingRequest;
import com.example.words.dto.GenerateReadingResponse;
import com.example.words.dto.GenerateWordDetailsRequest;
import com.example.words.dto.GenerateWordDetailsResponse;
import com.example.words.service.AiConversationService;
import com.example.words.service.AiGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
public class AiController {

    private final AiConversationService aiConversationService;
    private final AiGenerationService aiGenerationService;

    public AiController(
            AiConversationService aiConversationService,
            AiGenerationService aiGenerationService) {
        this.aiConversationService = aiConversationService;
        this.aiGenerationService = aiGenerationService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(aiConversationService.chat(request));
    }

    @PostMapping("/generate-reading")
    public ResponseEntity<GenerateReadingResponse> generateReading(@Valid @RequestBody GenerateReadingRequest request) {
        return ResponseEntity.ok(aiGenerationService.generateReading(request));
    }

    @PostMapping("/generate-word-details")
    public ResponseEntity<GenerateWordDetailsResponse> generateWordDetails(
            @Valid @RequestBody GenerateWordDetailsRequest request) {
        return ResponseEntity.ok(aiGenerationService.generateWordDetails(request));
    }
}
