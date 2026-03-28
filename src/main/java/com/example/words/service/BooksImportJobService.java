package com.example.words.service;

import com.example.words.dto.BooksImportBatchFileResponse;
import com.example.words.dto.BooksImportConflictResponse;
import com.example.words.dto.BooksImportJobResponse;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.dto.PartOfSpeechDto;
import com.example.words.dto.PhoneticDto;
import com.example.words.dto.ResolveBooksImportConflictRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ConflictException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.BooksImportBatchFileStatus;
import com.example.words.model.BooksImportJob;
import com.example.words.model.BooksImportJobStatus;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryCreationType;
import com.example.words.model.ImportConflictResolution;
import com.example.words.model.ImportConflictType;
import com.example.words.model.ImportMetaWordCandidateStatus;
import com.example.words.model.ImportPublishLogStatus;
import com.example.words.model.ImportResolutionSource;
import com.example.words.model.MetaWord;
import com.example.words.model.ResourceScopeType;
import com.example.words.repository.BooksImportJobRepository;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.util.WordNormalizationUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class BooksImportJobService {

    private static final Logger log = LoggerFactory.getLogger(BooksImportJobService.class);

    private static final String BOOKS_DIR = "/app/books";
    private static final String BATCH_TYPE = "BOOKS_FULL";
    private static final int STAGE_BATCH_SIZE = 500;
    private static final long IMPORT_LOCK_KEY = 2026032801L;
    private static final List<BooksImportJobStatus> ACTIVE_STATUSES = List.of(
            BooksImportJobStatus.PENDING,
            BooksImportJobStatus.SCANNING,
            BooksImportJobStatus.STAGING,
            BooksImportJobStatus.AUTO_MERGING,
            BooksImportJobStatus.PUBLISHING
    );
    private static final List<BooksImportJobStatus> TERMINAL_STATUSES = List.of(
            BooksImportJobStatus.SUCCEEDED,
            BooksImportJobStatus.FAILED,
            BooksImportJobStatus.CANCELLED,
            BooksImportJobStatus.DISCARDED
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final BooksImportJobRepository booksImportJobRepository;
    private final MetaWordRepository metaWordRepository;
    private final DictionaryRepository dictionaryRepository;
    private final DictionaryWordRepository dictionaryWordRepository;
    private final DictionaryService dictionaryService;
    private final TagService tagService;
    private final Executor booksImportTaskExecutor;
    private final BooksImportProgressEmitterService booksImportProgressEmitterService;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final DataSource dataSource;
    private final ReentrantLock fallbackImportLock = new ReentrantLock();

    public BooksImportJobService(
            ObjectMapper objectMapper,
            BooksImportJobRepository booksImportJobRepository,
            MetaWordRepository metaWordRepository,
            DictionaryRepository dictionaryRepository,
            DictionaryWordRepository dictionaryWordRepository,
            DictionaryService dictionaryService,
            TagService tagService,
            @Qualifier("booksImportTaskExecutor") Executor booksImportTaskExecutor,
            BooksImportProgressEmitterService booksImportProgressEmitterService,
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            PlatformTransactionManager transactionManager,
            DataSource dataSource) {
        this.objectMapper = objectMapper;
        this.booksImportJobRepository = booksImportJobRepository;
        this.metaWordRepository = metaWordRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.dictionaryWordRepository = dictionaryWordRepository;
        this.dictionaryService = dictionaryService;
        this.tagService = tagService;
        this.booksImportTaskExecutor = booksImportTaskExecutor;
        this.booksImportProgressEmitterService = booksImportProgressEmitterService;
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dataSource = dataSource;
    }

    public BooksImportJobResponse createAndStart() {
        return createAndStart(null);
    }

    public BooksImportJobResponse createAndStart(Long createdBy) {
        BooksImportJob job = createBatch(createdBy);
        booksImportTaskExecutor.execute(() -> runStageJob(job.getId()));
        publishSnapshot(job);
        return toResponse(job);
    }

    public BooksImportJobResponse startAutoMerge(String batchId) {
        BooksImportJob batch = getJobEntity(batchId);
        ensureStatus(batch, List.of(
                BooksImportJobStatus.STAGED,
                BooksImportJobStatus.WAITING_REVIEW,
                BooksImportJobStatus.READY_TO_PUBLISH,
                BooksImportJobStatus.FAILED
        ), "Batch cannot start auto-merge in current status");
        scheduleAutoMerge(batchId, true);
        return getJob(batchId);
    }

    public BooksImportJobResponse startPublish(String batchId) {
        BooksImportJob batch = getJobEntity(batchId);
        ensureStatus(
                batch,
                List.of(BooksImportJobStatus.WAITING_REVIEW, BooksImportJobStatus.READY_TO_PUBLISH),
                "Batch is not ready to publish"
        );
        schedulePublish(batchId, true);
        return getJob(batchId);
    }

    public BooksImportJobResponse discard(String batchId) {
        BooksImportJob batch = getJobEntity(batchId);
        if (ACTIVE_STATUSES.contains(batch.getStatus())) {
            throw new ConflictException("Active batch cannot be discarded");
        }

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM book_import_stage WHERE batch_id = ?", batchId);
            jdbcTemplate.update("DELETE FROM import_conflicts WHERE batch_id = ?", batchId);
            jdbcTemplate.update("DELETE FROM import_meta_word_candidates WHERE batch_id = ?", batchId);
            jdbcTemplate.update("DELETE FROM import_publish_logs WHERE batch_id = ?", batchId);
        });

        updateJob(batchId, job -> {
            job.setStatus(BooksImportJobStatus.DISCARDED);
            job.setCurrentFile(null);
            job.setCandidateCount(0L);
            job.setConflictCount(0L);
            job.setPublishFinishedAt(LocalDateTime.now());
        });
        return getJob(batchId);
    }

    public BooksImportJobResponse getJob(String jobId) {
        return toResponse(getJobEntity(jobId));
    }

    public BooksImportJobResponse getLatestJob() {
        BooksImportJob job = booksImportJobRepository.findTopByOrderByCreatedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException("No books import batch found"));
        return toResponse(job);
    }

    public List<BooksImportBatchFileResponse> getFiles(String batchId) {
        assertBatchExists(batchId);
        return jdbcTemplate.query(
                """
                        SELECT id,
                               batch_id,
                               file_name,
                               dictionary_name,
                               status,
                               row_count,
                               success_rows,
                               failed_rows,
                               duration_ms,
                               error_message,
                               created_at,
                               updated_at
                        FROM import_batch_files
                        WHERE batch_id = ?
                        ORDER BY id
                        """,
                importBatchFileRowMapper(),
                batchId
        );
    }

    public Page<BooksImportBatchFileResponse> getFilesPage(String batchId, int page, int size) {
        assertBatchExists(batchId);
        Pageable pageable = buildPageable(page, size);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_batch_files WHERE batch_id = ?",
                Long.class,
                batchId
        );
        List<BooksImportBatchFileResponse> content = jdbcTemplate.query(
                """
                        SELECT id,
                               batch_id,
                               file_name,
                               dictionary_name,
                               status,
                               row_count,
                               success_rows,
                               failed_rows,
                               duration_ms,
                               error_message,
                               created_at,
                               updated_at
                        FROM import_batch_files
                        WHERE batch_id = ?
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ?
                        OFFSET ?
                        """,
                importBatchFileRowMapper(),
                batchId,
                pageable.getPageSize(),
                pageable.getOffset()
        );
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public List<BooksImportConflictResponse> getConflicts(String batchId, ImportConflictType type, Boolean resolved) {
        assertBatchExists(batchId);
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       candidate_id,
                       normalized_word,
                       display_word,
                       conflict_type,
                       dictionary_names,
                       resolution,
                       existing_payload,
                       imported_payload,
                       resolved_payload,
                       comment,
                       resolved_at
                FROM import_conflicts
                WHERE batch_id = :batchId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("batchId", batchId);
        if (type != null) {
            sql.append(" AND conflict_type = :conflictType");
            params.addValue("conflictType", type.name());
        }
        if (resolved != null) {
            sql.append(resolved ? " AND resolution IS NOT NULL" : " AND resolution IS NULL");
        }
        sql.append(" ORDER BY resolved_at NULLS FIRST, id ASC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new BooksImportConflictResponse(
                rs.getLong("id"),
                nullableLong(rs, "candidate_id"),
                rs.getString("normalized_word"),
                rs.getString("display_word"),
                ImportConflictType.valueOf(rs.getString("conflict_type")),
                splitDictionaryNames(rs.getString("dictionary_names")),
                nullableEnum(rs.getString("resolution"), ImportConflictResolution.class),
                parsePayload(rs.getString("existing_payload")),
                parsePayload(rs.getString("imported_payload")),
                parsePayload(rs.getString("resolved_payload")),
                rs.getString("comment"),
                rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toLocalDateTime()
        ));
    }

    public BooksImportConflictResponse resolveConflict(
            String batchId,
            Long conflictId,
            ResolveBooksImportConflictRequest request,
            Long resolvedBy) {
        assertBatchExists(batchId);
        Map<String, Object> conflictRow = jdbcTemplate.query(
                """
                        SELECT c.id,
                               c.candidate_id,
                               c.normalized_word,
                               c.display_word,
                               c.conflict_type,
                               c.dictionary_names,
                               c.existing_payload::text AS existing_payload,
                               c.imported_payload::text AS imported_payload,
                               c.resolution,
                               c.resolved_payload::text AS resolved_payload,
                               c.comment,
                               c.resolved_at
                        FROM import_conflicts c
                        WHERE c.batch_id = ?
                          AND c.id = ?
                        """,
                rs -> rs.next() ? mapConflictRow(rs) : null,
                batchId,
                conflictId
        );
        if (conflictRow == null) {
            throw new ResourceNotFoundException("Import conflict not found: " + conflictId);
        }

        ImportConflictResolution resolution = request.getResolution();
        Map<String, Object> existingPayload = castMap(conflictRow.get("existingPayload"));
        Map<String, Object> importedPayload = castMap(conflictRow.get("importedPayload"));
        Map<String, Object> finalPayload = buildResolvedPayload(resolution, request, existingPayload, importedPayload);
        Long candidateId = (Long) conflictRow.get("candidateId");

        transactionTemplate.executeWithoutResult(status -> {
            if (resolution == ImportConflictResolution.IGNORE) {
                jdbcTemplate.update(
                        """
                                UPDATE import_meta_word_candidates
                                SET merge_status = ?,
                                    resolution_source = ?,
                                    updated_at = CURRENT_TIMESTAMP
                                WHERE id = ?
                                """,
                        ImportMetaWordCandidateStatus.IGNORED.name(),
                        ImportResolutionSource.MANUAL.name(),
                        candidateId
                );
            } else {
                jdbcTemplate.update(
                        """
                                UPDATE import_meta_word_candidates
                                SET display_word = ?,
                                    definition = ?,
                                    difficulty = ?,
                                    phonetic_detail = CAST(? AS jsonb),
                                    part_of_speech_detail = CAST(? AS jsonb),
                                    merge_status = ?,
                                    resolution_source = ?,
                                    updated_at = CURRENT_TIMESTAMP
                                WHERE id = ?
                                """,
                        finalPayload.get("word"),
                        finalPayload.get("definition"),
                        finalPayload.get("difficulty"),
                        toJsonString(finalPayload.get("phoneticDetail")),
                        toJsonString(finalPayload.get("partOfSpeechDetail")),
                        ImportMetaWordCandidateStatus.MANUALLY_RESOLVED.name(),
                        ImportResolutionSource.MANUAL.name(),
                        candidateId
                );
            }

            jdbcTemplate.update(
                    """
                            UPDATE import_conflicts
                            SET resolution = ?,
                                resolved_payload = CAST(? AS jsonb),
                                resolved_by = ?,
                                resolved_at = CURRENT_TIMESTAMP,
                                comment = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """,
                    resolution.name(),
                    toJsonString(finalPayload),
                    resolvedBy,
                    request.getComment(),
                    conflictId
            );
        });

        refreshBatchCountersAndStatus(batchId, false);
        return getConflicts(batchId, null, null).stream()
                .filter(conflict -> Objects.equals(conflict.getId(), conflictId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Import conflict not found after update: " + conflictId));
    }

    public SseEmitter subscribe(String jobId) {
        BooksImportJob job = getJobEntity(jobId);
        return booksImportProgressEmitterService.subscribe(jobId, toResponse(job), isTerminal(job.getStatus()));
    }

    private BooksImportJob createBatch(Long createdBy) {
        if (booksImportJobRepository.existsByStatusIn(ACTIVE_STATUSES)) {
            throw new ConflictException("A books import batch is already running");
        }

        BooksImportJob job = new BooksImportJob();
        job.setId(UUID.randomUUID().toString());
        job.setBatchType(BATCH_TYPE);
        job.setStatus(BooksImportJobStatus.PENDING);
        job.setTotalFiles(0);
        job.setProcessedFiles(0);
        job.setFailedFiles(0);
        job.setTotalRows(0L);
        job.setProcessedRows(0L);
        job.setSuccessRows(0L);
        job.setFailedRows(0L);
        job.setImportedDictionaryCount(0);
        job.setImportedWordCount(0L);
        job.setCandidateCount(0L);
        job.setConflictCount(0L);
        job.setCurrentFile(null);
        job.setErrorMessage(null);
        job.setCreatedBy(createdBy);
        return booksImportJobRepository.save(job);
    }

    private void runStageJob(String batchId) {
        try (ImportLockHandle ignored = acquireImportLock()) {
            updateJob(batchId, job -> {
                job.setStatus(BooksImportJobStatus.SCANNING);
                job.setStartedAt(LocalDateTime.now());
                job.setFinishedAt(null);
                job.setErrorMessage(null);
            });

            List<Path> files = discoverImportFiles();
            initializeBatchFiles(batchId, files);

            updateJob(batchId, job -> {
                job.setStatus(BooksImportJobStatus.STAGING);
                job.setTotalFiles(files.size());
                job.setImportedDictionaryCount(files.size());
            });

            long stagedRows = 0L;
            long failedRows = 0L;
            int processedFiles = 0;
            int failedFiles = 0;

            for (Path file : files) {
                String fileName = file.getFileName().toString();
                updateBatchCurrentFile(batchId, fileName, processedFiles, files.size());
                FileStageResult result = stageFile(batchId, file);
                processedFiles++;
                stagedRows += result.successRows();
                failedRows += result.failedRows();
                if (!result.success()) {
                    failedFiles++;
                }
                int currentProcessedFiles = processedFiles;
                int currentFailedFiles = failedFiles;
                long currentStagedRows = stagedRows;
                long currentFailedRows = failedRows;
                updateJob(batchId, job -> {
                    job.setProcessedFiles(currentProcessedFiles);
                    job.setFailedFiles(currentFailedFiles);
                    job.setProcessedRows(currentStagedRows + currentFailedRows);
                    job.setTotalRows(currentStagedRows + currentFailedRows);
                    job.setSuccessRows(currentStagedRows);
                    job.setFailedRows(currentFailedRows);
                    job.setImportedWordCount(currentStagedRows);
                    job.setCurrentFile(currentProcessedFiles >= files.size() ? null : fileName);
                });
            }

            long finalStagedRows = stagedRows;
            int finalFailedFiles = failedFiles;
            updateJob(batchId, job -> {
                job.setCurrentFile(null);
                job.setFinishedAt(LocalDateTime.now());
                if (finalStagedRows == 0 && finalFailedFiles > 0) {
                    job.setStatus(BooksImportJobStatus.FAILED);
                    job.setErrorMessage("No rows were staged successfully");
                } else {
                    job.setStatus(BooksImportJobStatus.STAGED);
                    if (finalFailedFiles > 0) {
                        job.setErrorMessage("Some files failed during staging");
                    }
                }
            });
            if (finalStagedRows > 0) {
                scheduleAutoMerge(batchId, false);
            }
        } catch (Exception ex) {
            log.error("Books import staging failed: {}", batchId, ex);
            markFailed(batchId, ex);
        }
    }

    private void runAutoMerge(String batchId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("DELETE FROM import_conflicts WHERE batch_id = ?", batchId);
                jdbcTemplate.update("DELETE FROM import_meta_word_candidates WHERE batch_id = ?", batchId);
            });

            Map<String, ExistingMetaWordSnapshot> existingByNormalizedWord = loadExistingMetaWords(batchId);
            List<CandidateInsertRow> candidateBuffer = new ArrayList<>(STAGE_BATCH_SIZE);
            List<ConflictInsertRow> conflictBuffer = new ArrayList<>(128);
            CandidateAccumulator[] current = new CandidateAccumulator[1];

            jdbcTemplate.query(
                    """
                            SELECT dictionary_name,
                                   normalized_word,
                                   word,
                                   definition,
                                   difficulty,
                                   phonetic_detail::text AS phonetic_detail,
                                   part_of_speech_detail::text AS part_of_speech_detail
                            FROM book_import_stage
                            WHERE batch_id = ?
                            ORDER BY normalized_word, dictionary_name, source_row_no
                            """,
                    rs -> {
                        StageAggregateRow row = new StageAggregateRow(
                                rs.getString("dictionary_name"),
                                rs.getString("normalized_word"),
                                rs.getString("word"),
                                rs.getString("definition"),
                                nullableInteger(rs, "difficulty"),
                                rs.getString("phonetic_detail"),
                                rs.getString("part_of_speech_detail")
                        );
                        if (current[0] == null || !current[0].normalizedWord().equals(row.normalizedWord())) {
                            flushAccumulator(batchId, current[0], existingByNormalizedWord, candidateBuffer, conflictBuffer);
                            current[0] = new CandidateAccumulator(row.normalizedWord());
                        }
                        current[0].add(row);
                        if (candidateBuffer.size() >= STAGE_BATCH_SIZE) {
                            insertCandidateRows(candidateBuffer);
                            candidateBuffer.clear();
                        }
                        if (conflictBuffer.size() >= 64) {
                            if (!candidateBuffer.isEmpty()) {
                                insertCandidateRows(candidateBuffer);
                                candidateBuffer.clear();
                            }
                            insertConflictRows(conflictBuffer);
                            conflictBuffer.clear();
                        }
                    },
                    batchId
            );

            flushAccumulator(batchId, current[0], existingByNormalizedWord, candidateBuffer, conflictBuffer);
            if (!candidateBuffer.isEmpty()) {
                insertCandidateRows(candidateBuffer);
            }
            if (!conflictBuffer.isEmpty()) {
                insertConflictRows(conflictBuffer);
            }

            refreshBatchCountersAndStatus(batchId, true);
            BooksImportJob latestJob = getJobEntity(batchId);
            if (latestJob.getStatus() == BooksImportJobStatus.READY_TO_PUBLISH
                    || latestJob.getStatus() == BooksImportJobStatus.WAITING_REVIEW) {
                schedulePublish(batchId, false);
            }
        } catch (Exception ex) {
            log.error("Books import auto-merge failed: {}", batchId, ex);
            markFailed(batchId, ex);
        }
    }

    private void runPublish(String batchId) {
        try {
            long unresolvedConflictCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM import_conflicts WHERE batch_id = ? AND resolution IS NULL",
                    Long.class,
                    batchId
            );

            upsertMetaWords(batchId);
            publishDictionaryEntries(batchId);

            if (unresolvedConflictCount == 0) {
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update("DELETE FROM book_import_stage WHERE batch_id = ?", batchId);
                });
            }

            updateJob(batchId, job -> {
                job.setStatus(unresolvedConflictCount > 0
                        ? BooksImportJobStatus.WAITING_REVIEW
                        : BooksImportJobStatus.SUCCEEDED);
                job.setCurrentFile(null);
                job.setPublishFinishedAt(LocalDateTime.now());
                job.setFinishedAt(LocalDateTime.now());
                if (unresolvedConflictCount == 0) {
                    job.setErrorMessage(null);
                }
            });
        } catch (Exception ex) {
            log.error("Books import publish failed: {}", batchId, ex);
            markFailed(batchId, ex);
        }
    }

    private List<Path> discoverImportFiles() {
        Path dir = Path.of(BOOKS_DIR);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new ResourceNotFoundException("Books directory not found: " + BOOKS_DIR);
        }
        try {
            List<Path> files = Files.list(dir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return lowerName.endsWith(".csv") || lowerName.endsWith(".json");
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (files.isEmpty()) {
                throw new ResourceNotFoundException("No importable books files found in " + BOOKS_DIR);
            }
            return files;
        } catch (IOException ex) {
            throw new BadRequestException("Failed to scan books directory: " + ex.getMessage());
        }
    }

    private void initializeBatchFiles(String batchId, List<Path> files) {
        jdbcTemplate.update("DELETE FROM import_batch_files WHERE batch_id = ?", batchId);
        String sql = """
                INSERT INTO import_batch_files (batch_id, file_name, dictionary_name, status, file_size)
                VALUES (?, ?, ?, ?, ?)
                """;
        for (Path file : files) {
            try {
                String fileName = file.getFileName().toString();
                jdbcTemplate.update(
                        sql,
                        batchId,
                        fileName,
                        stripExtension(fileName),
                        BooksImportBatchFileStatus.PENDING.name(),
                        Files.size(file)
                );
            } catch (IOException ex) {
                throw new BadRequestException("Failed to read file metadata for " + file.getFileName());
            }
        }
    }

    private FileStageResult stageFile(String batchId, Path file) {
        String fileName = file.getFileName().toString();
        String dictionaryName = stripExtension(fileName);
        String category = dictionaryService.extractCategory(dictionaryName);
        updateBatchFileStatus(batchId, fileName, BooksImportBatchFileStatus.STAGING, null, null, null, null);

        long startTime = System.currentTimeMillis();
        try {
            FileStageResult result = isJsonFile(fileName)
                    ? stageJsonFile(batchId, file, fileName, dictionaryName, category)
                    : stageCsvFile(batchId, file, fileName, dictionaryName, category);
            long durationMs = System.currentTimeMillis() - startTime;
            updateBatchFileStatus(
                    batchId,
                    fileName,
                    result.success() ? BooksImportBatchFileStatus.STAGED : BooksImportBatchFileStatus.FAILED,
                    result.totalRows(),
                    result.successRows(),
                    result.failedRows(),
                    result.success() ? null : normalizeErrorMessage(result.errorMessage()),
                    durationMs
            );
            return result;
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Failed to stage file {}", fileName, ex);
            String errorMessage = normalizeErrorMessage(ex);
            updateBatchFileStatus(
                    batchId,
                    fileName,
                    BooksImportBatchFileStatus.FAILED,
                    0L,
                    0L,
                    0L,
                    errorMessage,
                    durationMs
            );
            return new FileStageResult(false, 0L, 0L, 0L, errorMessage);
        }
    }

    private FileStageResult stageCsvFile(
            String batchId,
            Path file,
            String fileName,
            String dictionaryName,
            String category) throws IOException, CsvValidationException {
        List<StageRow> batch = new ArrayList<>(STAGE_BATCH_SIZE);
        long totalRows = 0L;
        long successRows = 0L;
        long failedRows = 0L;
        int entryOrder = 1;

        try (BufferedReader fileReader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
            String[] line;
            while ((line = reader.readNext()) != null) {
                totalRows++;
                if (line.length < 2) {
                    failedRows++;
                    continue;
                }

                String word = trimToNull(line[0]);
                String definition = trimToNull(line[1]);
                if (word == null) {
                    failedRows++;
                    continue;
                }

                        batch.add(new StageRow(
                                batchId,
                                fileName,
                        dictionaryName,
                        category,
                        totalRows,
                        entryOrder++,
                        word,
                        WordNormalizationUtils.normalize(word),
                        definition,
                        estimateDifficulty(category),
                        null,
                        null,
                        toJsonString(buildCsvRawPayload(word, definition))
                ));
                if (batch.size() >= STAGE_BATCH_SIZE) {
                    stageRows(batch);
                    successRows += batch.size();
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            stageRows(batch);
            successRows += batch.size();
        }

        return new FileStageResult(true, totalRows, successRows, failedRows, null);
    }

    private FileStageResult stageJsonFile(
            String batchId,
            Path file,
            String fileName,
            String dictionaryName,
            String category) throws IOException {
        List<StageRow> batch = new ArrayList<>(STAGE_BATCH_SIZE);
        long totalRows = 0L;
        long successRows = 0L;
        long failedRows = 0L;
        int entryOrder = 1;

        try (JsonParser parser = objectMapper.getFactory().createParser(file.toFile())) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new BadRequestException("JSON books file must be an array: " + fileName);
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                totalRows++;
                JsonNode rawNode = objectMapper.readTree(parser);
                MetaWordEntryDtoV2 entry = objectMapper.treeToValue(rawNode, MetaWordEntryDtoV2.class);
                String word = entry == null ? null : trimToNull(entry.getWord());
                if (word == null) {
                    failedRows++;
                    continue;
                }

                batch.add(new StageRow(
                        batchId,
                        fileName,
                        dictionaryName,
                        category,
                                totalRows,
                                entryOrder++,
                                word,
                                WordNormalizationUtils.normalize(word),
                                extractDefinition(entry.getPartOfSpeech()),
                                entry.getDifficulty() != null ? entry.getDifficulty() : estimateDifficulty(category),
                                toJsonString(entry.getPhonetic()),
                                toJsonString(entry.getPartOfSpeech()),
                        rawNode.toString()
                ));
                if (batch.size() >= STAGE_BATCH_SIZE) {
                    stageRows(batch);
                    successRows += batch.size();
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            stageRows(batch);
            successRows += batch.size();
        }

        return new FileStageResult(true, totalRows, successRows, failedRows, null);
    }

    private void stageRows(List<StageRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO book_import_stage (
                            batch_id,
                            file_name,
                            dictionary_name,
                            category,
                            source_row_no,
                            entry_order,
                            word,
                            normalized_word,
                            definition,
                            difficulty,
                            phonetic_detail,
                            part_of_speech_detail,
                            raw_payload
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb))
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        StageRow row = rows.get(i);
                        ps.setString(1, row.batchId());
                        ps.setString(2, row.fileName());
                        ps.setString(3, row.dictionaryName());
                        ps.setString(4, row.category());
                        ps.setLong(5, row.sourceRowNo());
                        ps.setInt(6, row.entryOrder());
                        ps.setString(7, row.word());
                        ps.setString(8, row.normalizedWord());
                        if (row.definition() == null) {
                            ps.setNull(9, Types.VARCHAR);
                        } else {
                            ps.setString(9, row.definition());
                        }
                        if (row.difficulty() == null) {
                            ps.setNull(10, Types.INTEGER);
                        } else {
                            ps.setInt(10, row.difficulty());
                        }
                        setNullableString(ps, 11, row.phoneticDetailJson());
                        setNullableString(ps, 12, row.partOfSpeechDetailJson());
                        setNullableString(ps, 13, row.rawPayloadJson());
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                }
        );
    }

    private Map<String, Object> buildCsvRawPayload(String word, String definition) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("word", word);
        payload.put("definition", definition);
        return payload;
    }

    private Pageable buildPageable(int page, int size) {
        int normalizedPage = Math.max(page, 1) - 1;
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    private String normalizeErrorMessage(Exception ex) {
        return normalizeErrorMessage(ex == null ? null : ex.getMessage(), ex == null ? null : ex.getClass().getSimpleName());
    }

    private String normalizeErrorMessage(String message) {
        return normalizeErrorMessage(message, "UnknownError");
    }

    private String normalizeErrorMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }

    private void flushAccumulator(
            String batchId,
            CandidateAccumulator accumulator,
            Map<String, ExistingMetaWordSnapshot> existingByNormalizedWord,
            List<CandidateInsertRow> candidateBuffer,
            List<ConflictInsertRow> conflictBuffer) {
        if (accumulator == null || accumulator.sourceCount() == 0) {
            return;
        }

        ExistingMetaWordSnapshot existing = existingByNormalizedWord.get(accumulator.normalizedWord());
        CandidateDecision decision = accumulator.decide(existing);
        candidateBuffer.add(new CandidateInsertRow(
                batchId,
                decision.normalizedWord(),
                decision.displayWord(),
                decision.definition(),
                decision.difficulty(),
                decision.phoneticDetailJson(),
                decision.partOfSpeechDetailJson(),
                decision.sourceCount(),
                decision.status(),
                existing == null ? null : existing.id(),
                decision.status() == ImportMetaWordCandidateStatus.PENDING_REVIEW ? null : ImportResolutionSource.AUTO
        ));
        if (decision.conflictType() != null) {
            conflictBuffer.add(new ConflictInsertRow(
                    batchId,
                    decision.normalizedWord(),
                    decision.displayWord(),
                    decision.conflictType(),
                    String.join(",", accumulator.dictionaryNames()),
                    existing == null ? null : existing.id(),
                    decision.existingPayloadJson(),
                    decision.importedPayloadJson()
            ));
        }
    }

    private Map<String, ExistingMetaWordSnapshot> loadExistingMetaWords(String batchId) {
        Map<String, ExistingMetaWordSnapshot> snapshots = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                        SELECT m.id,
                               m.normalized_word,
                               m.word,
                               m.definition,
                               m.difficulty,
                               m.phonetic_detail::text AS phonetic_detail,
                               m.part_of_speech_detail::text AS part_of_speech_detail
                        FROM meta_words m
                        JOIN (
                            SELECT DISTINCT normalized_word
                            FROM book_import_stage
                            WHERE batch_id = ?
                        ) s
                          ON s.normalized_word = m.normalized_word
                        """,
                rs -> {
                    while (rs.next()) {
                        snapshots.put(
                                rs.getString("normalized_word"),
                                new ExistingMetaWordSnapshot(
                                        rs.getLong("id"),
                                        rs.getString("word"),
                                        rs.getString("definition"),
                                        nullableInteger(rs, "difficulty"),
                                        rs.getString("phonetic_detail"),
                                        rs.getString("part_of_speech_detail")
                                )
                        );
                    }
                    return null;
                },
                batchId
        );
        return snapshots;
    }

    private void insertCandidateRows(List<CandidateInsertRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        namedParameterJdbcTemplate.batchUpdate(
                """
                        INSERT INTO import_meta_word_candidates (
                            batch_id,
                            normalized_word,
                            display_word,
                            definition,
                            difficulty,
                            phonetic_detail,
                            part_of_speech_detail,
                            source_count,
                            merge_status,
                            matched_meta_word_id,
                            resolution_source,
                            created_at,
                            updated_at
                        ) VALUES (
                            :batchId,
                            :normalizedWord,
                            :displayWord,
                            :definition,
                            :difficulty,
                            CAST(:phoneticDetail AS jsonb),
                            CAST(:partOfSpeechDetail AS jsonb),
                            :sourceCount,
                            :mergeStatus,
                            :matchedMetaWordId,
                            :resolutionSource,
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP
                        )
                        """,
                rows.stream().map(row -> new MapSqlParameterSource()
                        .addValue("batchId", row.batchId())
                        .addValue("normalizedWord", row.normalizedWord())
                        .addValue("displayWord", row.displayWord())
                        .addValue("definition", row.definition())
                        .addValue("difficulty", row.difficulty())
                        .addValue("phoneticDetail", row.phoneticDetailJson())
                        .addValue("partOfSpeechDetail", row.partOfSpeechDetailJson())
                        .addValue("sourceCount", row.sourceCount())
                        .addValue("mergeStatus", row.status().name())
                        .addValue("matchedMetaWordId", row.matchedMetaWordId())
                        .addValue("resolutionSource", row.resolutionSource() == null ? null : row.resolutionSource().name()))
                        .toArray(MapSqlParameterSource[]::new)
        );
    }

    private void insertConflictRows(List<ConflictInsertRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        namedParameterJdbcTemplate.batchUpdate(
                """
                        INSERT INTO import_conflicts (
                            batch_id,
                            candidate_id,
                            normalized_word,
                            display_word,
                            conflict_type,
                            dictionary_names,
                            existing_meta_word_id,
                            existing_payload,
                            imported_payload,
                            created_at,
                            updated_at
                        ) VALUES (
                            :batchId,
                            (
                                SELECT id
                                FROM import_meta_word_candidates
                                WHERE batch_id = :batchId
                                  AND normalized_word = :normalizedWord
                            ),
                            :normalizedWord,
                            :displayWord,
                            :conflictType,
                            :dictionaryNames,
                            :existingMetaWordId,
                            CAST(:existingPayload AS jsonb),
                            CAST(:importedPayload AS jsonb),
                            CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP
                        )
                        """,
                rows.stream().map(row -> new MapSqlParameterSource()
                        .addValue("batchId", row.batchId())
                        .addValue("normalizedWord", row.normalizedWord())
                        .addValue("displayWord", row.displayWord())
                        .addValue("conflictType", row.conflictType().name())
                        .addValue("dictionaryNames", row.dictionaryNames())
                        .addValue("existingMetaWordId", row.existingMetaWordId())
                        .addValue("existingPayload", row.existingPayloadJson())
                        .addValue("importedPayload", row.importedPayloadJson()))
                        .toArray(MapSqlParameterSource[]::new)
        );
    }

    private void refreshBatchCountersAndStatus(String batchId, boolean fromAutoMerge) {
        Long candidateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_meta_word_candidates WHERE batch_id = ?",
                Long.class,
                batchId
        );
        Long conflictCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM import_conflicts WHERE batch_id = ? AND resolution IS NULL",
                Long.class,
                batchId
        );
        updateJob(batchId, job -> {
            job.setCandidateCount(candidateCount);
            job.setConflictCount(conflictCount);
            if (fromAutoMerge || job.getStatus() == BooksImportJobStatus.WAITING_REVIEW) {
                job.setStatus(conflictCount != null && conflictCount > 0
                        ? BooksImportJobStatus.WAITING_REVIEW
                        : BooksImportJobStatus.READY_TO_PUBLISH);
            }
            job.setFinishedAt(LocalDateTime.now());
            job.setCurrentFile(null);
        });
    }

    private void upsertMetaWords(String batchId) {
        jdbcTemplate.update(
                """
                        INSERT INTO meta_words (
                            normalized_word,
                            word,
                            definition,
                            difficulty,
                            phonetic_detail,
                            part_of_speech_detail,
                            created_at,
                            updated_at
                        )
                        SELECT normalized_word,
                               display_word,
                               definition,
                               difficulty,
                               phonetic_detail,
                               part_of_speech_detail,
                               CURRENT_TIMESTAMP,
                               CURRENT_TIMESTAMP
                        FROM import_meta_word_candidates
                        WHERE batch_id = ?
                          AND merge_status IN (?, ?, ?)
                        ON CONFLICT (normalized_word) DO UPDATE
                        SET word = EXCLUDED.word,
                            definition = EXCLUDED.definition,
                            difficulty = EXCLUDED.difficulty,
                            phonetic_detail = EXCLUDED.phonetic_detail,
                            part_of_speech_detail = EXCLUDED.part_of_speech_detail,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE meta_words.word IS DISTINCT FROM EXCLUDED.word
                           OR meta_words.definition IS DISTINCT FROM EXCLUDED.definition
                           OR meta_words.difficulty IS DISTINCT FROM EXCLUDED.difficulty
                           OR meta_words.phonetic_detail IS DISTINCT FROM EXCLUDED.phonetic_detail
                           OR meta_words.part_of_speech_detail IS DISTINCT FROM EXCLUDED.part_of_speech_detail
                        """,
                batchId,
                ImportMetaWordCandidateStatus.AUTO_CREATE.name(),
                ImportMetaWordCandidateStatus.AUTO_UPDATE.name(),
                ImportMetaWordCandidateStatus.MANUALLY_RESOLVED.name()
        );
    }

    private void publishDictionaryEntries(String batchId) {
        List<DictionaryPublishSource> dictionaries = jdbcTemplate.query(
                """
                        SELECT stage.dictionary_name,
                               MAX(stage.category) AS category,
                               MIN(files.file_name) AS file_name,
                               MIN(files.file_size) AS file_size
                        FROM book_import_stage stage
                        LEFT JOIN import_batch_files files
                          ON files.batch_id = stage.batch_id
                         AND files.dictionary_name = stage.dictionary_name
                        WHERE stage.batch_id = ?
                        GROUP BY stage.dictionary_name
                        ORDER BY stage.dictionary_name
                        """,
                (rs, rowNum) -> new DictionaryPublishSource(
                        rs.getString("dictionary_name"),
                        rs.getString("category"),
                        rs.getString("file_name"),
                        nullableLong(rs, "file_size")
                ),
                batchId
        );
        List<ResolvedDictionaryEntry> entries = jdbcTemplate.query(
                """
                        WITH dedup_entries AS (
                            SELECT dictionary_name,
                                   MAX(category) AS category,
                                   normalized_word,
                                   MIN(entry_order) AS first_entry_order
                            FROM book_import_stage
                            WHERE batch_id = ?
                            GROUP BY dictionary_name, normalized_word
                        )
                        SELECT de.dictionary_name,
                               de.category,
                               de.normalized_word,
                               de.first_entry_order,
                               mw.id AS meta_word_id
                        FROM dedup_entries de
                        JOIN meta_words mw
                          ON mw.normalized_word = de.normalized_word
                        ORDER BY de.dictionary_name, de.first_entry_order, mw.id
                        """,
                (rs, rowNum) -> new ResolvedDictionaryEntry(
                        rs.getString("dictionary_name"),
                        rs.getString("category"),
                        rs.getString("normalized_word"),
                        rs.getInt("first_entry_order"),
                        rs.getLong("meta_word_id")
                ),
                batchId
        );

        Map<String, List<ResolvedDictionaryEntry>> entriesByDictionary = new LinkedHashMap<>();
        for (ResolvedDictionaryEntry entry : entries) {
            entriesByDictionary.computeIfAbsent(entry.dictionaryName(), ignored -> new ArrayList<>()).add(entry);
        }

        for (DictionaryPublishSource source : dictionaries) {
            String dictionaryName = source.dictionaryName();
            List<ResolvedDictionaryEntry> dictionaryEntries = entriesByDictionary.getOrDefault(dictionaryName, List.of());
            LocalDateTime startedAt = LocalDateTime.now();
            Dictionary dictionary = transactionTemplate.execute(status -> publishSingleDictionary(
                    dictionaryName,
                    defaultIfBlank(source.category(), "其他"),
                    source.fileName(),
                    source.fileSize(),
                    dictionaryEntries
            ));
            jdbcTemplate.update(
                    """
                            INSERT INTO import_publish_logs (
                                batch_id,
                                dictionary_id,
                                dictionary_name,
                                published_entry_count,
                                published_word_count,
                                status,
                                started_at,
                                finished_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    batchId,
                    dictionary.getId(),
                    dictionaryName,
                    dictionaryEntries.size(),
                    dictionaryEntries.stream().map(ResolvedDictionaryEntry::metaWordId).distinct().count(),
                    ImportPublishLogStatus.SUCCEEDED.name(),
                    startedAt,
                    LocalDateTime.now()
            );
        }
    }

    private Dictionary publishSingleDictionary(
            String dictionaryName,
            String category,
            String fileName,
            Long fileSize,
            List<ResolvedDictionaryEntry> dictionaryEntries) {
        Dictionary dictionary = dictionaryRepository.findByName(dictionaryName)
                .map(existing -> {
                    if (existing.getCreationType() == DictionaryCreationType.USER_CREATED) {
                        throw new ConflictException("Dictionary name conflicts with user-created dictionary: " + dictionaryName);
                    }
                    existing.setCategory(category);
                    existing.setFilePath(buildDictionaryFilePath(fileName));
                    existing.setFileSize(fileSize);
                    existing.setCreationType(DictionaryCreationType.IMPORTED);
                    existing.setScopeType(ResourceScopeType.SYSTEM);
                    return dictionaryRepository.save(existing);
                })
                .orElseGet(() -> dictionaryRepository.save(new Dictionary(
                        dictionaryName,
                        buildDictionaryFilePath(fileName),
                        fileSize,
                        category,
                        DictionaryCreationType.IMPORTED
                )));

        Long dictionaryId = dictionary.getId();
        Long defaultChapterTagId = tagService.getOrCreateDefaultChapterTagId(dictionaryId);
        dictionaryWordRepository.deleteByDictionaryId(dictionaryId);
        batchInsertDictionaryWords(dictionaryId, defaultChapterTagId, dictionaryEntries);
        dictionaryService.updateCounts(
                dictionaryId,
                (int) dictionaryEntries.stream().map(ResolvedDictionaryEntry::metaWordId).distinct().count(),
                dictionaryEntries.size()
        );
        return dictionary;
    }

    private void batchInsertDictionaryWords(
            Long dictionaryId,
            Long chapterTagId,
            List<ResolvedDictionaryEntry> dictionaryEntries) {
        if (dictionaryEntries.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO dictionary_words (
                            dictionary_id,
                            meta_word_id,
                            chapter_tag_id,
                            entry_order
                        ) VALUES (?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ResolvedDictionaryEntry entry = dictionaryEntries.get(i);
                        ps.setLong(1, dictionaryId);
                        ps.setLong(2, entry.metaWordId());
                        ps.setLong(3, chapterTagId);
                        ps.setInt(4, i + 1);
                    }

                    @Override
                    public int getBatchSize() {
                        return dictionaryEntries.size();
                    }
                }
        );
    }

    private String buildDictionaryFilePath(String fileName) {
        String normalizedFileName = trimToNull(fileName);
        return normalizedFileName == null ? null : Path.of(BOOKS_DIR, normalizedFileName).toString();
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalizedValue = trimToNull(value);
        return normalizedValue == null ? fallback : normalizedValue;
    }

    private Map<String, Object> buildResolvedPayload(
            ImportConflictResolution resolution,
            ResolveBooksImportConflictRequest request,
            Map<String, Object> existingPayload,
            Map<String, Object> importedPayload) {
        if (resolution == ImportConflictResolution.IGNORE) {
            return Map.of("ignored", true);
        }

        Map<String, Object> base = switch (resolution) {
            case KEEP_EXISTING -> new LinkedHashMap<>(existingPayload == null ? Map.of() : existingPayload);
            case USE_IMPORTED -> new LinkedHashMap<>(importedPayload == null ? Map.of() : importedPayload);
            case MANUAL -> new LinkedHashMap<>(importedPayload == null ? Map.of() : importedPayload);
            case IGNORE -> new LinkedHashMap<>();
        };

        if (resolution == ImportConflictResolution.MANUAL) {
            if (request.getFinalWord() != null && !request.getFinalWord().trim().isEmpty()) {
                base.put("word", request.getFinalWord().trim());
            }
            if (request.getFinalDefinition() != null) {
                base.put("definition", trimToNull(request.getFinalDefinition()));
            }
            if (request.getFinalDifficulty() != null) {
                base.put("difficulty", request.getFinalDifficulty());
            }
            if (request.getFinalPhonetic() != null) {
                base.put("phoneticDetail", request.getFinalPhonetic());
            }
            if (request.getFinalPartOfSpeech() != null) {
                base.put("partOfSpeechDetail", request.getFinalPartOfSpeech());
            }
        }
        return base;
    }

    private void updateBatchCurrentFile(String batchId, String fileName, int processedFiles, int totalFiles) {
        updateJob(batchId, job -> {
            job.setCurrentFile(fileName);
            job.setProcessedFiles(processedFiles);
            job.setTotalFiles(totalFiles);
        });
    }

    private void updateBatchFileStatus(
            String batchId,
            String fileName,
            BooksImportBatchFileStatus status,
            Long rowCount,
            Long successRows,
            Long failedRows,
            String errorMessage) {
        updateBatchFileStatus(batchId, fileName, status, rowCount, successRows, failedRows, errorMessage, null);
    }

    private void updateBatchFileStatus(
            String batchId,
            String fileName,
            BooksImportBatchFileStatus status,
            Long rowCount,
            Long successRows,
            Long failedRows,
            String errorMessage,
            Long durationMs) {
        jdbcTemplate.update(
                """
                        UPDATE import_batch_files
                        SET status = ?,
                            row_count = COALESCE(?, row_count),
                            success_rows = COALESCE(?, success_rows),
                            failed_rows = COALESCE(?, failed_rows),
                            duration_ms = COALESCE(?, duration_ms),
                            error_message = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE batch_id = ?
                          AND file_name = ?
                        """,
                status.name(),
                rowCount,
                successRows,
                failedRows,
                durationMs,
                errorMessage,
                batchId,
                fileName
        );
    }

    private ImportLockHandle acquireImportLock() {
        try {
            Connection connection = dataSource.getConnection();
            String productName = connection.getMetaData().getDatabaseProductName();
            if ("PostgreSQL".equalsIgnoreCase(productName)) {
                try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                    statement.setLong(1, IMPORT_LOCK_KEY);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next() || !rs.getBoolean(1)) {
                            connection.close();
                            throw new ConflictException("Another books import is already holding the advisory lock");
                        }
                    }
                }
                return () -> {
                    try (PreparedStatement unlock = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                        unlock.setLong(1, IMPORT_LOCK_KEY);
                        unlock.execute();
                    } finally {
                        connection.close();
                    }
                };
            }
            if (!fallbackImportLock.tryLock()) {
                connection.close();
                throw new ConflictException("Another books import is already running");
            }
            return () -> {
                try {
                    connection.close();
                } finally {
                    fallbackImportLock.unlock();
                }
            };
        } catch (SQLException ex) {
            throw new BadRequestException("Failed to acquire import lock: " + ex.getMessage());
        }
    }

    private void markFailed(String jobId, Exception ex) {
        updateJob(jobId, job -> {
            job.setStatus(BooksImportJobStatus.FAILED);
            job.setCurrentFile(null);
            job.setFinishedAt(LocalDateTime.now());
            job.setPublishFinishedAt(LocalDateTime.now());
            job.setErrorMessage(ex.getMessage());
        });
    }

    private void updateJob(String jobId, Consumer<BooksImportJob> updater) {
        BooksImportJob job = getJobEntity(jobId);
        updater.accept(job);
        BooksImportJob savedJob = booksImportJobRepository.save(job);
        publishSnapshot(savedJob);
    }

    private void scheduleAutoMerge(String batchId, boolean clearErrorMessage) {
        updateJob(batchId, job -> {
            job.setStatus(BooksImportJobStatus.AUTO_MERGING);
            if (clearErrorMessage) {
                job.setErrorMessage(null);
            }
            job.setCurrentFile(null);
            job.setFinishedAt(null);
        });
        booksImportTaskExecutor.execute(() -> runAutoMerge(batchId));
    }

    private void schedulePublish(String batchId, boolean clearErrorMessage) {
        updateJob(batchId, job -> {
            job.setStatus(BooksImportJobStatus.PUBLISHING);
            job.setPublishStartedAt(LocalDateTime.now());
            job.setPublishFinishedAt(null);
            if (clearErrorMessage) {
                job.setErrorMessage(null);
            }
            job.setCurrentFile(null);
            job.setFinishedAt(null);
        });
        booksImportTaskExecutor.execute(() -> runPublish(batchId));
    }

    private BooksImportJob getJobEntity(String jobId) {
        return booksImportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Books import batch not found: " + jobId));
    }

    private void assertBatchExists(String batchId) {
        if (!booksImportJobRepository.existsById(batchId)) {
            throw new ResourceNotFoundException("Books import batch not found: " + batchId);
        }
    }

    private void ensureStatus(BooksImportJob batch, Collection<BooksImportJobStatus> allowed, String message) {
        if (!allowed.contains(batch.getStatus())) {
            throw new ConflictException(message + ": " + batch.getStatus());
        }
    }

    private BooksImportJobResponse toResponse(BooksImportJob job) {
        return new BooksImportJobResponse(
                job.getId(),
                job.getStatus(),
                job.getBatchType(),
                job.getTotalFiles(),
                job.getProcessedFiles(),
                job.getFailedFiles(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getSuccessRows(),
                job.getFailedRows(),
                job.getImportedDictionaryCount(),
                job.getImportedWordCount(),
                job.getCurrentFile(),
                job.getCandidateCount(),
                job.getConflictCount(),
                job.getErrorMessage(),
                job.getCreatedBy(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getPublishStartedAt(),
                job.getPublishFinishedAt(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private void publishSnapshot(BooksImportJob job) {
        BooksImportJobResponse snapshot = toResponse(job);
        booksImportProgressEmitterService.publish(snapshot, isTerminal(job.getStatus()));
    }

    private boolean isTerminal(BooksImportJobStatus status) {
        return TERMINAL_STATUSES.contains(status);
    }

    private RowMapper<BooksImportBatchFileResponse> importBatchFileRowMapper() {
        return (rs, rowNum) -> new BooksImportBatchFileResponse(
                rs.getLong("id"),
                rs.getString("batch_id"),
                rs.getString("file_name"),
                rs.getString("dictionary_name"),
                BooksImportBatchFileStatus.valueOf(rs.getString("status")),
                rs.getLong("row_count"),
                rs.getLong("success_rows"),
                rs.getLong("failed_rows"),
                nullableLong(rs, "duration_ms"),
                rs.getString("error_message"),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private Map<String, Object> mapConflictRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("candidateId", nullableLong(rs, "candidate_id"));
        row.put("normalizedWord", rs.getString("normalized_word"));
        row.put("displayWord", rs.getString("display_word"));
        row.put("conflictType", rs.getString("conflict_type"));
        row.put("dictionaryNames", rs.getString("dictionary_names"));
        row.put("existingPayload", parsePayload(rs.getString("existing_payload")));
        row.put("importedPayload", parsePayload(rs.getString("imported_payload")));
        row.put("resolvedPayload", parsePayload(rs.getString("resolved_payload")));
        row.put("comment", rs.getString("comment"));
        row.put("resolvedAt", rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toLocalDateTime());
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value == null ? null : (Map<String, Object>) value;
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to parse import payload: " + ex.getMessage());
        }
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to serialize import payload: " + ex.getMessage());
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isJsonFile(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }

    private int estimateDifficulty(String category) {
        return switch (category) {
            case "小学", "初中", "中考" -> 1;
            case "高中", "四级", "高考" -> 2;
            case "六级", "考研", "雅思", "托福" -> 3;
            case "GRE", "GMAT", "考博", "SAT" -> 4;
            default -> 2;
        };
    }

    private String extractDefinition(List<PartOfSpeechDto> partsOfSpeech) {
        if (partsOfSpeech == null || partsOfSpeech.isEmpty()) {
            return null;
        }
        for (PartOfSpeechDto partOfSpeech : partsOfSpeech) {
            if (partOfSpeech == null || partOfSpeech.getDefinitions() == null) {
                continue;
            }
            for (com.example.words.dto.DefinitionDto definition : partOfSpeech.getDefinitions()) {
                if (definition == null) {
                    continue;
                }
                String translation = trimToNull(definition.getTranslation());
                if (translation != null) {
                    return translation;
                }
                String rawDefinition = trimToNull(definition.getDefinition());
                if (rawDefinition != null) {
                    return rawDefinition;
                }
            }
        }
        return null;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private <E extends Enum<E>> E nullableEnum(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private List<String> splitDictionaryNames(String dictionaryNames) {
        if (dictionaryNames == null || dictionaryNames.isBlank()) {
            return List.of();
        }
        return Arrays.stream(dictionaryNames.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private boolean valuesDiffer(String left, String right) {
        if (right == null || right.isBlank()) {
            return false;
        }
        if (left == null || left.isBlank()) {
            return false;
        }
        return !left.equals(right);
    }

    private boolean jsonValuesDiffer(String left, String right) {
        if (right == null || right.isBlank()) {
            return false;
        }
        if (left == null || left.isBlank()) {
            return false;
        }
        try {
            JsonNode leftNode = objectMapper.readTree(left);
            JsonNode rightNode = objectMapper.readTree(right);
            return !leftNode.equals(rightNode);
        } catch (IOException ex) {
            return !left.equals(right);
        }
    }

    private void setNullableString(PreparedStatement ps, int parameterIndex, String value) throws SQLException {
        if (value == null) {
            ps.setNull(parameterIndex, Types.VARCHAR);
        } else {
            ps.setString(parameterIndex, value);
        }
    }

    private record StageRow(
            String batchId,
            String fileName,
            String dictionaryName,
            String category,
            long sourceRowNo,
            int entryOrder,
            String word,
            String normalizedWord,
            String definition,
            Integer difficulty,
            String phoneticDetailJson,
            String partOfSpeechDetailJson,
            String rawPayloadJson) {
    }

    private record StageAggregateRow(
            String dictionaryName,
            String normalizedWord,
            String word,
            String definition,
            Integer difficulty,
            String phoneticDetailJson,
            String partOfSpeechDetailJson) {
    }

    private record CandidateInsertRow(
            String batchId,
            String normalizedWord,
            String displayWord,
            String definition,
            Integer difficulty,
            String phoneticDetailJson,
            String partOfSpeechDetailJson,
            int sourceCount,
            ImportMetaWordCandidateStatus status,
            Long matchedMetaWordId,
            ImportResolutionSource resolutionSource) {
    }

    private record ConflictInsertRow(
            String batchId,
            String normalizedWord,
            String displayWord,
            ImportConflictType conflictType,
            String dictionaryNames,
            Long existingMetaWordId,
            String existingPayloadJson,
            String importedPayloadJson) {
    }

    private record ExistingMetaWordSnapshot(
            Long id,
            String word,
            String definition,
            Integer difficulty,
            String phoneticDetailJson,
            String partOfSpeechDetailJson) {
    }

    private record ResolvedDictionaryEntry(
            String dictionaryName,
            String category,
            String normalizedWord,
            int firstEntryOrder,
            Long metaWordId) {
    }

    private record DictionaryPublishSource(
            String dictionaryName,
            String category,
            String fileName,
            Long fileSize) {
    }

    private record FileStageResult(
            boolean success,
            long totalRows,
            long successRows,
            long failedRows,
            String errorMessage) {
    }

    private record CandidateDecision(
            String normalizedWord,
            String displayWord,
            String definition,
            Integer difficulty,
            String phoneticDetailJson,
            String partOfSpeechDetailJson,
            int sourceCount,
            ImportMetaWordCandidateStatus status,
            ImportConflictType conflictType,
            String existingPayloadJson,
            String importedPayloadJson) {
    }

    private interface ImportLockHandle extends AutoCloseable {

        @Override
        void close() throws Exception;
    }

    private final class CandidateAccumulator {

        private final String normalizedWord;
        private final Set<String> dictionaryNames = new LinkedHashSet<>();
        private final Set<String> words = new LinkedHashSet<>();
        private final Set<String> definitions = new LinkedHashSet<>();
        private final Set<Integer> difficulties = new LinkedHashSet<>();
        private final Set<String> phoneticValues = new LinkedHashSet<>();
        private final Set<String> partOfSpeechValues = new LinkedHashSet<>();
        private int sourceCount;

        private CandidateAccumulator(String normalizedWord) {
            this.normalizedWord = normalizedWord;
        }

        public void add(StageAggregateRow row) {
            this.sourceCount++;
            this.dictionaryNames.add(row.dictionaryName());
            if (row.word() != null && !row.word().isBlank()) {
                this.words.add(row.word());
            }
            if (row.definition() != null && !row.definition().isBlank()) {
                this.definitions.add(row.definition());
            }
            if (row.difficulty() != null) {
                this.difficulties.add(row.difficulty());
            }
            if (row.phoneticDetailJson() != null && !row.phoneticDetailJson().isBlank()) {
                this.phoneticValues.add(row.phoneticDetailJson());
            }
            if (row.partOfSpeechDetailJson() != null && !row.partOfSpeechDetailJson().isBlank()) {
                this.partOfSpeechValues.add(row.partOfSpeechDetailJson());
            }
        }

        public String normalizedWord() {
            return normalizedWord;
        }

        public int sourceCount() {
            return sourceCount;
        }

        public Set<String> dictionaryNames() {
            return dictionaryNames;
        }

        public CandidateDecision decide(ExistingMetaWordSnapshot existing) {
            String displayWord = words.stream().findFirst().orElse(normalizedWord);
            String definition = definitions.stream().findFirst().orElse(null);
            Integer difficulty = difficulties.stream().findFirst().orElse(null);
            String phoneticDetailJson = phoneticValues.stream().findFirst().orElse(null);
            String partOfSpeechDetailJson = partOfSpeechValues.stream().findFirst().orElse(null);

            boolean multiSourceConflict = definitions.size() > 1
                    || difficulties.size() > 1
                    || phoneticValues.size() > 1
                    || partOfSpeechValues.size() > 1;

            Map<String, Object> importedPayload = new LinkedHashMap<>();
            importedPayload.put("word", displayWord);
            importedPayload.put("definition", definition);
            importedPayload.put("difficulty", difficulty);
            importedPayload.put("phoneticDetail", parseJsonValue(phoneticDetailJson));
            importedPayload.put("partOfSpeechDetail", parseJsonValue(partOfSpeechDetailJson));
            importedPayload.put("dictionaryNames", dictionaryNames);
            importedPayload.put("sourceCount", sourceCount);
            importedPayload.put("sourceWords", words);
            importedPayload.put("sourceDefinitions", definitions);

            if (existing == null) {
                return new CandidateDecision(
                        normalizedWord,
                        displayWord,
                        definition,
                        difficulty,
                        phoneticDetailJson,
                        partOfSpeechDetailJson,
                        sourceCount,
                        multiSourceConflict ? ImportMetaWordCandidateStatus.PENDING_REVIEW : ImportMetaWordCandidateStatus.AUTO_CREATE,
                        multiSourceConflict ? ImportConflictType.MULTI_SOURCE_CONFLICT : null,
                        null,
                        toJsonString(importedPayload)
                );
            }

            boolean existingConflict = valuesDiffer(existing.definition(), definition)
                    || (existing.difficulty() != null && difficulty != null && !Objects.equals(existing.difficulty(), difficulty))
                    || jsonValuesDiffer(existing.phoneticDetailJson(), phoneticDetailJson)
                    || jsonValuesDiffer(existing.partOfSpeechDetailJson(), partOfSpeechDetailJson);

            Map<String, Object> existingPayload = new LinkedHashMap<>();
            existingPayload.put("word", existing.word());
            existingPayload.put("definition", existing.definition());
            existingPayload.put("difficulty", existing.difficulty());
            existingPayload.put("phoneticDetail", parseJsonValue(existing.phoneticDetailJson()));
            existingPayload.put("partOfSpeechDetail", parseJsonValue(existing.partOfSpeechDetailJson()));

            if (multiSourceConflict || existingConflict) {
                ImportConflictType conflictType = multiSourceConflict
                        ? ImportConflictType.MULTI_SOURCE_CONFLICT
                        : ImportConflictType.FIELD_CONFLICT;
                return new CandidateDecision(
                        normalizedWord,
                        displayWord,
                        definition,
                        difficulty,
                        phoneticDetailJson,
                        partOfSpeechDetailJson,
                        sourceCount,
                        ImportMetaWordCandidateStatus.PENDING_REVIEW,
                        conflictType,
                        toJsonString(existingPayload),
                        toJsonString(importedPayload)
                );
            }

            return new CandidateDecision(
                    normalizedWord,
                    existing.word() != null ? existing.word() : displayWord,
                    existing.definition() == null ? definition : existing.definition(),
                    existing.difficulty() == null ? difficulty : existing.difficulty(),
                    existing.phoneticDetailJson() == null ? phoneticDetailJson : existing.phoneticDetailJson(),
                    existing.partOfSpeechDetailJson() == null ? partOfSpeechDetailJson : existing.partOfSpeechDetailJson(),
                    sourceCount,
                    ImportMetaWordCandidateStatus.AUTO_UPDATE,
                    null,
                    toJsonString(existingPayload),
                    toJsonString(importedPayload)
            );
        }

        private Object parseJsonValue(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                return objectMapper.readValue(json, Object.class);
            } catch (IOException ex) {
                return json;
            }
        }
    }
}
