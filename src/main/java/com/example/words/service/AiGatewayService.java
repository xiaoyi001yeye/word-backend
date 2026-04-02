package com.example.words.service;

import com.example.words.dto.AiChatMessageRequest;
import com.example.words.exception.BadGatewayException;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AiConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class AiGatewayService {

    private final RestTemplate aiRestTemplate;
    private final AiConfigCryptoService aiConfigCryptoService;

    public AiGatewayService(RestTemplate aiRestTemplate, AiConfigCryptoService aiConfigCryptoService) {
        this.aiRestTemplate = aiRestTemplate;
        this.aiConfigCryptoService = aiConfigCryptoService;
    }

    public String generateText(AiConfig config, List<AiChatMessageRequest> messages) {
        String apiKey = aiConfigCryptoService.decrypt(config.getApiKeyEncrypted());
        String requestUrl = resolveChatCompletionsUrl(config.getApiUrl());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getModelName());
        payload.put("temperature", 0.7d);
        payload.put("messages", buildMessages(messages));

        try {
            ResponseEntity<Map<String, Object>> response = aiRestTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }
            );
            return extractContent(response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw translateProviderException(ex);
        } catch (ResourceAccessException ex) {
            throw new BadGatewayException("AI provider request timed out or is unreachable");
        }
    }

    private List<Map<String, String>> buildMessages(List<AiChatMessageRequest> messages) {
        List<Map<String, String>> payload = new ArrayList<>();
        for (AiChatMessageRequest message : messages) {
            payload.add(Map.of(
                    "role", message.getRole(),
                    "content", message.getContent().trim()
            ));
        }
        return payload;
    }

    private String resolveChatCompletionsUrl(String apiUrl) {
        String normalizedUrl = apiUrl == null ? "" : apiUrl.trim();
        if (normalizedUrl.isEmpty()) {
            return normalizedUrl;
        }

        String normalizedPath = UriComponentsBuilder.fromUriString(normalizedUrl)
                .build()
                .getPath();
        if (normalizedPath != null && normalizedPath.endsWith("/chat/completions")) {
            return normalizedUrl;
        }

        if (normalizedPath == null || normalizedPath.isBlank() || "/".equals(normalizedPath)) {
            normalizedPath = "/chat/completions";
        } else {
            normalizedPath = normalizedPath.endsWith("/")
                    ? normalizedPath + "chat/completions"
                    : normalizedPath + "/chat/completions";
        }

        return UriComponentsBuilder.fromUriString(normalizedUrl)
                .replacePath(normalizedPath)
                .build(true)
                .toUriString();
    }

    private String extractContent(Map<String, Object> body) {
        if (body == null) {
            throw new BadGatewayException("AI provider returned an empty response");
        }

        Object choicesObject = body.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) {
            throw new BadGatewayException("AI provider returned an invalid response");
        }

        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new BadGatewayException("AI provider returned an invalid response");
        }

        Object messageObject = choiceMap.get("message");
        if (!(messageObject instanceof Map<?, ?> messageMap)) {
            throw new BadGatewayException("AI provider returned an invalid response");
        }

        Object contentObject = messageMap.get("content");
        String content = extractTextContent(contentObject);
        if (content == null || content.isBlank()) {
            throw new BadGatewayException("AI provider returned an empty reply");
        }
        return content.trim();
    }

    private String extractTextContent(Object contentObject) {
        if (contentObject instanceof String text) {
            return text;
        }

        if (contentObject instanceof List<?> parts) {
            StringBuilder builder = new StringBuilder();
            for (Object part : parts) {
                if (!(part instanceof Map<?, ?> partMap)) {
                    continue;
                }
                Object text = partMap.get("text");
                if (text instanceof String textValue && !textValue.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(textValue.trim());
                }
            }
            return builder.toString();
        }

        return null;
    }

    private RuntimeException translateProviderException(HttpStatusCodeException ex) {
        int statusCode = ex.getStatusCode().value();
        String providerErrorDetail = extractProviderErrorDetail(ex.getResponseBodyAsString());
        if (statusCode == 401 || statusCode == 403) {
            return new BadRequestException(appendProviderDetail("AI provider rejected the API key", providerErrorDetail));
        }
        if (statusCode == 429) {
            return new BadGatewayException(appendProviderDetail("AI provider rate limit exceeded", providerErrorDetail));
        }
        if (statusCode >= 500) {
            return new BadGatewayException(appendProviderDetail("AI provider is temporarily unavailable", providerErrorDetail));
        }
        return new BadRequestException(
                appendProviderDetail("AI provider request failed with status " + statusCode, providerErrorDetail)
        );
    }

    private String extractProviderErrorDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 237) + "...";
    }

    private String appendProviderDetail(String message, String providerErrorDetail) {
        if (providerErrorDetail == null || providerErrorDetail.isBlank()) {
            return message;
        }
        return message + ": " + providerErrorDetail;
    }
}
