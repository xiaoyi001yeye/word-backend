package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordMarkdownMaterialServiceTest {

    @TempDir
    Path materialDirectory;

    @Test
    void findMaterialShouldReadCompleteMarkdownFromGeneratedFileName() throws IOException {
        String sourceMarkdown = """
                ---
                word: be able to
                ---
                # be able to

                ## 内容

                能够""";
        Files.writeString(materialDirectory.resolve("be-able-to.md"), sourceMarkdown);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        assertEquals(sourceMarkdown, service.findMaterial(" Be Able To ").orElseThrow());
    }

    @Test
    void findMaterialShouldUseFrontMatterWhenGeneratedFileNameHasSuffix() throws IOException {
        String sourceMarkdown = """
                ---
                word: ability
                ---
                # ability

                ## 内容

                能力""";
        Files.writeString(materialDirectory.resolve("ability-2.md"), sourceMarkdown);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        assertEquals(sourceMarkdown, service.findMaterial("ability").orElseThrow());
    }

    @Test
    void findMaterialShouldReturnEmptyWhenNoMatchingMarkdownExists() {
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        assertFalse(service.findMaterial("not-in-material").isPresent());
        assertTrue(Files.isDirectory(materialDirectory));
    }
}
