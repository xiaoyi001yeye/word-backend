package com.example.words.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.words.util.WordNormalizationUtils;

/** Loads the Markdown entry generated from the Gaokao vocabulary teaching materials. */
@Service
@Slf4j
public class WordMarkdownMaterialService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Path materialDirectory;
    private volatile Map<String, Path> indexedFiles;

    public WordMarkdownMaterialService(
            @Value("${word.learning-material.markdown-directory:generated/gaokao-3500-words-markdown}")
            String materialDirectory) {
        this.materialDirectory = Path.of(materialDirectory);
    }

    /**
     * Loads an entry and extracts the single complete full-width bracketed teaching material
     * from its content section. The raw Markdown remains separate from the extracted material.
     */
    public LearningMaterialParseResult loadMaterial(String word) {
        String normalizedWord = normalizeMaterialWord(word);
        if (normalizedWord == null || normalizedWord.isBlank() || !Files.isDirectory(materialDirectory)) {
            return new LearningMaterialParseResult(
                    LearningMaterialStatus.NOT_FOUND, null, null, null, null, null, List.of());
        }

        Path materialFile = materialIndex().get(normalizedWord);
        if (materialFile == null) {
            return new LearningMaterialParseResult(
                    LearningMaterialStatus.NOT_FOUND, null, null, null, null, null, List.of());
        }

        try {
            String sourceMarkdown = Files.readString(materialFile, StandardCharsets.UTF_8);
            return extractMaterial(sourceMarkdown, materialFile.getFileName().toString());
        } catch (IOException exception) {
            log.warn("Unable to read Markdown learning material for word {} from {}", word, materialFile, exception);
            return new LearningMaterialParseResult(
                    LearningMaterialStatus.READ_ERROR,
                    null,
                    null,
                    materialFile.getFileName().toString(),
                    null,
                    null,
                    List.of(new LearningMaterialWarning("READ_ERROR", "Unable to read source Markdown", null, null))
            );
        }
    }

    private LearningMaterialParseResult extractMaterial(String sourceMarkdown, String sourcePath) {
        String[] lines = sourceMarkdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int contentLine = findContentLine(lines);
        if (contentLine < 0) {
            return parseWarning(sourceMarkdown, sourcePath, "MISSING_CONTENT_SECTION", "Missing content section", null, null);
        }
        if (countContentSections(lines) > 1) {
            return parseWarning(
                    sourceMarkdown,
                    sourcePath,
                    "MULTIPLE_CONTENT_SECTIONS",
                    "Multiple content sections",
                    null,
                    null
            );
        }
        int contentSectionEnd = findContentSectionEnd(lines, contentLine + 1);

        int startLine = -1;
        int endLine = -1;
        int startColumn = -1;
        int endColumn = -1;
        int depth = 0;
        for (int lineIndex = contentLine + 1; lineIndex < contentSectionEnd; lineIndex++) {
            String line = lines[lineIndex];
            for (int columnIndex = 0; columnIndex < line.length(); columnIndex++) {
                char character = line.charAt(columnIndex);
                if (character == '【') {
                    if (depth == 0) {
                        startLine = lineIndex;
                        startColumn = columnIndex;
                    } else {
                        return parseWarning(
                                sourceMarkdown,
                                sourcePath,
                                "NESTED_BRACKETS",
                                "Nested full-width brackets are ambiguous",
                                startLine + 1,
                                lineIndex + 1
                        );
                    }
                    depth++;
                } else if (character == '】') {
                    if (depth == 0) {
                        return parseWarning(
                                sourceMarkdown,
                                sourcePath,
                                "UNBALANCED_BRACKETS",
                                "Unbalanced closing bracket",
                                lineIndex + 1,
                                lineIndex + 1
                        );
                    }
                    depth--;
                    if (depth == 0) {
                        endLine = lineIndex;
                        endColumn = columnIndex;
                        break;
                    }
                }
            }
            if (endLine >= 0) {
                break;
            }
        }

        if (startLine < 0) {
            if (countNonBlankContentLines(lines, contentLine + 1, contentSectionEnd) > 1) {
                return parseWarning(
                        sourceMarkdown,
                        sourcePath,
                        "MISSING_BRACKETS",
                        "Missing full-width material brackets",
                        null,
                        null
                );
            }
            return new LearningMaterialParseResult(
                    LearningMaterialStatus.NOT_FOUND, sourceMarkdown, null, sourcePath, null, null, List.of());
        }
        if (depth != 0 || endLine < 0) {
            return parseWarning(
                    sourceMarkdown,
                    sourcePath,
                    "UNBALANCED_BRACKETS",
                    "Unbalanced opening bracket",
                    startLine + 1,
                    null
            );
        }

        if (hasTrailingText(lines, endLine, endColumn, contentSectionEnd)) {
            return parseWarning(
                    sourceMarkdown,
                    sourcePath,
                    "TRAILING_TEXT",
                    "Text follows the closing material bracket",
                    endLine + 1,
                    null
            );
        }

        String material = extractBracketContents(lines, startLine, startColumn, endLine, endColumn);
        if (containsWatermark(material)) {
            return parseWarning(
                    sourceMarkdown,
                    sourcePath,
                    "WATERMARK",
                    "Material contains a watermark or URL",
                    startLine + 1,
                    endLine + 1
            );
        }
        if (containsSuspectedConcatenatedWord(material)) {
            return parseWarning(
                    sourceMarkdown,
                    sourcePath,
                    "SUSPECTED_CONCATENATED_WORD",
                    "Material contains a suspected concatenated word",
                    startLine + 1,
                    endLine + 1
            );
        }
        return new LearningMaterialParseResult(
                LearningMaterialStatus.FOUND,
                sourceMarkdown,
                normalizeLearningMaterial(material),
                sourcePath,
                startLine + 1,
                endLine + 1,
                List.of()
        );
    }

    private int findContentLine(String[] lines) {
        for (int index = 0; index < lines.length; index++) {
            if ("## 内容".equals(lines[index].trim())) {
                return index;
            }
        }
        return -1;
    }

    private int countContentSections(String[] lines) {
        int count = 0;
        for (String line : lines) {
            if ("## 内容".equals(line.trim())) {
                count++;
            }
        }
        return count;
    }

    private int findContentSectionEnd(String[] lines, int firstContentLine) {
        for (int lineIndex = firstContentLine; lineIndex < lines.length; lineIndex++) {
            if (lines[lineIndex].trim().matches("^#{1,2}\\s+.+")) {
                return lineIndex;
            }
        }
        return lines.length;
    }

    private int countNonBlankContentLines(String[] lines, int firstContentLine, int contentSectionEnd) {
        int count = 0;
        for (int lineIndex = firstContentLine; lineIndex < contentSectionEnd; lineIndex++) {
            if (!lines[lineIndex].trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private boolean hasTrailingText(String[] lines, int endLine, int endColumn, int contentSectionEnd) {
        if (!lines[endLine].substring(endColumn + 1).trim().isEmpty()) {
            return true;
        }
        for (int lineIndex = endLine + 1; lineIndex < contentSectionEnd; lineIndex++) {
            if (!lines[lineIndex].trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWatermark(String material) {
        String lowerCaseMaterial = material.toLowerCase(java.util.Locale.ROOT);
        return material.contains("水印") || lowerCaseMaterial.contains("http://")
                || lowerCaseMaterial.contains("https://") || lowerCaseMaterial.contains("www.");
    }

    private boolean containsSuspectedConcatenatedWord(String material) {
        return material.matches("(?s).*\\p{IsHan}[A-Za-z].*");
    }

    private String extractBracketContents(
            String[] lines,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn) {
        if (startLine == endLine) {
            return lines[startLine].substring(startColumn + 1, endColumn);
        }

        StringBuilder result = new StringBuilder(lines[startLine].substring(startColumn + 1));
        for (int lineIndex = startLine + 1; lineIndex < endLine; lineIndex++) {
            result.append('\n').append(lines[lineIndex]);
        }
        return result.append('\n').append(lines[endLine], 0, endColumn).toString();
    }

    private String normalizeLearningMaterial(String material) {
        StringBuilder result = new StringBuilder();
        boolean previousLineBlank = false;
        for (String line : material.split("\n", -1)) {
            String normalizedLine = line.trim();
            if (normalizedLine.isEmpty()) {
                if (!result.isEmpty() && !previousLineBlank) {
                    result.append("\n\n");
                }
                previousLineBlank = true;
            } else {
                if (!result.isEmpty() && !previousLineBlank) {
                    result.append('\n');
                }
                result.append(normalizedLine);
                previousLineBlank = false;
            }
        }
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '\n') {
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    private LearningMaterialParseResult parseWarning(
            String sourceMarkdown,
            String sourcePath,
            String warningCode,
            String warning,
            Integer startLine,
            Integer endLine) {
        return new LearningMaterialParseResult(
                LearningMaterialStatus.PARSE_WARNING,
                sourceMarkdown,
                null,
                sourcePath,
                startLine,
                endLine,
                List.of(new LearningMaterialWarning(warningCode, warning, startLine, endLine))
        );
    }

    private Map<String, Path> materialIndex() {
        Map<String, Path> currentIndex = indexedFiles;
        if (currentIndex != null) {
            return currentIndex;
        }

        synchronized (this) {
            if (indexedFiles == null) {
                indexedFiles = buildMaterialIndex();
            }
            return indexedFiles;
        }
    }

    private Map<String, Path> buildMaterialIndex() {
        Map<String, Path> result = new HashMap<>();
        try (Stream<Path> files = Files.list(materialDirectory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .forEach(path -> indexMaterialFile(result, path));
        } catch (IOException exception) {
            log.warn("Unable to index Markdown learning materials in {}", materialDirectory, exception);
        }
        return Map.copyOf(result);
    }

    private void indexMaterialFile(Map<String, Path> index, Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String frontMatterWord = content.lines()
                    .limit(20)
                    .map(String::trim)
                    .filter(line -> line.startsWith("word:"))
                    .map(line -> line.substring("word:".length()).trim())
                    .findFirst()
                    .orElse(null);
            String normalizedWord = normalizeMaterialWord(frontMatterWord);
            if (normalizedWord != null && !normalizedWord.isBlank()) {
                index.putIfAbsent(normalizedWord, path);
            }
        } catch (IOException exception) {
            log.warn("Unable to index Markdown learning material {}", path, exception);
        }
    }

    private String normalizeMaterialWord(String word) {
        String normalizedWord = WordNormalizationUtils.normalize(word);
        return normalizedWord == null ? null : WHITESPACE.matcher(normalizedWord).replaceAll(" ");
    }
}
