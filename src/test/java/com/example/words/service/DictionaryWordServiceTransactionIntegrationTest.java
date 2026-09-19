package com.example.words.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.words.dto.LearningDetailDto;
import com.example.words.dto.MetaWordEntryDtoV2;
import com.example.words.model.LearningDetail;
import com.example.words.model.MetaWord;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.url=jdbc:h2:mem:dictionary_word_ai_tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DictionaryWordService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DictionaryWordServiceTransactionIntegrationTest {

    @Autowired
    private DictionaryWordService dictionaryWordService;

    @Autowired
    private MetaWordRepository metaWordRepository;

    @Autowired
    private DictionaryWordRepository dictionaryWordRepository;

    @MockBean
    private TagService tagService;

    @MockBean
    private DictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        dictionaryWordRepository.deleteAll();
        metaWordRepository.deleteAll();
    }

    @Test
    void saveGeneratedWordShouldRollbackWordAndRelationWhenCountRefreshFails() {
        MetaWord existing = new MetaWord();
        existing.setWord("ability");
        existing.setLearningDetail(new LearningDetail(
                "authoritative material",
                "old hint",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        existing = metaWordRepository.saveAndFlush(existing);
        Long metaWordId = existing.getId();

        when(tagService.getOrCreateDefaultChapterTagId(36L)).thenReturn(7L);
        doThrow(new IllegalStateException("count refresh failed"))
                .when(dictionaryService).updateCounts(anyLong(), anyInt(), anyInt());

        assertThrows(IllegalStateException.class, () -> dictionaryWordService.saveGeneratedWordV2(
                36L,
                metaWordId,
                2L,
                "test-provider",
                "test-model",
                generatedEntry(),
                foundMaterial("new source Markdown", "new material")
        ));

        MetaWord reloaded = metaWordRepository.findById(metaWordId).orElseThrow();
        assertEquals("old hint", reloaded.getLearningDetail().getMemoryHint());
        assertEquals("authoritative material", reloaded.getLearningDetail().getLearningMaterial());
        assertFalse(dictionaryWordRepository.existsByDictionaryIdAndMetaWordId(36L, metaWordId));
    }

    private MetaWordEntryDtoV2 generatedEntry() {
        MetaWordEntryDtoV2 entry = new MetaWordEntryDtoV2();
        entry.setWord("ability");
        entry.setLearningDetail(new LearningDetailDto(
                null,
                "new hint",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
        return entry;
    }

    private LearningMaterialParseResult foundMaterial(String sourceMarkdown, String learningMaterial) {
        return new LearningMaterialParseResult(
                LearningMaterialStatus.FOUND, sourceMarkdown, learningMaterial, "ability.md", 1, 1, List.of());
    }
}
