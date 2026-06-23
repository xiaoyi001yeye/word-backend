CREATE TABLE IF NOT EXISTS student_word_memories (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    meta_word_id BIGINT NOT NULL,
    box_level INT NOT NULL DEFAULT 0,
    mastery_level NUMERIC(5, 2) NOT NULL DEFAULT 0,
    next_review_date DATE,
    correct_times INT NOT NULL DEFAULT 0,
    wrong_times INT NOT NULL DEFAULT 0,
    correct_streak INT NOT NULL DEFAULT 0,
    last_result VARCHAR(32),
    last_source VARCHAR(32),
    auto_wrong BOOLEAN NOT NULL DEFAULT FALSE,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    last_studied_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_word_memories_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_word_memories_meta_word FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_student_word_memories_student_id ON student_word_memories(student_id);
CREATE INDEX IF NOT EXISTS idx_student_word_memories_auto_wrong ON student_word_memories(student_id, auto_wrong);
CREATE INDEX IF NOT EXISTS idx_student_word_memories_favorite ON student_word_memories(student_id, favorite);
CREATE INDEX IF NOT EXISTS idx_student_word_memories_next_review_date ON student_word_memories(next_review_date);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_student_word_memories_student_word') THEN
        ALTER TABLE student_word_memories
            ADD CONSTRAINT uk_student_word_memories_student_word UNIQUE (student_id, meta_word_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS student_word_memory_events (
    id BIGSERIAL PRIMARY KEY,
    student_word_memory_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    meta_word_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT,
    dictionary_id BIGINT,
    result VARCHAR(32) NOT NULL,
    box_level_before INT NOT NULL,
    box_level_after INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_word_memory_events_memory FOREIGN KEY (student_word_memory_id)
        REFERENCES student_word_memories(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_word_memory_events_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_word_memory_events_meta_word FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_word_memory_events_dictionary FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_student_word_memory_events_student_id ON student_word_memory_events(student_id);
CREATE INDEX IF NOT EXISTS idx_student_word_memory_events_memory_id ON student_word_memory_events(student_word_memory_id);
CREATE INDEX IF NOT EXISTS idx_student_word_memory_events_source ON student_word_memory_events(source_type, source_id);
