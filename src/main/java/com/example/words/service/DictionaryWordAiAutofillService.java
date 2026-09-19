package com.example.words.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.example.words.dto.ConfusableWordDto;
import com.example.words.dto.DefinitionDto;
import com.example.words.dto.ExampleSentenceDto;
import com.example.words.dto.GenerateDictionaryWordWithAiRequest;
import com.example.words.dto.GenerateDictionaryWordWithAiResponse;
import com.example.words.dto.GenerateWordDetailsRequest;
import com.example.words.dto.LearningDetailDto;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.dto.PartOfSpeechDto;
import com.example.words.dto.SamePatternWordDto;
import com.example.words.dto.SyllableSegmentDto;
import com.example.words.dto.WordFamilyItemDto;
import com.example.words.exception.BadGatewayException;
import com.example.words.model.AiConfig;
import com.example.words.util.WordNormalizationUtils;

/**
 * Runs the complete dictionary-word AI autofill command behind one application seam.
 */
@Service
@Slf4j
public class DictionaryWordAiAutofillService {

    private static final int MAX_MEMORY_HINT_LENGTH = 80;
    private static final int MAX_LEARNING_MATERIAL_LENGTH = 6000;
    private static final int MAX_SHORT_TEXT_LENGTH = 80;
    private static final int MAX_WORD_LENGTH = 100;
    private static final int MAX_ROOT_BREAKDOWN_LENGTH = 160;
    private static final int MAX_EXAM_PHRASES = 3;
    private static final int MAX_WORD_FAMILY = 4;
    private static final int MAX_CONFUSABLE_WORDS = 2;
    private static final Set<String> ALLOWED_PARTS_OF_SPEECH = Set.of(
            "n.", "vt.", "vi.", "v.", "adj.", "adv.", "prep.", "pron.",
            "conj.", "interj.", "art.", "num.", "aux.", "modal v."
    );

    private final ObjectMapper objectMapper;
    private final AiConfigService aiConfigService;
    private final AiPromptService aiPromptService;
    private final AiGatewayService aiGatewayService;
    private final WordMarkdownMaterialService wordMarkdownMaterialService;
    private final DictionaryWordService dictionaryWordService;

    public DictionaryWordAiAutofillService(
            ObjectMapper objectMapper,
            AiConfigService aiConfigService,
            AiPromptService aiPromptService,
            AiGatewayService aiGatewayService,
            WordMarkdownMaterialService wordMarkdownMaterialService,
            DictionaryWordService dictionaryWordService) {
        this.objectMapper = objectMapper;
        this.aiConfigService = aiConfigService;
        this.aiPromptService = aiPromptService;
        this.aiGatewayService = aiGatewayService;
        this.wordMarkdownMaterialService = wordMarkdownMaterialService;
        this.dictionaryWordService = dictionaryWordService;
    }

    public GenerateDictionaryWordWithAiResponse autofill(
            Long dictionaryId,
            GenerateDictionaryWordWithAiRequest request) {
        String requestedWord = request.getWord().trim();
        LearningMaterialParseResult materialResult = wordMarkdownMaterialService.loadMaterial(requestedWord);
        GenerateWordDetailsRequest generationRequest = new GenerateWordDetailsRequest(
                request.getConfigId(),
                requestedWord,
                materialResult.getSourceMarkdown()
        );
        AiConfig config = aiConfigService.resolveActiveConfig(request.getConfigId());

        String firstResponse = aiGatewayService.generateText(
                config,
                aiPromptService.buildWordDetailsV2Messages(generationRequest)
        );
        MetaWordEntryDtoV2 entry;
        try {
            entry = parseAndValidate(firstResponse, requestedWord);
        } catch (GeneratedEntryValidationException firstFailure) {
            String repairedResponse = aiGatewayService.generateText(
                    config,
                    aiPromptService.buildWordDetailsV2RepairMessages(
                            generationRequest,
                            firstResponse,
                            firstFailure.getMessage()
                    )
            );
            try {
                entry = parseAndValidate(repairedResponse, requestedWord);
            } catch (GeneratedEntryValidationException secondFailure) {
                log.warn(
                        "AI word autofill validation failed after repair: dictionaryId={}, metaWordId={}, "
                                + "configId={}, provider={}, model={}",
                        dictionaryId,
                        request.getMetaWordId(),
                        config.getId(),
                        config.getProviderName(),
                        config.getModelName(),
                        secondFailure
                );
                throw new BadGatewayException(
                        "AI word details remained invalid after one repair attempt: "
                                + secondFailure.getMessage()
                );
            }
        }

        return dictionaryWordService.saveGeneratedWordV2(
                dictionaryId,
                request.getMetaWordId(),
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                entry,
                materialResult
        );
    }

