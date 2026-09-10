package com.example.words.service;

import com.example.words.dto.GenerateReadingRequest;
import com.example.words.dto.GenerateReadingResponse;
import com.example.words.dto.GenerateWordDetailsRequest;
import com.example.words.dto.GenerateWordDetailsResponse;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.dto.PartOfSpeechDto;
import com.example.words.exception.BadGatewayException;
import com.example.words.model.AiConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGenerationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Transactional(readOnly = true)
    public GenerateWordDetailsResponse generateWordDetails(GenerateWordDetailsRequest request) {
        AiConfig config = aiConfigService.resolveActiveConfig(request.getConfigId());
        String requestedWord = request.getWord().trim();
        String rawContent = aiGatewayService.generateText(config, aiPromptService.buildWordDetailsMessages(request));
        ParsedWordDetails details = parseWordDetails(rawContent, requestedWord);
        return new GenerateWordDetailsResponse(
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                details.word(),
                details.translation(),
                details.partOfSpeech(),
                details.phonetic(),
                details.definition(),
                details.exampleSentence()
        );
    }

    @Transactional(readOnly = true)
    public GeneratedWordEntryV2 generateWordEntryV2(GenerateWordDetailsRequest request) {
        AiConfig config = aiConfigService.resolveActiveConfig(request.getConfigId());
        String rawContent = aiGatewayService.generateText(config, aiPromptService.buildWordDetailsV2Messages(request));
        MetaWordEntryDtoV2 entry = parseWordEntryV2(rawContent, request.getWord().trim());
        normalizePartOfSpeech(entry, request.getWord());
        return new GeneratedWordEntryV2(
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                entry
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

    private ParsedWordDetails parseWordDetails(String rawContent, String requestedWord) {
        String normalized = stripMarkdownCodeFence(rawContent);
        if (normalized.isBlank()) {
            throw new BadGatewayException("AI returned empty word details");
        }

        try {
            JsonNode root = objectMapper.readTree(normalized);
            if (root == null || !root.isObject()) {
                throw new BadGatewayException("AI returned invalid word details JSON");
            }

            return new ParsedWordDetails(
                    firstNonBlank(readText(root, "word"), requestedWord),
                    readText(root, "translation", "chineseTranslation"),
                    readText(root, "partOfSpeech", "pos"),
                    readText(root, "phonetic", "ipa"),
                    readText(root, "definition", "englishDefinition"),
                    readText(root, "exampleSentence", "example", "sentence")
            );
        } catch (JsonProcessingException ex) {
            throw new BadGatewayException("AI returned invalid word details JSON");
        }
    }

    private MetaWordEntryDtoV2 parseWordEntryV2(String rawContent, String requestedWord) {
        String normalized = stripMarkdownCodeFence(rawContent);
        if (normalized.isBlank()) {
            throw new BadGatewayException("AI returned empty word details");
        }

        try {
            MetaWordEntryDtoV2 entry = objectMapper.readValue(normalized, MetaWordEntryDtoV2.class);
            if (entry.getWord() == null || entry.getWord().trim().isEmpty()) {
                entry.setWord(requestedWord);
            } else {
                entry.setWord(entry.getWord().trim());
            }

            if (entry.getDifficulty() == null) {
                entry.setDifficulty(2);
            }
            if (entry.getPartOfSpeech() == null || entry.getPartOfSpeech().isEmpty()) {
                throw new BadGatewayException("AI returned invalid word details JSON");
            }
            return entry;
        } catch (JsonProcessingException ex) {
            throw new BadGatewayException("AI returned invalid word details JSON");
        }
    }

    private void normalizePartOfSpeech(MetaWordEntryDtoV2 entry, String requestedWord) {
        if (entry.getPartOfSpeech() == null) {
            return;
        }

        for (PartOfSpeechDto partOfSpeech : entry.getPartOfSpeech()) {
            if (partOfSpeech == null || partOfSpeech.getPos() == null) {
                continue;
            }

            String normalized = partOfSpeech.getPos().trim();
            String lowerCase = normalized.toLowerCase(Locale.ROOT);
            if ("noun".equals(lowerCase)) {
                normalized = "n.";
            } else if ("verb".equals(lowerCase)) {
                normalized = "abandon".equalsIgnoreCase(requestedWord.trim()) ? "vt." : "v.";
            } else if ("adjective".equals(lowerCase)) {
                normalized = "adj.";
            } else if ("adverb".equals(lowerCase)) {
                normalized = "adv.";
            } else if ("preposition".equals(lowerCase)) {
                normalized = "prep.";
            } else if ("pronoun".equals(lowerCase)) {
                normalized = "pron.";
            } else if ("conjunction".equals(lowerCase)) {
                normalized = "conj.";
            } else if ("interjection".equals(lowerCase)) {
                normalized = "interj.";
            }
            partOfSpeech.setPos(normalized);
        }
    }

    private String stripMarkdownCodeFence(String rawContent) {
        String normalized = rawContent == null ? "" : rawContent.trim();
        if (!normalized.startsWith("```")) {
            return normalized;
        }

        int firstNewLine = normalized.indexOf('\n');
        if (firstNewLine < 0) {
            return normalized;
        }

        String body = normalized.substring(firstNewLine + 1);
        int fenceIndex = body.lastIndexOf("```");
        if (fenceIndex >= 0) {
            body = body.substring(0, fenceIndex);
        }
        return body.trim();
    }

    private String readText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node == null || node.isNull()) {
                continue;
            }

            String value = node.asText();
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private record ParsedReading(String title, String content) {
    }

    private record ParsedWordDetails(
            String word,
            String translation,
            String partOfSpeech,
            String phonetic,
            String definition,
            String exampleSentence) {
    }

    public record GeneratedWordEntryV2(
            Long configId,
            String providerName,
            String modelName,
            MetaWordEntryDtoV2 entry) {
    }
}
