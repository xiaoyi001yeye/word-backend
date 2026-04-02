package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.AiChatMessageRequest;
import com.example.words.dto.GenerateWordDetailsRequest;
import com.example.words.dto.GenerateWordDetailsResponse;
import com.example.words.exception.BadGatewayException;
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
class AiGenerationServiceTest {

    @Mock
    private AiConfigService aiConfigService;

    @Mock
    private AiPromptService aiPromptService;

    @Mock
    private AiGatewayService aiGatewayService;

    private AiGenerationService aiGenerationService;

    @BeforeEach
    void setUp() {
        aiGenerationService = new AiGenerationService(aiConfigService, aiPromptService, aiGatewayService);
    }

    @Test
    void generateWordDetailsShouldUseResolvedConfigAndParseJson() {
        AiConfig config = config();
        GenerateWordDetailsRequest request = new GenerateWordDetailsRequest(null, "apple");
        List<AiChatMessageRequest> messages = List.of(new AiChatMessageRequest("user", "fill apple"));

        when(aiConfigService.resolveActiveConfig(null)).thenReturn(config);
        when(aiPromptService.buildWordDetailsMessages(request)).thenReturn(messages);
        when(aiGatewayService.generateText(config, messages)).thenReturn("""
                ```json
                {
                  "word": "apple",
                  "translation": "苹果",
                  "partOfSpeech": "noun",
                  "phonetic": "/ˈæp.əl/",
                  "definition": "a round fruit with red, green, or yellow skin",
                  "exampleSentence": "She ate an apple after lunch."
                }
                ```
                """);

        GenerateWordDetailsResponse response = aiGenerationService.generateWordDetails(request);

        assertEquals(config.getId(), response.getConfigId());
        assertEquals("apple", response.getWord());
        assertEquals("苹果", response.getTranslation());
        assertEquals("noun", response.getPartOfSpeech());
        assertEquals("/ˈæp.əl/", response.getPhonetic());
        assertEquals("a round fruit with red, green, or yellow skin", response.getDefinition());
        assertEquals("She ate an apple after lunch.", response.getExampleSentence());
        verify(aiConfigService).resolveActiveConfig(null);
    }

    @Test
    void generateWordDetailsShouldRejectInvalidJson() {
        AiConfig config = config();
        GenerateWordDetailsRequest request = new GenerateWordDetailsRequest(null, "apple");
        List<AiChatMessageRequest> messages = List.of(new AiChatMessageRequest("user", "fill apple"));

        when(aiConfigService.resolveActiveConfig(null)).thenReturn(config);
        when(aiPromptService.buildWordDetailsMessages(request)).thenReturn(messages);
        when(aiGatewayService.generateText(config, messages)).thenReturn("not-json");

        BadGatewayException exception = assertThrows(
                BadGatewayException.class,
                () -> aiGenerationService.generateWordDetails(request)
        );

        assertEquals("AI returned invalid word details JSON", exception.getMessage());
    }

    private AiConfig config() {
        AiConfig config = new AiConfig();
        config.setId(21L);
        config.setProviderName("OpenAI");
        config.setModelName("gpt-4o-mini");
        config.setStatus(AiConfigStatus.ENABLED);
        config.setUserRole(UserRole.TEACHER);
        return config;
    }
}
