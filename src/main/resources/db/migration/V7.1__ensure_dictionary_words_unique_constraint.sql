-- Ensure dictionary_words has a uniqueness guarantee for dictionary_id + meta_word_id
-- This is required by batched ON CONFLICT inserts during rolling imports.

DELETE FROM dictionary_words dw1
USING dictionary_words dw2
WHERE dw1.id > dw2.id
  AND dw1.dictionary_id = dw2.dictionary_id
  AND dw1.meta_word_id = dw2.meta_word_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_dictionary_words_unique_relation
    ON dictionary_words (dictionary_id, meta_word_id);
