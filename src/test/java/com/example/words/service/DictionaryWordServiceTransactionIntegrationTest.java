package com.example.words.service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.LearningDetailDto;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.exception.ConflictException;
import com.example.words.model.LearningDetail;
import com.example.words.model.MetaWord;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.url=jdbc:h2:mem:dictionary_word_ai_tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DictionaryWordService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DictionaryWordServiceTransactionIntegrationTest {

    @Autowired
    private DictionaryWordService dictionaryWordService;

    @Autowired
    private MetaWordRepository metaWordRepository;

    @Autowired
    private DictionaryWordRepository dictionaryWordRepository;

    @MockBean
    private TagService tagService;

    @MockBean
    private DictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        dictionaryWordRepository.deleteAll();
        metaWordRepository.deleteAll();
    }

    @Test
    void saveGeneratedWordShouldRollbackWordAndRelationWhenCountRefreshFails() {
        MetaWord existing = new MetaWord();
        existing.setWord("ability");
        existing.setLearningDetail(new LearningDetail(
                "authoritative material",
                "old hint",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        existing = metaWordRepository.saveAndFlush(existing);
        Long metaWordId = existing.getId();

        when(tagService.getOrCreateDefaultChapterTagId(36L)).thenReturn(7L);
        doThrow(new IllegalStateException("count refresh failed"))
                .when(dictionaryService).updateCounts(anyLong(), anyInt(), anyInt());

        assertThrows(IllegalStateException.class, () -> dictionaryWordService.saveGeneratedWordV2(
                36L,
                metaWordId,
                2L,
                "test-provider",
                "test-model",
                generatedEntry(),
                "new material"
        ));

        MetaWord reloaded = metaWordRepository.findById(metaWordId).orElseThrow();
        assertEquals("old hint", reloaded.getLearningDetail().getMemoryHint());
        assertEquals("authoritative material", reloaded.getLearningDetail().getLearningMaterial());
        assertFalse(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(36L, metaWordId));
    }

    @Test
    void saveGeneratedWordShouldSucceedWhenObservedVersionMatches() {
        MetaWord existing = new MetaWord();
        existing.setWord("ability");
        existing.setLearningDetail(new LearningDetail(
                "authoritative material",
                "old hint",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        existing = metaWordRepository.saveAndFlush(existing);
        final Long metaWordId = existing.getId();
        final Long observedVersion = existing.getVersion();

        dictionaryWordService.saveGeneratedWordV2(
                36L,
                metaWordId,
                2L,
                "test-provider",
                "test-model",
                generatedEntry(),
                "new material",
                observedVersion
        );

        assertThrows(ConflictException.class, () -> dictionaryWordService.saveGeneratedWordV2(
                36L,
                metaWordId,
                2L,
                "test-provider",
                "test-model",
                generatedEntry(),
                "competing material",
                observedVersion
        ));

        MetaWord reloaded = metaWordRepository.findById(metaWordId).orElseThrow();
        assertEquals("new hint", reloaded.getLearningDetail().getMemoryHint());
        assertEquals(observedVersion + 1, reloaded.getVersion());
    }

    @Test
    void concurrentGenerationsFromTheSameVersionShouldLeaveOnlyOneWrite() throws Exception {
        MetaWord existing = new MetaWord();
        existing.setWord("ability");
        existing.setLearningDetail(new LearningDetail(
                "authoritative material",
                "old hint",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        existing = metaWordRepository.saveAndFlush(existing);
        final Long metaWordId = existing.getId();
        final Long observedVersion = existing.getVersion();
        CountDownLatch bothRequestsReady = new CountDownLatch(2);
        CountDownLatch startBothRequests = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() ->
                    saveWhenBothRequestsStart(metaWordId, observedVersion, bothRequestsReady, startBothRequests));
            Future<Boolean> second = executor.submit(() ->
                    saveWhenBothRequestsStart(metaWordId, observedVersion, bothRequestsReady, startBothRequests));
            assertTrue(bothRequestsReady.await(10, TimeUnit.SECONDS));
            startBothRequests.countDown();

            int successfulWrites = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successfulWrites);
        } finally {
            executor.shutdownNow();
        }

        MetaWord reloaded = metaWordRepository.findById(metaWordId).orElseThrow();
        assertEquals(observedVersion + 1, reloaded.getVersion());
        assertEquals("new hint", reloaded.getLearningDetail().getMemoryHint());
    }

    @Test
    void manualEditDuringGenerationShouldRemainWhenTheObservedVersionIsStale() {
        MetaWord existing = new MetaWord();
        existing.setWord("ability");
        existing.setLearningDetail(new LearningDetail(
                "authoritative material",
                "old hint",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        existing = metaWordRepository.saveAndFlush(existing);
        Long metaWordId = existing.getId();
        Long observedVersion = existing.getVersion();
        existing.setLearningDetail(new LearningDetail(
                "authoritative material",
                "manual hint",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        metaWordRepository.saveAndFlush(existing);

        assertThrows(ConflictException.class, () -> dictionaryWordService.saveGeneratedWordV2(
                36L,
                metaWordId,
                2L,
                "test-provider",
                "test-model",
                generatedEntry(),
                "new material",
                observedVersion
        ));

        MetaWord reloaded = metaWordRepository.findById(metaWordId).orElseThrow();
        assertEquals("manual hint", reloaded.getLearningDetail().getMemoryHint());
    }

    private boolean saveFromObservedVersion(Long metaWordId, Long observedVersion) {
        try {
            dictionaryWordService.saveGeneratedWordV2(
                    36L,
                    metaWordId,
                    2L,
                    "test-provider",
                    "test-model",
                    generatedEntry(),
                    "new material",
                    observedVersion
            );
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean saveWhenBothRequestsStart(
            Long metaWordId,
            Long observedVersion,
            CountDownLatch bothRequestsReady,
            CountDownLatch startBothRequests) {
        bothRequestsReady.countDown();
        try {
            assertTrue(startBothRequests.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
        return saveFromObservedVersion(metaWordId, observedVersion);
    }

    private MetaWordEntryDtoV2 generatedEntry() {
        MetaWordEntryDtoV2 entry = new MetaWordEntryDtoV2();
        entry.setWord("ability");
        entry.setLearningDetail(new LearningDetailDto(
                null,
                "new hint",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        return entry;
    }
}
