ALTER TABLE books_import_jobs
    ADD COLUMN IF NOT EXISTS batch_type VARCHAR(32) NOT NULL DEFAULT 'BOOKS_FULL',
    ADD COLUMN IF NOT EXISTS total_rows BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS processed_rows BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS success_rows BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failed_rows BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS candidate_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS conflict_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS publish_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS publish_finished_at TIMESTAMP;

UPDATE books_import_jobs
SET status = 'STAGING'
WHERE status = 'RUNNING';

CREATE TABLE IF NOT EXISTS import_batch_files (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    dictionary_name VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    file_size BIGINT,
    row_count BIGINT NOT NULL DEFAULT 0,
    success_rows BIGINT NOT NULL DEFAULT 0,
    failed_rows BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_batch_files_batch
        FOREIGN KEY (batch_id) REFERENCES books_import_jobs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_import_batch_files_batch_id
    ON import_batch_files(batch_id);

CREATE INDEX IF NOT EXISTS idx_import_batch_files_batch_status
    ON import_batch_files(batch_id, status);

CREATE TABLE IF NOT EXISTS book_import_stage (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    dictionary_name VARCHAR(500) NOT NULL,
    category VARCHAR(100),
    source_row_no BIGINT NOT NULL,
    entry_order INT NOT NULL,
    word TEXT NOT NULL,
    normalized_word TEXT NOT NULL,
    definition TEXT,
    difficulty INT,
    phonetic_detail JSONB,
    part_of_speech_detail JSONB,
    raw_payload JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_book_import_stage_batch
        FOREIGN KEY (batch_id) REFERENCES books_import_jobs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_book_import_stage_batch_word
    ON book_import_stage(batch_id, normalized_word);

CREATE INDEX IF NOT EXISTS idx_book_import_stage_batch_dictionary
    ON book_import_stage(batch_id, dictionary_name);

CREATE TABLE IF NOT EXISTS import_meta_word_candidates (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    normalized_word TEXT NOT NULL,
    display_word TEXT,
    definition TEXT,
    difficulty INT,
    phonetic_detail JSONB,
    part_of_speech_detail JSONB,
    source_count INT NOT NULL DEFAULT 0,
    merge_status VARCHAR(32) NOT NULL,
    matched_meta_word_id BIGINT,
    resolution_source VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_candidates_batch
        FOREIGN KEY (batch_id) REFERENCES books_import_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_import_candidates_meta_word
        FOREIGN KEY (matched_meta_word_id) REFERENCES meta_words(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_import_candidates_batch_word
    ON import_meta_word_candidates(batch_id, normalized_word);

CREATE INDEX IF NOT EXISTS idx_import_candidates_batch_status
    ON import_meta_word_candidates(batch_id, merge_status);

CREATE TABLE IF NOT EXISTS import_conflicts (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    candidate_id BIGINT,
    normalized_word TEXT NOT NULL,
    display_word TEXT,
    conflict_type VARCHAR(32) NOT NULL,
    dictionary_names TEXT,
    existing_meta_word_id BIGINT,
    existing_payload JSONB,
    imported_payload JSONB,
    resolution VARCHAR(32),
    resolved_payload JSONB,
    resolved_by BIGINT,
    resolved_at TIMESTAMP,
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_conflicts_batch
        FOREIGN KEY (batch_id) REFERENCES books_import_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_import_conflicts_candidate
        FOREIGN KEY (candidate_id) REFERENCES import_meta_word_candidates(id) ON DELETE CASCADE,
    CONSTRAINT fk_import_conflicts_meta_word
        FOREIGN KEY (existing_meta_word_id) REFERENCES meta_words(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_import_conflicts_batch_id
    ON import_conflicts(batch_id);

CREATE INDEX IF NOT EXISTS idx_import_conflicts_batch_resolution
    ON import_conflicts(batch_id, resolution);

CREATE TABLE IF NOT EXISTS import_publish_logs (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    dictionary_id BIGINT,
    dictionary_name VARCHAR(500) NOT NULL,
    published_entry_count BIGINT NOT NULL DEFAULT 0,
    published_word_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    CONSTRAINT fk_import_publish_logs_batch
        FOREIGN KEY (batch_id) REFERENCES books_import_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_import_publish_logs_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_import_publish_logs_batch_id
    ON import_publish_logs(batch_id);

ALTER TABLE meta_words
    ADD COLUMN IF NOT EXISTS normalized_word TEXT;

UPDATE meta_words
SET normalized_word = LOWER(TRIM(word))
WHERE normalized_word IS NULL;

CREATE INDEX IF NOT EXISTS idx_meta_words_normalized_word
    ON meta_words(normalized_word);

CREATE TEMP TABLE meta_word_duplicate_map AS
SELECT id AS duplicate_id,
       MIN(id) OVER (PARTITION BY normalized_word) AS canonical_id
FROM meta_words
WHERE normalized_word IS NOT NULL;

DELETE FROM meta_word_duplicate_map
WHERE duplicate_id = canonical_id;

DELETE FROM study_day_task_items s
USING meta_word_duplicate_map dm
WHERE s.meta_word_id = dm.duplicate_id
  AND EXISTS (
      SELECT 1
      FROM study_day_task_items keep
      WHERE keep.study_day_task_id = s.study_day_task_id
        AND keep.meta_word_id = dm.canonical_id
  );

DELETE FROM study_word_progresses s
USING meta_word_duplicate_map dm
WHERE s.meta_word_id = dm.duplicate_id
  AND EXISTS (
      SELECT 1
      FROM study_word_progresses keep
      WHERE keep.student_study_plan_id = s.student_study_plan_id
        AND keep.meta_word_id = dm.canonical_id
  );

UPDATE dictionary_words dw
SET meta_word_id = dm.canonical_id
FROM meta_word_duplicate_map dm
WHERE dw.meta_word_id = dm.duplicate_id;

UPDATE exam_questions eq
SET meta_word_id = dm.canonical_id
FROM meta_word_duplicate_map dm
WHERE eq.meta_word_id = dm.duplicate_id;

UPDATE study_day_task_items s
SET meta_word_id = dm.canonical_id
FROM meta_word_duplicate_map dm
WHERE s.meta_word_id = dm.duplicate_id;

UPDATE study_word_progresses s
SET meta_word_id = dm.canonical_id
FROM meta_word_duplicate_map dm
WHERE s.meta_word_id = dm.duplicate_id;

UPDATE study_records sr
SET meta_word_id = dm.canonical_id
FROM meta_word_duplicate_map dm
WHERE sr.meta_word_id = dm.duplicate_id;

DELETE FROM meta_words m
USING meta_word_duplicate_map dm
WHERE m.id = dm.duplicate_id;

DROP TABLE meta_word_duplicate_map;

ALTER TABLE meta_words
    ALTER COLUMN normalized_word SET NOT NULL;

ALTER TABLE meta_words
    DROP CONSTRAINT IF EXISTS uk_meta_words_word;

DROP INDEX IF EXISTS idx_meta_words_word;

CREATE INDEX IF NOT EXISTS idx_meta_words_word
    ON meta_words(word);

CREATE UNIQUE INDEX IF NOT EXISTS uk_meta_words_normalized_word
    ON meta_words(normalized_word);
