-- Optional: Drop old columns after successful migration and validation
-- V6__drop_old_columns.sql

-- Only run this after confirming that the new JSONB structure works correctly
-- and all applications have been updated to use the new structure

-- ALTER TABLE meta_words 
-- DROP COLUMN IF EXISTS phonetic,
-- DROP COLUMN IF EXISTS definition,
-- DROP COLUMN IF EXISTS part_of_speech,
-- DROP COLUMN IF EXISTS example_sentence,
-- DROP COLUMN IF EXISTS translation;

-- Note: Keep the old columns for now to maintain backward compatibility
-- We'll drop them in a future version after full migration