package com.example.words.service;

import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.words.dto.AiChatMessageRequest;
import com.example.words.dto.GenerateDictionaryWordWithAiRequest;
import com.example.words.dto.GenerateDictionaryWordWithAiResponse;
import com.example.words.dto.GenerateWordDetailsRequest;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.exception.BadGatewayException;
import com.example.words.model.AiConfig;
import com.example.words.model.AiConfigStatus;
import com.example.words.model.UserRole;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryWordAiAutofillServiceTest {

    @Mock
    private AiConfigService aiConfigService;

    @Mock
    private AiPromptService aiPromptService;

    @Mock
    private AiGatewayService aiGatewayService;

    @Mock
    private WordMarkdownMaterialService wordMarkdownMaterialService;

    @Mock
    private DictionaryWordService dictionaryWordService;

    private DictionaryWordAiAutofillService service;

    @BeforeEach
    void setUp() {
        service = new DictionaryWordAiAutofillService(
                new ObjectMapper(),
                aiConfigService,
                aiPromptService,
                aiGatewayService,
                wordMarkdownMaterialService,
                dictionaryWordService
        );
    }

    @Test
    void autofillShouldGenerateValidateAndSaveThroughOneCommand() {
        AiConfig config = config();
        GenerateDictionaryWordWithAiRequest request = new GenerateDictionaryWordWithAiRequest(null, 9L, 4L, "ability");
        List<AiChatMessageRequest> messages = List.of(new AiChatMessageRequest("user", "generate ability"));
        GenerateDictionaryWordWithAiResponse saved = savedResponse("ability");

        when(wordMarkdownMaterialService.findMaterial("ability")).thenReturn(Optional.of("教材材料"));
        when(dictionaryWordService.observeMetaWordVersion(9L, "ability", 4L)).thenReturn(4L);
        when(aiConfigService.resolveActiveConfig(null)).thenReturn(config);
        when(aiPromptService.buildWordDetailsV2Messages(any(GenerateWordDetailsRequest.class))).thenReturn(messages);
        when(aiGatewayService.generateText(config, messages)).thenReturn(validEntryJson("ability"));
        when(dictionaryWordService.saveGeneratedWordV2(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(saved);

        GenerateDictionaryWordWithAiResponse response = service.autofill(7L, request);

        assertEquals(saved, response);
        ArgumentCaptor<GenerateWordDetailsRequest> generationRequest =
                ArgumentCaptor.forClass(GenerateWordDetailsRequest.class);
        verify(aiPromptService).buildWordDetailsV2Messages(generationRequest.capture());
        assertEquals("ability", generationRequest.getValue().getWord());
        assertEquals("教材材料", generationRequest.getValue().getLearningMaterial());

        ArgumentCaptor<MetaWordEntryDtoV2> generatedEntry = ArgumentCaptor.forClass(MetaWordEntryDtoV2.class);
        verify(dictionaryWordService).saveGeneratedWordV2(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(21L),
                org.mockito.ArgumentMatchers.eq("OpenAI"),
                org.mockito.ArgumentMatchers.eq("gpt-test"),
                generatedEntry.capture(),
                org.mockito.ArgumentMatchers.eq("教材材料"),
                org.mockito.ArgumentMatchers.eq(4L)
        );
        assertEquals("ability", generatedEntry.getValue().getWord());
    }

    @Test
    void autofillShouldRetryOnceWithValidationErrorsThenSave() {
        AiConfig config = config();
        GenerateDictionaryWordWithAiRequest request = new GenerateDictionaryWordWithAiRequest(null, 9L, 4L, "ability");
        List<AiChatMessageRequest> initialMessages = List.of(new AiChatMessageRequest("user", "generate ability"));
        List<AiChatMessageRequest> repairMessages = List.of(new AiChatMessageRequest("user", "repair ability"));

        when(wordMarkdownMaterialService.findMaterial("ability")).thenReturn(Optional.empty());
        when(aiConfigService.resolveActiveConfig(null)).thenReturn(config);
        when(aiPromptService.buildWordDetailsV2Messages(any(GenerateWordDetailsRequest.class)))
                .thenReturn(initialMessages);
        when(aiPromptService.buildWordDetailsV2RepairMessages(
                any(GenerateWordDetailsRequest.class), any(), any()
        )).thenReturn(repairMessages);
        when(aiGatewayService.generateText(config, initialMessages)).thenReturn("{\"word\":\"wrong\"}");
        when(aiGatewayService.generateText(config, repairMessages)).thenReturn(validEntryJson("ability"));
        when(dictionaryWordService.saveGeneratedWordV2(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(savedResponse("ability"));

        service.autofill(7L, request);

        ArgumentCaptor<String> validationErrors = ArgumentCaptor.forClass(String.class);
        verify(aiPromptService).buildWordDetailsV2RepairMessages(
                any(GenerateWordDetailsRequest.class),
                org.mockito.ArgumentMatchers.eq("{\"word\":\"wrong\"}"),
                validationErrors.capture()
        );
        assertTrue(validationErrors.getValue().contains("word"));
        verify(dictionaryWordService).saveGeneratedWordV2(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void autofillShouldRejectSamePatternWordLongerThanEightyCharacters() {
        AiConfig config = config();
        GenerateDictionaryWordWithAiRequest request = new GenerateDictionaryWordWithAiRequest(null, 9L, 4L, "ability");
        List<AiChatMessageRequest> initialMessages = List.of(new AiChatMessageRequest("user", "generate ability"));
        List<AiChatMessageRequest> repairMessages = List.of(new AiChatMessageRequest("user", "repair ability"));
        String invalidEntry = validEntryJson("ability").replace(
                "\"samePatternWords\": []",
                "\"samePatternWords\": [{\"word\": \"" + "x".repeat(81)
                        + "\", \"translation\": \"测试\", \"rootBreakdown\": \"test root\"}]"
        );

        when(wordMarkdownMaterialService.findMaterial("ability")).thenReturn(Optional.empty());
        when(aiConfigService.resolveActiveConfig(null)).thenReturn(config);
        when(aiPromptService.buildWordDetailsV2Messages(any(GenerateWordDetailsRequest.class)))
                .thenReturn(initialMessages);
        when(aiPromptService.buildWordDetailsV2RepairMessages(
                any(GenerateWordDetailsRequest.class), any(), any()
        )).thenReturn(repairMessages);
        when(aiGatewayService.generateText(config, initialMessages)).thenReturn(invalidEntry);
        when(aiGatewayService.generateText(config, repairMessages)).thenReturn(validEntryJson("ability"));
        when(dictionaryWordService.saveGeneratedWordV2(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(savedResponse("ability"));

        service.autofill(7L, request);

        ArgumentCaptor<String> validationErrors = ArgumentCaptor.forClass(String.class);
        verify(aiPromptService).buildWordDetailsV2RepairMessages(
                any(GenerateWordDetailsRequest.class),
                org.mockito.ArgumentMatchers.eq(invalidEntry),
                validationErrors.capture()
        );
        assertTrue(validationErrors.getValue().contains("learningDetail.samePatternWords[0].word"));
        assertTrue(validationErrors.getValue().contains("80"));
    }

    @Test
    void autofillShouldRejectSecondInvalidResultWithoutSaving() {
        AiConfig config = config();
        GenerateDictionaryWordWithAiRequest request = new GenerateDictionaryWordWithAiRequest(null, 9L, 4L, "ability");
        List<AiChatMessageRequest> initialMessages = List.of(new AiChatMessageRequest("user", "generate ability"));
        List<AiChatMessageRequest> repairMessages = List.of(new AiChatMessageRequest("user", "repair ability"));

        when(wordMarkdownMaterialService.findMaterial("ability")).thenReturn(Optional.empty());
        when(aiConfigService.resolveActiveConfig(null)).thenReturn(config);
        when(aiPromptService.buildWordDetailsV2Messages(any(GenerateWordDetailsRequest.class)))
                .thenReturn(initialMessages);
        when(aiPromptService.buildWordDetailsV2RepairMessages(
                any(GenerateWordDetailsRequest.class), any(), any()
        )).thenReturn(repairMessages);
        when(aiGatewayService.generateText(config, initialMessages)).thenReturn("not-json");
        when(aiGatewayService.generateText(config, repairMessages)).thenReturn("still-not-json");

        BadGatewayException exception = assertThrows(
                BadGatewayException.class,
                () -> service.autofill(7L, request)
        );

        assertTrue(exception.getMessage().contains("after one repair attempt"));
        verify(dictionaryWordService, never()).saveGeneratedWordV2(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    private AiConfig config() {
        AiConfig config = new AiConfig();
        config.setId(21L);
        config.setProviderName("OpenAI");
        config.setModelName("gpt-test");
        config.setStatus(AiConfigStatus.ENABLED);
        config.setUserRole(UserRole.TEACHER);
        return config;
    }

    private GenerateDictionaryWordWithAiResponse savedResponse(String word) {
        return new GenerateDictionaryWordWithAiResponse(
                7L, 9L, 21L, "OpenAI", "gpt-test", word, "能力", "n.",
                "/əˈbɪləti/", "the power to do something", "She has the ability to lead.",
                1, 1, 0, 0, 0
        );
    }

    private String validEntryJson(String word) {
        return """
                {
                  "word": "%s",
                  "phonetic": {"uk": "/əˈbɪləti/", "us": "/əˈbɪləti/"},
                  "syllableDetail": {"segments": [
                    {"text": "abil", "ukPhonetic": "/əˈbɪl/", "usPhonetic": "/əˈbɪl/"},
                    {"text": "ity", "ukPhonetic": "/əti/", "usPhonetic": "/əti/"}
                  ]},
                  "partOfSpeech": [{
                    "pos": "n.",
                    "definitions": [{
                      "definition": "the power to do something",
                      "translation": "能力",
                      "exampleSentences": [{
                        "sentence": "She has the ability to lead.",
                        "translation": "她有领导能力。"
                      }]
                    }],
                    "synonyms": [],
                    "antonyms": []
                  }],
                  "difficulty": 2,
                  "learningDetail": {
                    "memoryHint": "able 加 ity 表示能力",
                    "samePatternWords": [],
                    "examPhrases": ["have the ability to do sth"],
                    "wordFamily": [{"word": "able", "pos": "adj.", "translation": "有能力的"}],
                    "confusableWords": []
                  }
                }
                """.formatted(word);
    }
}
