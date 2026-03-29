package com.example.words.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryCreationType;
import com.example.words.model.DictionaryWord;
import com.example.words.model.Tag;
import com.example.words.model.TagType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DictionaryWordRepositoryIntegrationTest {

    @Autowired
    private DictionaryRepository dictionaryRepository;

    @Autowired
    private DictionaryWordRepository dictionaryWordRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                """
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_dictionary_words_chapter_entry
                        ON dictionary_words (dictionary_id, chapter_tag_id, entry_order)
                        """
        );
        jdbcTemplate.execute(
                """
                        CREATE TABLE IF NOT EXISTS meta_words (
                            id BIGINT PRIMARY KEY,
                            word VARCHAR(255) NOT NULL,
                            normalized_word VARCHAR(255) NOT NULL
                        )
                        """
        );
    }

    @Test
    void deleteByDictionaryIdShouldApplyBeforeJdbcInsertInSameTransaction() {
        Dictionary dictionary = dictionaryRepository.saveAndFlush(
                new Dictionary("Imported Dictionary", null, null, "Category", DictionaryCreationType.IMPORTED)
        );

        Tag defaultChapter = new Tag();
        defaultChapter.setName("默认章节");
        defaultChapter.setType(TagType.CHAPTER);
        defaultChapter.setDictionaryId(dictionary.getId());
        defaultChapter = tagRepository.saveAndFlush(defaultChapter);

        long existingWordId = 1001L;
        long replacementWordId = 1002L;
        jdbcTemplate.update(
                "INSERT INTO meta_words (id, word, normalized_word) VALUES (?, ?, ?)",
                existingWordId,
                "alpha",
                "alpha"
        );
        jdbcTemplate.update(
                "INSERT INTO meta_words (id, word, normalized_word) VALUES (?, ?, ?)",
                replacementWordId,
                "beta",
                "beta"
        );

        Long dictionaryId = dictionary.getId();
        Long chapterTagId = defaultChapter.getId();

        dictionaryWordRepository.saveAndFlush(
                new DictionaryWord(dictionaryId, existingWordId, chapterTagId, 1)
        );

        dictionaryWordRepository.deleteByDictionaryId(dictionaryId);

        assertDoesNotThrow(() -> jdbcTemplate.update(
                """
                        INSERT INTO dictionary_words (
                            dictionary_id,
                            meta_word_id,
                            chapter_tag_id,
                            entry_order
                        ) VALUES (?, ?, ?, ?)
                        """,
                dictionaryId,
                replacementWordId,
                chapterTagId,
                1
        ));

        Integer entryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dictionary_words WHERE dictionary_id = ?",
                Integer.class,
                dictionaryId
        );

        assertEquals(1, entryCount);
    }
}
