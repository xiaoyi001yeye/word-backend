-- Database initialization script for word_vocabulary table
-- This script creates the table if it does not exist, preserving existing data

-- Create sequence for auto-increment ID
CREATE SEQUENCE IF NOT EXISTS word_vocabulary_id_seq START WITH 1 INCREMENT BY 1;

-- Create word_vocabulary table if it does not exist
CREATE TABLE IF NOT EXISTS word_vocabulary (
    id BIGINT DEFAULT nextval('word_vocabulary_id_seq') PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000),
    file_size BIGINT,
    category VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on file_name for faster lookups
CREATE INDEX IF NOT EXISTS idx_word_vocabulary_file_name ON word_vocabulary(file_name);

-- Create index on category for faster category queries
CREATE INDEX IF NOT EXISTS idx_word_vocabulary_category ON word_vocabulary(category);

-- Create unique constraint on file_name to prevent duplicates
-- Note: This may fail if constraint already exists, but that's acceptable

-- Comment on the table
COMMENT ON TABLE word_vocabulary IS 'Vocabulary file metadata table';

-- Comment on columns
COMMENT ON COLUMN word_vocabulary.id IS 'Primary key';
COMMENT ON COLUMN word_vocabulary.file_name IS 'CSV file name';
COMMENT ON COLUMN word_vocabulary.file_path IS 'Full path to the CSV file';
COMMENT ON COLUMN word_vocabulary.file_size IS 'File size in bytes';
COMMENT ON COLUMN word_vocabulary.category IS 'Category: 高考, 考研, 四级, 六级, 雅思, 托福, GRE, etc.';
COMMENT ON COLUMN word_vocabulary.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN word_vocabulary.updated_at IS 'Record last update timestamp';
