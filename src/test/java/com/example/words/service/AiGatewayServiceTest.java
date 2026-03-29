package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.words.dto.AiChatMessageRequest;
import com.example.words.exception.BadGatewayException;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AiConfig;
import com.example.words.model.AiConfigStatus;
import com.example.words.model.UserRole;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AiConfigCryptoService aiConfigCryptoService;

    private AiGatewayService aiGatewayService;

    @BeforeEach
    void setUp() {
        aiGatewayService = new AiGatewayService(restTemplate, aiConfigCryptoService);
    }

    @Test
    void generateTextShouldReturnAssistantContent() {
        AiConfig config = config();
        when(aiConfigCryptoService.decrypt(config.getApiKeyEncrypted())).thenReturn("sk-plain");
        when(restTemplate.exchange(
                eq(config.getApiUrl()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", "test-ok")
                ))
        )));

        String result = aiGatewayService.generateText(
                config,
                List.of(new AiChatMessageRequest("user", "hello"))
        );

        assertEquals("test-ok", result);
    }

    @Test
    void generateTextShouldTranslateUnauthorizedProviderError() {
        AiConfig config = config();
        when(aiConfigCryptoService.decrypt(config.getApiKeyEncrypted())).thenReturn("sk-plain");
        when(restTemplate.exchange(
                eq(config.getApiUrl()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        ));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiGatewayService.generateText(
                config,
                List.of(new AiChatMessageRequest("user", "hello"))
        ));

        assertInstanceOf(BadRequestException.class, exception);
        assertEquals("AI provider rejected the API key", exception.getMessage());
    }

    @Test
    void generateTextShouldTranslateRateLimitError() {
        AiConfig config = config();
        when(aiConfigCryptoService.decrypt(config.getApiKeyEncrypted())).thenReturn("sk-plain");
        when(restTemplate.exchange(
                eq(config.getApiUrl()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        ));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiGatewayService.generateText(
                config,
                List.of(new AiChatMessageRequest("user", "hello"))
        ));

        assertInstanceOf(BadGatewayException.class, exception);
        assertEquals("AI provider rate limit exceeded", exception.getMessage());
    }

    @Test
    void generateTextShouldTranslateServerError() {
        AiConfig config = config();
        when(aiConfigCryptoService.decrypt(config.getApiKeyEncrypted())).thenReturn("sk-plain");
        when(restTemplate.exchange(
                eq(config.getApiUrl()),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        ));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiGatewayService.generateText(
                config,
                List.of(new AiChatMessageRequest("user", "hello"))
        ));

        assertInstanceOf(BadGatewayException.class, exception);
        assertEquals("AI provider is temporarily unavailable", exception.getMessage());
    }

    private AiConfig config() {
        AiConfig config = new AiConfig();
        config.setId(1L);
        config.setUserId(1L);
        config.setUserRole(UserRole.ADMIN);
        config.setProviderName("OpenAI");
        config.setApiUrl("https://api.openai.com/v1/chat/completions");
        config.setApiKeyEncrypted("v1:encrypted");
        config.setApiKeyMasked("sk-t****1234");
        config.setModelName("gpt-4o-mini");
        config.setStatus(AiConfigStatus.ENABLED);
        config.setIsDefault(Boolean.TRUE);
        return config;
    }
}
