package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.AiChatMessageRequest;
import com.example.words.dto.AiChatRequest;
import com.example.words.dto.AiChatResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AiConfig;
import com.example.words.model.AiConfigStatus;
import com.example.words.model.UserRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTest {

    @Mock
    private AiConfigService aiConfigService;

    @Mock
    private AiGatewayService aiGatewayService;

    private AiConversationService aiConversationService;

    @BeforeEach
    void setUp() {
        aiConversationService = new AiConversationService(aiConfigService, aiGatewayService);
    }

    @Test
    void chatShouldRejectEmptyMessages() {
        AiChatRequest request = new AiChatRequest(1L, List.of());

        assertThrows(BadRequestException.class, () -> aiConversationService.chat(request));
    }

    @Test
    void chatShouldReturnGatewayReply() {
        AiConfig config = new AiConfig();
        config.setId(12L);
        config.setProviderName("OpenAI");
        config.setModelName("gpt-4o-mini");
        config.setStatus(AiConfigStatus.ENABLED);
        config.setUserRole(UserRole.TEACHER);
        AiChatRequest request = new AiChatRequest(
                12L,
                List.of(new AiChatMessageRequest("user", "hello"))
        );

        when(aiConfigService.getMyEnabledConfig(12L)).thenReturn(config);
        when(aiGatewayService.generateText(config, request.getMessages())).thenReturn("test-ok");

        AiChatResponse response = aiConversationService.chat(request);

        assertEquals(12L, response.getConfigId());
        assertEquals("OpenAI", response.getProviderName());
        assertEquals("gpt-4o-mini", response.getModelName());
        assertEquals("test-ok", response.getReply());
        verify(aiConfigService).getMyEnabledConfig(12L);
    }
}
