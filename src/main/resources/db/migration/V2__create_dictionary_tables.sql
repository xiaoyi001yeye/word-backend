-- Dictionary table (辞书表)
CREATE SEQUENCE IF NOT EXISTS dictionaries_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS dictionaries (
    id BIGINT DEFAULT nextval('dictionaries_id_seq') PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000),
    file_size BIGINT,
    category VARCHAR(100),
    word_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dictionaries_name ON dictionaries(name);
CREATE INDEX IF NOT EXISTS idx_dictionaries_category ON dictionaries(category);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_dictionaries_name') THEN
        ALTER TABLE dictionaries ADD CONSTRAINT uk_dictionaries_name UNIQUE (name);
    END IF;
END $$;

COMMENT ON TABLE dictionaries IS 'Dictionary table - stores scanned CSV file information';
COMMENT ON COLUMN dictionaries.name IS 'Dictionary name (CSV file name)';
COMMENT ON COLUMN dictionaries.file_path IS 'Full path to the CSV file';
COMMENT ON COLUMN dictionaries.file_size IS 'File size in bytes';
COMMENT ON COLUMN dictionaries.category IS 'Category: 高考, 考研, 四级, 六级, 雅思, 托福, GRE, etc.';
COMMENT ON COLUMN dictionaries.word_count IS 'Number of words in the dictionary';

-- MetaWord table (元单词表)
CREATE SEQUENCE IF NOT EXISTS meta_words_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS meta_words (
    id BIGINT DEFAULT nextval('meta_words_id_seq') PRIMARY KEY,
    word VARCHAR(200) NOT NULL,
    phonetic VARCHAR(200),
    definition TEXT,
    part_of_speech VARCHAR(50),
    example_sentence TEXT,
    translation TEXT,
    difficulty INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_meta_words_word ON meta_words(word);
CREATE INDEX IF NOT EXISTS idx_meta_words_difficulty ON meta_words(difficulty);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_meta_words_word') THEN
        ALTER TABLE meta_words ADD CONSTRAINT uk_meta_words_word UNIQUE (word);
    END IF;
END $$;

COMMENT ON TABLE meta_words IS 'Meta word table - stores word metadata';
COMMENT ON COLUMN meta_words.word IS 'The word itself';
COMMENT ON COLUMN meta_words.phonetic IS 'Phonetic transcription';
COMMENT ON COLUMN meta_words.definition IS 'Word definition';
COMMENT ON COLUMN meta_words.part_of_speech IS 'Part of speech: noun, verb, adjective, etc.';
COMMENT ON COLUMN meta_words.example_sentence IS 'Example sentence';
COMMENT ON COLUMN meta_words.translation IS 'Chinese translation';
COMMENT ON COLUMN meta_words.difficulty IS 'Difficulty level: 1-5';

-- Dictionary-Word association table (辞书单词关联表)
CREATE SEQUENCE IF NOT EXISTS dictionary_words_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS dictionary_words (
    id BIGINT DEFAULT nextval('dictionary_words_id_seq') PRIMARY KEY,
    dictionary_id BIGINT NOT NULL,
    meta_word_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE CASCADE,
    FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_dictionary_words_dictionary ON dictionary_words(dictionary_id);
CREATE INDEX IF NOT EXISTS idx_dictionary_words_word ON dictionary_words(meta_word_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_dictionary_words_relation') THEN
        ALTER TABLE dictionary_words ADD CONSTRAINT uk_dictionary_words_relation UNIQUE (dictionary_id, meta_word_id);
    END IF;
END $$;

COMMENT ON TABLE dictionary_words IS 'Dictionary-Word association table';
COMMENT ON COLUMN dictionary_words.dictionary_id IS 'Foreign key to dictionaries';
COMMENT ON COLUMN dictionary_words.meta_word_id IS 'Foreign key to meta_words';
