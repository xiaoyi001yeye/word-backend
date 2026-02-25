-- Add JSONB fields to meta_words table for new nested structure
-- V4__add_jsonb_fields_to_meta_words.sql

ALTER TABLE meta_words 
ADD COLUMN IF NOT EXISTS phonetic_detail JSONB,
ADD COLUMN IF NOT EXISTS part_of_speech_detail JSONB;

-- Create indexes for JSONB fields to improve query performance
CREATE INDEX IF NOT EXISTS idx_meta_words_phonetic_detail ON meta_words USING GIN (phonetic_detail);
CREATE INDEX IF NOT EXISTS idx_meta_words_part_of_speech_detail ON meta_words USING GIN (part_of_speech_detail);

-- Add comments for new columns
COMMENT ON COLUMN meta_words.phonetic_detail IS 'Detailed phonetic information with UK/US pronunciations';
COMMENT ON COLUMN meta_words.part_of_speech_detail IS 'Detailed part of speech information with definitions, examples, etc.';