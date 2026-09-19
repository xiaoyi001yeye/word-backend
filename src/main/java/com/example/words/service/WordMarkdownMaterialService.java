package com.example.words.service;

import com.example.words.util.WordNormalizationUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
     * Returns the complete source Markdown so the AI receives all teaching material as context.
     * The normalized {@code word} front matter is the lookup contract; generated filenames are
     * deliberately not part of the interface between the converter and this module.
     */
    public Optional<String> findMaterial(String word) {
        String normalizedWord = normalizeMaterialWord(word);
        if (normalizedWord == null || normalizedWord.isBlank() || !Files.isDirectory(materialDirectory)) {
            return Optional.empty();
        }

        Path indexedFile = materialIndex().get(normalizedWord);
        return indexedFile == null ? Optional.empty() : readMaterial(indexedFile, word);
    }

    private Optional<String> readMaterial(Path materialFile, String word) {
        try {
            return Optional.of(Files.readString(materialFile, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            log.warn("Unable to read Markdown learning material for word {} from {}", word, materialFile, exception);
            return Optional.empty();
        }
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