    private MetaWordEntryDtoV2 parseAndValidate(String rawContent, String requestedWord) {
        String normalized = AiJsonResponseNormalizer.stripMarkdownCodeFence(rawContent);
        if (normalized.isBlank()) {
            throw new GeneratedEntryValidationException("response must contain a JSON object");
        }

        MetaWordEntryDtoV2 entry;
        try {
            entry = objectMapper.readValue(normalized, MetaWordEntryDtoV2.class);
        } catch (JsonProcessingException exception) {
            throw new GeneratedEntryValidationException("response is not valid MetaWordEntryDtoV2 JSON");
        }

        List<String> errors = validate(entry, requestedWord);
        if (!errors.isEmpty()) {
            throw new GeneratedEntryValidationException(String.join("; ", errors));
        }
        return entry;
    }

    private List<String> validate(MetaWordEntryDtoV2 entry, String requestedWord) {
        List<String> errors = new ArrayList<>();
        if (entry == null) {
            errors.add("entry must be an object");
            return errors;
        }

        String generatedWord = trimToNull(entry.getWord());
        if (generatedWord == null) {
            errors.add("word is required");
        } else if (!WordNormalizationUtils.normalize(generatedWord)
                .equals(WordNormalizationUtils.normalize(requestedWord))) {
            errors.add("word must match requested word " + requestedWord);
        }

        if (entry.getDifficulty() == null || entry.getDifficulty() < 1 || entry.getDifficulty() > 5) {
            errors.add("difficulty must be between 1 and 5");
        }

        validatePhonetic(entry, errors);
        validateSyllables(entry, requestedWord, errors);
        validatePartsOfSpeech(entry.getPartOfSpeech(), errors);
        validateLearningDetail(entry.getLearningDetail(), requestedWord, errors);
        return errors;
    }

    private void validatePhonetic(MetaWordEntryDtoV2 entry, List<String> errors) {
        if (entry.getPhonetic() == null) {
            return;
        }
        validateOptionalLength(entry.getPhonetic().getUk(), 100, "phonetic.uk", errors);
        validateOptionalLength(entry.getPhonetic().getUs(), 100, "phonetic.us", errors);
    }

    private void validateSyllables(MetaWordEntryDtoV2 entry, String requestedWord, List<String> errors) {
        if (entry.getSyllableDetail() == null) {
            return;
        }
        List<SyllableSegmentDto> segments = entry.getSyllableDetail().getSegments();
        if (segments == null) {
            errors.add("syllableDetail.segments must be an array");
            return;
        }
        StringBuilder spelling = new StringBuilder();
        for (int index = 0; index < segments.size(); index++) {
            SyllableSegmentDto segment = segments.get(index);
            if (segment == null || trimToNull(segment.getText()) == null) {
                errors.add("syllableDetail.segments[" + index + "].text is required");
                continue;
            }
            spelling.append(segment.getText().trim());
            validateOptionalLength(segment.getText(), MAX_WORD_LENGTH,
                    "syllableDetail.segments[" + index + "].text", errors);
        }
        if (!segments.isEmpty() && !spelling.toString().equalsIgnoreCase(requestedWord)) {
            errors.add("syllableDetail.segments must concatenate to requested word");
        }
    }

