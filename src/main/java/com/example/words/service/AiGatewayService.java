package com.example.words.service;

import com.example.words.dto.AiChatMessageRequest;
import com.example.words.exception.BadGatewayException;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AiConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getModelName());
        payload.put("temperature", 0.7d);
        payload.put("messages", buildMessages(messages));

        try {
            ResponseEntity<Map<String, Object>> response = aiRestTemplate.exchange(
                    config.getApiUrl(),
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
        if (statusCode == 401 || statusCode == 403) {
            return new BadRequestException("AI provider rejected the API key");
        }
        if (statusCode == 429) {
            return new BadGatewayException("AI provider rate limit exceeded");
        }
        if (statusCode >= 500) {
            return new BadGatewayException("AI provider is temporarily unavailable");
        }
        return new BadRequestException("AI provider request failed");
    }
}
