CREATE SEQUENCE IF NOT EXISTS tags_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS tags (
    id BIGINT DEFAULT nextval('tags_id_seq') PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    dictionary_id BIGINT,
    parent_id BIGINT,
    sort_order INT NOT NULL DEFAULT 1,
    path_name TEXT NOT NULL DEFAULT '',
    path_key TEXT NOT NULL DEFAULT '',
    sort_key TEXT NOT NULL DEFAULT '',
    level INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tags_dictionary FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE CASCADE,
    CONSTRAINT fk_tags_parent FOREIGN KEY (parent_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tags_dictionary_type ON tags(dictionary_id, type);
CREATE INDEX IF NOT EXISTS idx_tags_parent ON tags(parent_id);
CREATE INDEX IF NOT EXISTS idx_tags_sort_key ON tags(sort_key);

CREATE SEQUENCE IF NOT EXISTS tag_relations_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS tag_relations (
    id BIGINT DEFAULT nextval('tag_relations_id_seq') PRIMARY KEY,
    tag_id BIGINT NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id BIGINT NOT NULL,
    relation_role VARCHAR(50) NOT NULL DEFAULT 'TAGGED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tag_relations_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tag_relations_tag ON tag_relations(tag_id);
CREATE INDEX IF NOT EXISTS idx_tag_relations_resource ON tag_relations(resource_type, resource_id);

ALTER TABLE dictionary_words
    ADD COLUMN IF NOT EXISTS chapter_tag_id BIGINT;

ALTER TABLE dictionary_words
    ADD COLUMN IF NOT EXISTS entry_order INT NOT NULL DEFAULT 1;

ALTER TABLE dictionary_words
    DROP CONSTRAINT IF EXISTS fk_dictionary_words_chapter_tag;

ALTER TABLE dictionary_words
    ADD CONSTRAINT fk_dictionary_words_chapter_tag
        FOREIGN KEY (chapter_tag_id) REFERENCES tags(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_dictionary_words_chapter_tag ON dictionary_words(chapter_tag_id);
CREATE INDEX IF NOT EXISTS idx_dictionary_words_dictionary_meta_word ON dictionary_words(dictionary_id, meta_word_id);

ALTER TABLE dictionaries
    ADD COLUMN IF NOT EXISTS entry_count INT DEFAULT 0;

UPDATE dictionaries
SET entry_count = COALESCE(word_count, 0)
WHERE entry_count IS NULL OR entry_count = 0;

INSERT INTO tags (name, type, dictionary_id, parent_id, sort_order, path_name, path_key, sort_key, level)
SELECT '默认章节',
       'CHAPTER',
       d.id,
       NULL,
       1,
       '默认章节',
       'default-' || d.id,
       '000001',
       1
FROM dictionaries d
WHERE NOT EXISTS (
    SELECT 1
    FROM tags t
    WHERE t.dictionary_id = d.id
      AND t.type = 'CHAPTER'
      AND t.parent_id IS NULL
      AND t.name = '默认章节'
);

WITH ordered_entries AS (
    SELECT dw.id,
           dw.dictionary_id,
           ROW_NUMBER() OVER (PARTITION BY dw.dictionary_id ORDER BY dw.id) AS rn
    FROM dictionary_words dw
)
UPDATE dictionary_words dw
SET chapter_tag_id = t.id,
    entry_order = oe.rn
FROM ordered_entries oe
JOIN tags t ON t.dictionary_id = oe.dictionary_id
           AND t.type = 'CHAPTER'
           AND t.parent_id IS NULL
           AND t.name = '默认章节'
WHERE dw.id = oe.id
  AND (dw.chapter_tag_id IS NULL OR dw.entry_order IS NULL OR dw.entry_order = 1);

ALTER TABLE dictionary_words DROP CONSTRAINT IF EXISTS uk_dictionary_words_relation;
DROP INDEX IF EXISTS idx_dictionary_words_unique_relation;

CREATE UNIQUE INDEX IF NOT EXISTS idx_dictionary_words_chapter_entry
    ON dictionary_words (dictionary_id, chapter_tag_id, entry_order);
