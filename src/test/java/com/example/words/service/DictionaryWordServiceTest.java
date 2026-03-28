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
import com.example.words.model.MetaWord;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DictionaryWordServiceTest {

    @Mock
    private DictionaryWordRepository dictionaryWordRepository;

    @Mock
    private MetaWordRepository metaWordRepository;

    private DictionaryWordService dictionaryWordService;
    private RecordingDictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        dictionaryService = new RecordingDictionaryService();
        dictionaryWordService = new DictionaryWordService(
                dictionaryWordRepository,
                metaWordRepository,
                new JdbcTemplate(),
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
        when(dictionaryWordRepository.findByDictionaryId(10L)).thenReturn(List.of());

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
        assertEquals(10L, dictionaryService.lastIncrementDictionaryId);
        assertEquals(2, dictionaryService.lastIncrementDelta);
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

        dictionaryWordService.saveIfNotExists(3L, 7L);

        verify(dictionaryWordRepository).save(any());
        assertEquals(3L, dictionaryService.lastIncrementDictionaryId);
        assertEquals(1, dictionaryService.lastIncrementDelta);
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

    private static final class RecordingDictionaryService extends DictionaryService {

        private Long lastIncrementDictionaryId;
        private Integer lastIncrementDelta;
        private Long lastUpdatedDictionaryId;
        private Integer lastUpdatedWordCount;

        private RecordingDictionaryService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void updateWordCount(Long dictionaryId, int wordCount) {
            lastUpdatedDictionaryId = dictionaryId;
            lastUpdatedWordCount = wordCount;
        }

        @Override
        public void incrementWordCount(Long dictionaryId, int delta) {
            lastIncrementDictionaryId = dictionaryId;
            lastIncrementDelta = delta;
        }
    }
}
