ALTER TABLE meta_words
    ADD COLUMN IF NOT EXISTS learning_detail JSONB;

COMMENT ON COLUMN meta_words.learning_detail IS
    'Markdown teaching material and AI-generated learning extensions';
