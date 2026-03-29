package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.exception.ConflictException;
import com.example.words.model.BooksImportJob;
import com.example.words.model.BooksImportJobStatus;
import com.example.words.model.ImportMetaWordCandidateStatus;
import com.example.words.repository.BooksImportJobRepository;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class BooksImportJobServiceTest {

    private ObjectMapper objectMapper;

    @Mock
    private BooksImportJobRepository booksImportJobRepository;

    @Mock
    private MetaWordRepository metaWordRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private DictionaryWordRepository dictionaryWordRepository;

    @Mock
    private DictionaryService dictionaryService;

    @Mock
    private TagService tagService;

    @Mock
    private Executor booksImportTaskExecutor;

    @Mock
    private BooksImportProgressEmitterService booksImportProgressEmitterService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private DataSource dataSource;

    private BooksImportJobService booksImportJobService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        booksImportJobService = new BooksImportJobService(
                objectMapper,
                booksImportJobRepository,
                metaWordRepository,
                dictionaryRepository,
                dictionaryWordRepository,
                dictionaryService,
                tagService,
                booksImportTaskExecutor,
                booksImportProgressEmitterService,
                jdbcTemplate,
                namedParameterJdbcTemplate,
                new NoOpTransactionManager(),
                dataSource
        );
    }

    @Test
    void deleteBatchShouldRejectWhenNewerBatchRepublishedSameDictionary() {
        BooksImportJob job = buildJob("batch-1", BooksImportJobStatus.SUCCEEDED);
        when(booksImportJobRepository.findById("batch-1")).thenReturn(Optional.of(job));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("batch-1"), eq("batch-1")))
                .thenReturn(List.of("高考词汇"));

        ConflictException exception = assertThrows(ConflictException.class, () -> booksImportJobService.deleteBatch("batch-1"));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("高考词汇"));
        verify(dictionaryRepository, never()).deleteAllByIdInBatch(org.mockito.ArgumentMatchers.<Long>anyList());
        verify(booksImportJobRepository, never()).deleteById("batch-1");
    }

    @Test
    void deleteBatchShouldRemovePublishedDataWhenNoNewerPublishExists() {
        BooksImportJob job = buildJob("batch-1", BooksImportJobStatus.SUCCEEDED);
        when(booksImportJobRepository.findById("batch-1")).thenReturn(Optional.of(job));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("batch-1"), eq("batch-1")))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("batch-1")))
                .thenReturn(List.of(11L, 12L));

        assertDoesNotThrow(() -> booksImportJobService.deleteBatch("batch-1"));

        verify(dictionaryRepository).deleteAllByIdInBatch(List.of(11L, 12L));
        verify(dictionaryRepository).flush();
        verify(jdbcTemplate).update(anyString(), eq("batch-1"), eq("AUTO_CREATE"), eq("MANUALLY_RESOLVED"));
        verify(booksImportJobRepository).deleteById("batch-1");
    }

    @Test
    void candidateAccumulatorShouldKeepFirstSeenMetaWordForDuplicateSpellings() {
        BooksImportJobService.CandidateAccumulator accumulator = booksImportJobService.new CandidateAccumulator("state");
        accumulator.add(new BooksImportJobService.StageAggregateRow(
                "词书A",
                "state",
                "state",
                "first definition",
                1,
                null,
                null
        ));
        accumulator.add(new BooksImportJobService.StageAggregateRow(
                "词书B",
                "state",
                "state",
                "second definition",
                4,
                null,
                null
        ));

        BooksImportJobService.CandidateDecision decision = accumulator.decide(null);

        assertEquals(ImportMetaWordCandidateStatus.AUTO_CREATE, decision.status());
        assertEquals("state", decision.displayWord());
        assertEquals("first definition", decision.definition());
        assertEquals(1, decision.difficulty());
        assertEquals(2, decision.sourceCount());
        assertNull(decision.conflictType());
    }

    @Test
    void candidateAccumulatorShouldReuseExistingMetaWordWithoutOverwritingFields() {
        BooksImportJobService.CandidateAccumulator accumulator = booksImportJobService.new CandidateAccumulator("state");
        accumulator.add(new BooksImportJobService.StageAggregateRow(
                "词书A",
                "state",
                "state",
                "incoming definition",
                4,
                null,
                null
        ));

        BooksImportJobService.ExistingMetaWordSnapshot existing = new BooksImportJobService.ExistingMetaWordSnapshot(
                10L,
                "state",
                "existing definition",
                2,
                null,
                null
        );

        BooksImportJobService.CandidateDecision decision = accumulator.decide(existing);

        assertEquals(ImportMetaWordCandidateStatus.AUTO_UPDATE, decision.status());
        assertEquals("state", decision.displayWord());
        assertEquals("existing definition", decision.definition());
        assertEquals(2, decision.difficulty());
        assertNull(decision.conflictType());
    }

    private BooksImportJob buildJob(String id, BooksImportJobStatus status) {
        BooksImportJob job = new BooksImportJob();
        job.setId(id);
        job.setStatus(status);
        return job;
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
