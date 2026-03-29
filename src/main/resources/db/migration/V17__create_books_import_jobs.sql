CREATE TABLE IF NOT EXISTS books_import_jobs (
    id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    total_files INT NOT NULL DEFAULT 0,
    processed_files INT NOT NULL DEFAULT 0,
    failed_files INT NOT NULL DEFAULT 0,
    imported_dictionary_count INT NOT NULL DEFAULT 0,
    imported_word_count BIGINT NOT NULL DEFAULT 0,
    current_file VARCHAR(500),
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_books_import_jobs_status
    ON books_import_jobs(status);

CREATE INDEX IF NOT EXISTS idx_books_import_jobs_created_at
    ON books_import_jobs(created_at DESC);