    private void validatePartsOfSpeech(List<PartOfSpeechDto> partsOfSpeech, List<String> errors) {
        if (partsOfSpeech == null || partsOfSpeech.isEmpty()) {
            errors.add("partOfSpeech must contain at least one item");
            return;
        }
        for (int partIndex = 0; partIndex < partsOfSpeech.size(); partIndex++) {
            PartOfSpeechDto part = partsOfSpeech.get(partIndex);
            String path = "partOfSpeech[" + partIndex + "]";
            if (part == null) {
                errors.add(path + " must be an object");
                continue;
            }
            String pos = trimToNull(part.getPos());
            if (pos == null || !ALLOWED_PARTS_OF_SPEECH.contains(pos.toLowerCase(Locale.ROOT))) {
                errors.add(path + ".pos must use a supported dictionary abbreviation");
            }
            List<DefinitionDto> definitions = part.getDefinitions();
            if (definitions == null || definitions.isEmpty()) {
                errors.add(path + ".definitions must contain at least one item");
                continue;
            }
            for (int definitionIndex = 0; definitionIndex < definitions.size(); definitionIndex++) {
                validateDefinition(definitions.get(definitionIndex), path, definitionIndex, errors);
            }
        }
    }

    private void validateDefinition(
            DefinitionDto definition,
            String partPath,
            int definitionIndex,
            List<String> errors) {
        String path = partPath + ".definitions[" + definitionIndex + "]";
        if (definition == null) {
            errors.add(path + " must be an object");
            return;
        }
        validateRequiredLength(definition.getDefinition(), 1000, path + ".definition", errors);
        validateOptionalLength(definition.getTranslation(), 500, path + ".translation", errors);
        if (definition.getExampleSentences() == null) {
            return;
        }
        for (int exampleIndex = 0; exampleIndex < definition.getExampleSentences().size(); exampleIndex++) {
            ExampleSentenceDto example = definition.getExampleSentences().get(exampleIndex);
            String examplePath = path + ".exampleSentences[" + exampleIndex + "]";
            if (example == null) {
                errors.add(examplePath + " must be an object");
                continue;
            }
            validateRequiredLength(example.getSentence(), 500, examplePath + ".sentence", errors);
            validateOptionalLength(example.getTranslation(), 500, examplePath + ".translation", errors);
        }
    }

    private void validateLearningDetail(
            LearningDetailDto detail,
            String requestedWord,
            List<String> errors) {
        if (detail == null) {
            errors.add("learningDetail is required");
            return;
        }
        validateOptionalLength(detail.getLearningMaterial(), MAX_LEARNING_MATERIAL_LENGTH,
                "learningDetail.learningMaterial", errors);
        validateRequiredLength(detail.getMemoryHint(), MAX_MEMORY_HINT_LENGTH,
                "learningDetail.memoryHint", errors, true);
        validateSamePatternWords(detail.getSamePatternWords(), errors);
        validateExamPhrases(detail.getExamPhrases(), errors);
        validateWordFamily(detail.getWordFamily(), requestedWord, errors);
        validateConfusableWords(detail.getConfusableWords(), errors);
    }

    private void validateSamePatternWords(List<SamePatternWordDto> items, List<String> errors) {
        if (items == null) {
            errors.add("learningDetail.samePatternWords must be an array");
            return;
        }
        for (int index = 0; index < items.size(); index++) {
            SamePatternWordDto item = items.get(index);
            String path = "learningDetail.samePatternWords[" + index + "]";
            if (item == null) {
                errors.add(path + " must be an object");
                continue;
            }
            validateRequiredLength(item.getWord(), MAX_SHORT_TEXT_LENGTH, path + ".word", errors);
            validateRequiredLength(item.getTranslation(), MAX_SHORT_TEXT_LENGTH, path + ".translation", errors);
            validateRequiredLength(item.getRootBreakdown(), MAX_ROOT_BREAKDOWN_LENGTH,
                    path + ".rootBreakdown", errors);
        }
    }

