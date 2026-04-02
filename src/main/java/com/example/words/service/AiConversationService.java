package com.example.words.service;

import com.example.words.dto.AiChatRequest;
import com.example.words.dto.AiChatResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AiConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConversationService {

    private final AiConfigService aiConfigService;
    private final AiGatewayService aiGatewayService;

    public AiConversationService(AiConfigService aiConfigService, AiGatewayService aiGatewayService) {
        this.aiConfigService = aiConfigService;
        this.aiGatewayService = aiGatewayService;
    }

    @Transactional(readOnly = true)
    public AiChatResponse chat(AiChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BadRequestException("Chat messages must not be empty");
        }

        AiConfig config = aiConfigService.getMyEnabledConfig(request.getConfigId());
        String reply = aiGatewayService.generateText(config, request.getMessages());
        return new AiChatResponse(
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                reply
        );
    }
}
