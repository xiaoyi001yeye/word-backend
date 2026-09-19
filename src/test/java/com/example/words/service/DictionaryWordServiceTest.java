package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.words.dto.MetaWordSuggestionDto;
import com.example.words.dto.MetaWordEntryDto;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.dto.LearningDetailDto;
import com.example.words.dto.SamePatternWordDto;
import com.example.words.dto.WordFamilyItemDto;
import com.example.words.dto.ConfusableWordDto;
import com.example.words.dto.SyllableDetailDto;
import com.example.words.dto.SyllableSegmentDto;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ConflictException;
import com.example.words.model.MetaWord;
import com.example.words.model.LearningDetail;
import com.example.words.model.SamePatternWord;
import com.example.words.model.WordFamilyItem;
import com.example.words.model.ConfusableWord;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.TagRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DictionaryWordServiceTest {

    @Mock
    private DictionaryWordRepository dictionaryWordRepository;

    @Mock
    private MetaWordRepository metaWordRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagService tagService;

    private DictionaryWordService dictionaryWordService;
    private RecordingDictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        dictionaryService = new RecordingDictionaryService();
        dictionaryWordService = new DictionaryWordService(
                dictionaryWordRepository,
                metaWordRepository,
                tagRepository,
                tagService,
                dictionaryService
        );
    }

    @Test
    void processWordListShouldIncrementDictionaryWordCountForNewAssociations() {
        AtomicLong idGenerator = new AtomicLong(100L);
        when(metaWordRepository.findByWord("apple")).thenReturn(Optional.empty());
        when(metaWordRepository.findByWord("banana")).thenReturn(Optional.empty());
        when(metaWordRepository.save(any(MetaWord.class))).thenAnswer(invocation -> {
            MetaWord metaWord = invocation.getArgument(0);
            metaWord.setId(idGenerator.incrementAndGet());
            return metaWord;
        });
        when(tagService.getOrCreateDefaultChapterTagId(10L)).thenReturn(99L);
        when(dictionaryWordRepository.findMaxEntryOrderByDictionaryIdAndChapterTagId(10L, 99L)).thenReturn(0);
        when(dictionaryWordRepository.countDistinctMetaWordIdByDictionaryId(10L)).thenReturn(2L);
        when(dictionaryWordRepository.countByDictionaryId(10L)).thenReturn(2L);

        DictionaryWordService.WordListProcessResult result = dictionaryWordService.processWordList(
                10L,
                List.of(
                        new MetaWordEntryDto("apple", null, null, null, null, "苹果", 2),
                        new MetaWordEntryDto("banana", null, null, null, null, "香蕉", 1)
                )
        );

        assertEquals(2, result.getCreated());
        assertEquals(2, result.getAdded());
        verify(dictionaryWordRepository).saveAll(anyList());
        assertEquals(10L, dictionaryService.lastUpdatedDictionaryId);
        assertEquals(2, dictionaryService.lastUpdatedWordCount);
        assertEquals(2, dictionaryService.lastUpdatedEntryCount);
    }

    @Test
    void processWordListShouldUpdateExistingMetaWordFields() {
        MetaWord existingMetaWord = new MetaWord();
        existingMetaWord.setId(101L);
        existingMetaWord.setWord("apple");
        existingMetaWord.setDifficulty(1);

        when(metaWordRepository.findByNormalizedWord("apple")).thenReturn(Optional.of(existingMetaWord));
        when(tagService.getOrCreateDefaultChapterTagId(10L)).thenReturn(99L);
        when(dictionaryWordRepository.findMaxEntryOrderByDictionaryIdAndChapterTagId(10L, 99L)).thenReturn(0);
        when(metaWordRepository.save(any(MetaWord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dictionaryWordRepository.countDistinctMetaWordIdByDictionaryId(10L)).thenReturn(1L);
        when(dictionaryWordRepository.countByDictionaryId(10L)).thenReturn(1L);

        DictionaryWordService.WordListProcessResult result = dictionaryWordService.processWordList(
                10L,
                List.of(new MetaWordEntryDto(
                        "apple",
                        "/ˈæp.əl/",
                        "a round fruit",
                        "noun",
                        "She picked an apple.",
                        "苹果",
                        3
                ))
        );

        assertEquals(1, result.getExisted());
        assertEquals(0, result.getCreated());
        assertEquals("/ˈæp.əl/", existingMetaWord.getPhonetic());
        assertEquals("a round fruit", existingMetaWord.getDefinition());
        assertEquals("noun", existingMetaWord.getPartOfSpeech());
        assertEquals("She picked an apple.", existingMetaWord.getExampleSentence());
        assertEquals("苹果", existingMetaWord.getTranslation());
        assertEquals(3, existingMetaWord.getDifficulty());
        verify(metaWordRepository).save(existingMetaWord);
    }

    @Test
    void processWordListV2ShouldPersistSyllableMetadata() {
        MetaWord existingMetaWord = new MetaWord();
        existingMetaWord.setId(102L);
        existingMetaWord.setWord("resilient");

        MetaWordEntryDtoV2 entry = new MetaWordEntryDtoV2();
        entry.setWord("resilient");
        entry.setSyllableDetail(new SyllableDetailDto(List.of(
                new SyllableSegmentDto("re", "/rɪ/", "/rɪ/", null, null),
                new SyllableSegmentDto("sil", "/ˈzɪl/", "/ˈzɪl/", null, null),
                new SyllableSegmentDto("ient", "/iənt/", "/iənt/", null, null)
        )));
        entry.setLearningDetail(new LearningDetailDto(
                "可信导入教材",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        when(metaWordRepository.findByNormalizedWord("resilient")).thenReturn(Optional.of(existingMetaWord));
        when(tagService.getOrCreateDefaultChapterTagId(10L)).thenReturn(99L);
        when(dictionaryWordRepository.findMaxEntryOrderByDictionaryIdAndChapterTagId(10L, 99L)).thenReturn(0);
        when(metaWordRepository.save(any(MetaWord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dictionaryWordRepository.countDistinctMetaWordIdByDictionaryId(10L)).thenReturn(1L);
        when(dictionaryWordRepository.countByDictionaryId(10L)).thenReturn(1L);

        DictionaryWordService.WordListProcessResult result = dictionaryWordService.processWordListV2(
                10L,
                List.of(entry)
        );

        assertEquals(1, result.getExisted());
        assertEquals(3, existingMetaWord.getSyllableDetail().getSegments().size());
        assertEquals("re", existingMetaWord.getSyllableDetail().getSegments().get(0).getText());
        assertEquals("可信导入教材", existingMetaWord.getLearningDetail().getLearningMaterial());
        verify(metaWordRepository).save(existingMetaWord);
    }

    @Test
    void saveGeneratedWordV2ShouldPersistSourceMarkdownAndAiLearningExtensions() {
        MetaWord existingMetaWord = new MetaWord();
        existingMetaWord.setId(103L);
        existingMetaWord.setWord("ability");

        MetaWordEntryDtoV2 entry = new MetaWordEntryDtoV2();
        entry.setWord("ability");
        entry.setLearningDetail(new LearningDetailDto(
                "model changed material",
                "able + ity，能力由可做到积累而来",
                List.of(new SamePatternWordDto("adaptability", "适应能力", "adapt + ability")),
                List.of("have the ability to do sth"),
                List.of(new WordFamilyItemDto("able", "adj.", "有能力的")),
                List.of(new ConfusableWordDto("capacity", "capacity 偏容量或潜能"))
        ));

        when(metaWordRepository.findById(103L)).thenReturn(Optional.of(existingMetaWord));
        when(metaWordRepository.save(any(MetaWord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String sourceMarkdown = """
                ---
                word: ability
                ---
                # ability
                教材原文""";

        dictionaryWordService.saveGeneratedWordV2(
                10L,
                103L,
                1L,
                "OpenAI",
                "test-model",
                entry,
                sourceMarkdown
        );

        assertEquals(sourceMarkdown, existingMetaWord.getLearningDetail().getLearningMaterial());
        assertEquals("able + ity，能力由可做到积累而来", existingMetaWord.getLearningDetail().getMemoryHint());
        assertEquals("adaptability", existingMetaWord.getLearningDetail().getSamePatternWords().get(0).getWord());
        assertEquals("adapt + ability", existingMetaWord.getLearningDetail().getSamePatternWords().get(0).getRootBreakdown());
        assertEquals("have the ability to do sth", existingMetaWord.getLearningDetail().getExamPhrases().get(0));
        assertEquals("able", existingMetaWord.getLearningDetail().getWordFamily().get(0).getWord());
        assertEquals("capacity", existingMetaWord.getLearningDetail().getConfusableWords().get(0).getWord());
    }

    @Test
    void saveGeneratedWordV2ShouldPreserveAuthoritativeFieldsAndReplaceAiExtensionsTogether() {
        MetaWord existingMetaWord = new MetaWord();
        existingMetaWord.setId(104L);
        existingMetaWord.setWord("Ability");
        existingMetaWord.setPhonetic("authoritative phonetic");
        existingMetaWord.setPartOfSpeech("authoritative pos");
        existingMetaWord.setDefinition("authoritative definition");
        existingMetaWord.setTranslation("权威释义");
        existingMetaWord.setExampleSentence("Authoritative example.");
        existingMetaWord.setDifficulty(5);
        existingMetaWord.setLearningDetail(new LearningDetail(
                "权威教材材料",
                "旧提示",
                List.of(new SamePatternWord("old-pattern", "旧", "旧拆解")),
                List.of("old phrase"),
                List.of(new WordFamilyItem("old-family", "n.", "旧词族")),
                List.of(new ConfusableWord("old-confusable", "旧辨析"))
        ));

        MetaWordEntryDtoV2 generatedEntry = generatedEntry("ability");
        generatedEntry.setDifficulty(2);
        generatedEntry.setLearningDetail(new LearningDetailDto(
                "AI 试图覆盖教材",
                "新提示",
                List.of(new SamePatternWordDto("new-pattern", "新", "新拆解")),
                List.of("new phrase"),
                List.of(new WordFamilyItemDto("new-family", "adj.", "新词族")),
                List.of(new ConfusableWordDto("new-confusable", "新辨析"))
        ));

        when(metaWordRepository.findById(104L)).thenReturn(Optional.of(existingMetaWord));
        when(metaWordRepository.save(any(MetaWord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(10L, 104L)).thenReturn(true);

        dictionaryWordService.saveGeneratedWordV2(
                10L,
                104L,
                1L,
                "OpenAI",
                "test-model",
                generatedEntry,
                "新的来源教材"
        );

        assertEquals("Ability", existingMetaWord.getWord());
        assertEquals("authoritative phonetic", existingMetaWord.getPhonetic());
        assertEquals("authoritative pos", existingMetaWord.getPartOfSpeech());
        assertEquals("authoritative definition", existingMetaWord.getDefinition());
        assertEquals("权威释义", existingMetaWord.getTranslation());
        assertEquals("Authoritative example.", existingMetaWord.getExampleSentence());
        assertEquals(5, existingMetaWord.getDifficulty());
        assertEquals("权威教材材料", existingMetaWord.getLearningDetail().getLearningMaterial());
        assertEquals("新提示", existingMetaWord.getLearningDetail().getMemoryHint());
        assertEquals("new-pattern", existingMetaWord.getLearningDetail().getSamePatternWords().get(0).getWord());
        assertEquals("new phrase", existingMetaWord.getLearningDetail().getExamPhrases().get(0));
        assertEquals("new-family", existingMetaWord.getLearningDetail().getWordFamily().get(0).getWord());
        assertEquals("new-confusable", existingMetaWord.getLearningDetail().getConfusableWords().get(0).getWord());
    }

    @Test
    void saveGeneratedWordV2ShouldRejectMetaWordIdForAnotherWordWithoutWriting() {
        MetaWord existingMetaWord = new MetaWord();
        existingMetaWord.setId(105L);
        existingMetaWord.setWord("capacity");

        when(metaWordRepository.findById(105L)).thenReturn(Optional.of(existingMetaWord));

        BadRequestException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BadRequestException.class,
                () -> dictionaryWordService.saveGeneratedWordV2(
                        10L,
                        105L,
                        1L,
                        "OpenAI",
                        "test-model",
                        generatedEntry("ability"),
                        "教材材料"
                )
        );

        assertEquals("metaWordId does not match the requested word", exception.getMessage());
        org.mockito.Mockito.verify(metaWordRepository, org.mockito.Mockito.never()).save(any(MetaWord.class));
        verifyNoInteractions(dictionaryWordRepository);
    }

    @Test
    void saveGeneratedWordV2ShouldRejectAStaleGenerationWithoutWriting() {
        MetaWord existingMetaWord = new MetaWord();
        existingMetaWord.setId(106L);
        existingMetaWord.setVersion(3L);
        existingMetaWord.setWord("ability");

        when(metaWordRepository.findById(106L)).thenReturn(Optional.of(existingMetaWord));

        ConflictException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ConflictException.class,
                () -> dictionaryWordService.saveGeneratedWordV2(
                        10L,
                        106L,
                        1L,
                        "OpenAI",
                        "test-model",
                        generatedEntry("ability"),
                        "教材材料",
                        2L
                )
        );

        assertEquals("The word entry changed while AI generation was running; refresh and try again",
                exception.getMessage());
        org.mockito.Mockito.verify(metaWordRepository, org.mockito.Mockito.never()).save(any(MetaWord.class));
        verifyNoInteractions(dictionaryWordRepository);
    }

    @Test
    void saveGeneratedWordV2ShouldFillOnlyMissingPhoneticSubfield() {
        MetaWord existingMetaWord = new MetaWord();
        existingMetaWord.setId(106L);
        existingMetaWord.setWord("ability");
        existingMetaWord.setPhonetic("authoritative flat phonetic");
        existingMetaWord.setPhoneticDetail(new com.example.words.model.Phonetic("authoritative uk", null));

        when(metaWordRepository.findById(106L)).thenReturn(Optional.of(existingMetaWord));
        when(metaWordRepository.save(any(MetaWord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(10L, 106L)).thenReturn(true);

        dictionaryWordService.saveGeneratedWordV2(
                10L,
                106L,
                1L,
                "OpenAI",
                "test-model",
                generatedEntry("ability"),
                "教材材料"
        );

        assertEquals("authoritative flat phonetic", existingMetaWord.getPhonetic());
        assertEquals("authoritative uk", existingMetaWord.getPhoneticDetail().getUk());
        assertEquals("generated us", existingMetaWord.getPhoneticDetail().getUs());
    }

    private MetaWordEntryDtoV2 generatedEntry(String word) {
        MetaWordEntryDtoV2 entry = new MetaWordEntryDtoV2();
        entry.setWord(word);
        entry.setPhonetic(new com.example.words.dto.PhoneticDto("generated uk", "generated us"));
        entry.setPartOfSpeech(List.of(new com.example.words.dto.PartOfSpeechDto(
                "n.",
                List.of(new com.example.words.dto.DefinitionDto(
                        "generated definition",
                        "生成释义",
                        List.of(new com.example.words.dto.ExampleSentenceDto(
                                "Generated example.",
                                "生成例句。"
                        ))
                )),
                null,
                List.of(),
                List.of()
        )));
        return entry;
    }

    @Test
    void deleteByDictionaryIdShouldResetWordCount() {
        dictionaryWordService.deleteByDictionaryId(12L);

        verify(dictionaryWordRepository).deleteByDictionaryId(12L);
        assertEquals(12L, dictionaryService.lastUpdatedDictionaryId);
        assertEquals(0, dictionaryService.lastUpdatedWordCount);
    }

    @Test
    void saveIfNotExistsShouldIncrementCountOnlyWhenAssociationIsNew() {
        when(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(3L, 7L)).thenReturn(false);
        when(tagService.getOrCreateDefaultChapterTagId(3L)).thenReturn(11L);
        when(dictionaryWordRepository.findMaxEntryOrderByDictionaryIdAndChapterTagId(3L, 11L)).thenReturn(0);
        when(dictionaryWordRepository.countDistinctMetaWordIdByDictionaryId(3L)).thenReturn(1L);
        when(dictionaryWordRepository.countByDictionaryId(3L)).thenReturn(1L);

        dictionaryWordService.saveIfNotExists(3L, 7L);

        verify(dictionaryWordRepository).save(any());
        assertEquals(3L, dictionaryService.lastUpdatedDictionaryId);
        assertEquals(1, dictionaryService.lastUpdatedWordCount);
        assertEquals(1, dictionaryService.lastUpdatedEntryCount);
    }

    @Test
    void findSuggestionsForDictionaryShouldReturnTrimmedKeywordResults() {
        MetaWord metaWord = new MetaWord();
        metaWord.setId(9L);
        metaWord.setWord("apple");
        metaWord.setTranslation("苹果");
        metaWord.setPartOfSpeech("noun");
        metaWord.setPhonetic("/ˈæp.əl/");
        metaWord.setDifficulty(2);

        when(metaWordRepository.findSuggestionsByDictionaryIdAndKeyword(
                eq(5L),
                eq("app"),
                any(Pageable.class)))
                .thenReturn(List.of(metaWord));

        List<MetaWordSuggestionDto> suggestions = dictionaryWordService.findSuggestionsForDictionary(5L, "  app  ", 99);

        assertEquals(1, suggestions.size());
        assertEquals("apple", suggestions.get(0).getWord());
        assertEquals("苹果", suggestions.get(0).getTranslation());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(metaWordRepository).findSuggestionsByDictionaryIdAndKeyword(eq(5L), eq("app"), pageableCaptor.capture());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void findSuggestionsForDictionaryShouldSkipBlankKeyword() {
        List<MetaWordSuggestionDto> suggestions = dictionaryWordService.findSuggestionsForDictionary(5L, "   ", 8);

        assertTrue(suggestions.isEmpty());
        verifyNoInteractions(metaWordRepository);
    }

    @Test
    void findEntriesByDictionaryIdShouldNormalizeKeywordAndSortParameters() {
        when(dictionaryWordRepository.findEntriesPage(
                eq(7L),
                eq("%app%"),
                eq("entryOrder"),
                eq("asc"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        dictionaryWordService.findEntriesByDictionaryId(7L, 0, 0, "  App  ", "unknown", "invalid");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(dictionaryWordRepository).findEntriesPage(
                eq(7L),
                eq("%app%"),
                eq("entryOrder"),
                eq("asc"),
                pageableCaptor.capture()
        );
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(1, pageableCaptor.getValue().getPageSize());
    }

    private static final class RecordingDictionaryService extends DictionaryService {

        private Long lastIncrementDictionaryId;
        private Integer lastIncrementDelta;
        private Long lastUpdatedDictionaryId;
        private Integer lastUpdatedWordCount;
        private Integer lastUpdatedEntryCount;

        private RecordingDictionaryService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void updateWordCount(Long dictionaryId, int wordCount) {
            lastUpdatedDictionaryId = dictionaryId;
            lastUpdatedWordCount = wordCount;
        }

        @Override
        public void updateCounts(Long dictionaryId, int wordCount, int entryCount) {
            lastUpdatedDictionaryId = dictionaryId;
            lastUpdatedWordCount = wordCount;
            lastUpdatedEntryCount = entryCount;
        }

        @Override
        public void incrementWordCount(Long dictionaryId, int delta) {
            lastIncrementDictionaryId = dictionaryId;
            lastIncrementDelta = delta;
        }
    }
}
