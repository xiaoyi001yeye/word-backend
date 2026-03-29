package com.example.words.service;

import com.example.words.dto.GenerateReadingRequest;
import com.example.words.dto.GenerateReadingResponse;
import com.example.words.model.AiConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGenerationService {

    private final AiConfigService aiConfigService;
    private final AiPromptService aiPromptService;
    private final AiGatewayService aiGatewayService;

    public AiGenerationService(
            AiConfigService aiConfigService,
            AiPromptService aiPromptService,
            AiGatewayService aiGatewayService) {
        this.aiConfigService = aiConfigService;
        this.aiPromptService = aiPromptService;
        this.aiGatewayService = aiGatewayService;
    }

    @Transactional(readOnly = true)
    public GenerateReadingResponse generateReading(GenerateReadingRequest request) {
        AiConfig config = aiConfigService.resolveActiveConfig(request.getConfigId());
        String rawContent = aiGatewayService.generateText(config, aiPromptService.buildReadingMessages(request));
        ParsedReading reading = parseReading(rawContent);
        return new GenerateReadingResponse(
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                reading.title(),
                reading.content()
        );
    }

    private ParsedReading parseReading(String rawContent) {
        String normalized = rawContent == null ? "" : rawContent.trim();
        if (normalized.isBlank()) {
            return new ParsedReading("Untitled", "");
        }

        String[] lines = normalized.split("\\R");
        String title = null;
        StringBuilder contentBuilder = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            if (title == null) {
                title = stripLabel(trimmedLine, "标题：", "标题:", "Title:", "Title：");
                if (!contentBuilder.isEmpty()) {
                    contentBuilder.append('\n');
                }
                continue;
            }

            String contentLine = stripLabel(trimmedLine, "正文：", "正文:", "Content:", "Content：");
            if (!contentBuilder.isEmpty()) {
                contentBuilder.append('\n');
            }
            contentBuilder.append(contentLine);
        }

        if (title == null || title.isBlank()) {
            int newlineIndex = normalized.indexOf('\n');
            if (newlineIndex > 0) {
                title = normalized.substring(0, newlineIndex).trim();
                String remaining = normalized.substring(newlineIndex + 1).trim();
                contentBuilder = new StringBuilder(remaining);
            } else {
                title = "Generated Reading";
                contentBuilder = new StringBuilder(normalized);
            }
        }

        return new ParsedReading(title, contentBuilder.toString().trim());
    }

    private String stripLabel(String value, String... labels) {
        for (String label : labels) {
            if (value.startsWith(label)) {
                return value.substring(label.length()).trim();
            }
        }
        return value;
    }

    private record ParsedReading(String title, String content) {
    }
}