    private void validateExamPhrases(List<String> phrases, List<String> errors) {
        if (phrases == null) {
            errors.add("learningDetail.examPhrases must be an array");
            return;
        }
        if (phrases.size() > MAX_EXAM_PHRASES) {
            errors.add("learningDetail.examPhrases must contain at most " + MAX_EXAM_PHRASES + " items");
        }
        for (int index = 0; index < phrases.size(); index++) {
            validateRequiredLength(phrases.get(index), MAX_SHORT_TEXT_LENGTH,
                    "learningDetail.examPhrases[" + index + "]", errors);
        }
    }

    private void validateWordFamily(
            List<WordFamilyItemDto> items,
            String requestedWord,
            List<String> errors) {
        if (items == null) {
            errors.add("learningDetail.wordFamily must be an array");
            return;
        }
        if (items.size() > MAX_WORD_FAMILY) {
            errors.add("learningDetail.wordFamily must contain at most " + MAX_WORD_FAMILY + " items");
        }
        for (int index = 0; index < items.size(); index++) {
            WordFamilyItemDto item = items.get(index);
            String path = "learningDetail.wordFamily[" + index + "]";
            if (item == null) {
                errors.add(path + " must be an object");
                continue;
            }
            validateRequiredLength(item.getWord(), MAX_SHORT_TEXT_LENGTH, path + ".word", errors);
            validateRequiredLength(item.getTranslation(), MAX_SHORT_TEXT_LENGTH, path + ".translation", errors);
            String pos = trimToNull(item.getPos());
            if (pos == null || !ALLOWED_PARTS_OF_SPEECH.contains(pos.toLowerCase(Locale.ROOT))) {
                errors.add(path + ".pos must use a supported dictionary abbreviation");
            }
            if (WordNormalizationUtils.normalize(requestedWord)
                    .equals(WordNormalizationUtils.normalize(item.getWord()))) {
                errors.add(path + ".word must not equal the requested word");
            }
        }
    }

    private void validateConfusableWords(List<ConfusableWordDto> items, List<String> errors) {
        if (items == null) {
            errors.add("learningDetail.confusableWords must be an array");
            return;
        }
        if (items.size() > MAX_CONFUSABLE_WORDS) {
            errors.add("learningDetail.confusableWords must contain at most " + MAX_CONFUSABLE_WORDS + " items");
        }
        for (int index = 0; index < items.size(); index++) {
            ConfusableWordDto item = items.get(index);
            String path = "learningDetail.confusableWords[" + index + "]";
            if (item == null) {
                errors.add(path + " must be an object");
                continue;
            }
            validateRequiredLength(item.getWord(), MAX_SHORT_TEXT_LENGTH, path + ".word", errors);
            validateRequiredLength(item.getDistinction(), MAX_SHORT_TEXT_LENGTH, path + ".distinction", errors);
        }
    }

    private void validateRequiredLength(String value, int maxLength, String path, List<String> errors) {
        validateRequiredLength(value, maxLength, path, errors, false);
    }

    private void validateRequiredLength(
            String value,
            int maxLength,
            String path,
            List<String> errors,
            boolean allowEmpty) {
        if (value == null || (!allowEmpty && value.trim().isEmpty())) {
            errors.add(path + (allowEmpty ? " must be a string" : " is required"));
        } else if (value.length() > maxLength) {
            errors.add(path + " must not exceed " + maxLength + " characters");
        }
    }

    private void validateOptionalLength(String value, int maxLength, String path, List<String> errors) {
        if (value != null && value.length() > maxLength) {
            errors.add(path + " must not exceed " + maxLength + " characters");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static final class GeneratedEntryValidationException extends RuntimeException {

        private GeneratedEntryValidationException(String message) {
            super(message);
        }
    }
}
