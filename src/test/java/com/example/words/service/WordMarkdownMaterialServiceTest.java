package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void loadMaterialShouldExtractAndNormalizeCleanFullWidthBracketedMaterial() throws IOException {
        String sourceMarkdown = """
                ---
                word: ability
                ---
                # ability

                ## 内容

                2. ability n. 能力【  词根词缀：

                -able + -ity（构成抽象名词）


                】
                """;
        Files.writeString(materialDirectory.resolve("ability.md"), sourceMarkdown);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.FOUND, result.getStatus());
        assertEquals("词根词缀：\n\n-able + -ity（构成抽象名词）", result.getLearningMaterial());
        assertEquals("ability.md", result.getSourcePath());
        assertEquals(8, result.getStartLine());
        assertEquals(13, result.getEndLine());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void loadMaterialShouldWarnWhenMultiLineContentHasNoMaterialBrackets() throws IOException {
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability n. 能力
                这是一段未经标记的教材内容。
                """);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.PARSE_WARNING, result.getStatus());
        assertEquals("ability.md", result.getSourcePath());
        assertEquals("MISSING_BRACKETS", result.getWarnings().get(0).getCode());
        assertEquals("Missing full-width material brackets", result.getWarnings().get(0).getMessage());
    }

    @Test
    void loadMaterialShouldRejectNestedBrackets() throws IOException {
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability【外层【内层】】尾随文字
                """);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.PARSE_WARNING, result.getStatus());
        assertEquals("NESTED_BRACKETS", result.getWarnings().get(0).getCode());
    }

    @Test
    void loadMaterialShouldReturnNotFoundForOneLineContentWithoutBrackets() throws IOException {
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability n. 能力

                ## 扩展

                扩展说明【不能作为教材素材】
                """);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.NOT_FOUND, result.getStatus());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void loadMaterialShouldPreserveLongStoriesAndInternalNonFullWidthBrackets() throws IOException {
        String longStory = "这是一个很长的故事。".repeat(100);
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability【%s（保留这对括号）】
                """.formatted(longStory));
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.FOUND, result.getStatus());
        assertEquals(longStory + "（保留这对括号）", result.getLearningMaterial());
    }

    @Test
    void loadMaterialShouldWarnForUnbalancedBrackets() throws IOException {
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability【教材内容
                """);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.PARSE_WARNING, result.getStatus());
        assertEquals("UNBALANCED_BRACKETS", result.getWarnings().get(0).getCode());
    }

    @Test
    void loadMaterialShouldWarnForWatermarks() throws IOException {
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability【教材内容，请访问 https://example.com】
                """);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.PARSE_WARNING, result.getStatus());
        assertEquals("WATERMARK", result.getWarnings().get(0).getCode());
    }

    @Test
    void loadMaterialShouldWarnForTrailingText() throws IOException {
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability【教材内容】尾随文字
                """);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.PARSE_WARNING, result.getStatus());
        assertEquals("TRAILING_TEXT", result.getWarnings().get(0).getCode());
    }

    @Test
    void loadMaterialShouldWarnWhenASecondContentSectionExists() throws IOException {
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability【材料】

                ## 内容

                another【材料】
                """);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.PARSE_WARNING, result.getStatus());
        assertEquals("MULTIPLE_CONTENT_SECTIONS", result.getWarnings().get(0).getCode());
    }

    @Test
    void loadMaterialShouldWarnWhenChineseTextRunsIntoAnotherWord() throws IOException {
        Files.writeString(materialDirectory.resolve("ability.md"), """
                ---
                word: ability
                ---
                ## 内容

                ability【knowability 可知性changeability 可变性】
                """);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.PARSE_WARNING, result.getStatus());
        assertEquals("SUSPECTED_CONCATENATED_WORD", result.getWarnings().get(0).getCode());
    }

    @Test
    void loadMaterialShouldReturnReadErrorWhenAnIndexedFileBecomesUnreadable() throws IOException {
        Path sourceFile = materialDirectory.resolve("ability.md");
        Files.writeString(sourceFile, "---\nword: ability\n---\n## 内容\n\nability【材料】");
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());
        assertEquals(LearningMaterialStatus.FOUND, service.loadMaterial("ability").getStatus());
        Files.delete(sourceFile);

        LearningMaterialParseResult result = service.loadMaterial("ability");

        assertEquals(LearningMaterialStatus.READ_ERROR, result.getStatus());
        assertEquals("READ_ERROR", result.getWarnings().get(0).getCode());
    }

    @Test
    void loadMaterialShouldReadCompleteMarkdownFromGeneratedFileName() throws IOException {
        String sourceMarkdown = """
                ---
                word: be able to
                ---
                # be able to

                ## 内容

                能够""";
        Files.writeString(materialDirectory.resolve("be-able-to.md"), sourceMarkdown);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        assertEquals(sourceMarkdown, service.loadMaterial(" Be Able To ").getSourceMarkdown());
    }

    @Test
    void loadMaterialShouldUseFrontMatterWhenGeneratedFileNameHasSuffix() throws IOException {
        String sourceMarkdown = """
                ---
                word: ability
                ---
                # ability

                ## 内容

                能力""";
        Files.writeString(materialDirectory.resolve("ability-2.md"), sourceMarkdown);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        assertEquals(sourceMarkdown, service.loadMaterial("ability").getSourceMarkdown());
    }

    @Test
    void loadMaterialShouldUseNormalizedFrontMatterAsTheLookupContract() throws IOException {
        String sourceMarkdown = """
                ---
                word: be able to
                ---
                # be able to

                ## 内容

                能够""";
        Files.writeString(materialDirectory.resolve("source-entry-17.md"), sourceMarkdown);
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        assertEquals(sourceMarkdown, service.loadMaterial("  BE   ABLE TO  ").getSourceMarkdown());
    }

    @Test
    void loadMaterialShouldReturnNotFoundWhenNoMatchingMarkdownExists() {
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(materialDirectory.toString());

        assertEquals(LearningMaterialStatus.NOT_FOUND, service.loadMaterial("not-in-material").getStatus());
        assertTrue(Files.isDirectory(materialDirectory));
    }

    @Test
    void loadMaterialShouldReadRepresentativeRegeneratedCorpusEntries() {
        Path generatedMaterials = Path.of("generated", "gaokao-3500-words-markdown");
        WordMarkdownMaterialService service = new WordMarkdownMaterialService(generatedMaterials.toString());

        assertTrue(service.loadMaterial("weak").getSourceMarkdown().contains("50.  weak adj"));
        assertTrue(service.loadMaterial("world").getSourceMarkdown().contains("9.\tworld n"));
        assertTrue(service.loadMaterial("are").getSourceMarkdown().contains("40.  are v"));
    }
}
