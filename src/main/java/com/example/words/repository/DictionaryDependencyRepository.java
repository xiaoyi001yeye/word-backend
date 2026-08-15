package com.example.words.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DictionaryDependencyRepository {

    private static final String BLOCKING_REFERENCE_SQL = """
            SELECT reference_type
            FROM (
                SELECT '学生词书分配' AS reference_type,
                       EXISTS (SELECT 1 FROM dictionary_assignments WHERE dictionary_id = ?) AS referenced
                UNION ALL
                SELECT '班级词书分配',
                       EXISTS (SELECT 1 FROM classroom_dictionary_assignments WHERE dictionary_id = ?)
                UNION ALL
                SELECT '学习计划',
                       EXISTS (SELECT 1 FROM study_plans WHERE dictionary_id = ?)
                UNION ALL
                SELECT '历史测验',
                       EXISTS (SELECT 1 FROM exams WHERE dictionary_id = ?)
                UNION ALL
                SELECT '题库试题',
                       EXISTS (SELECT 1 FROM question_bank_items WHERE dictionary_id = ?)
                UNION ALL
                SELECT '题目导入预览',
                       EXISTS (SELECT 1 FROM question_import_preview_rows WHERE dictionary_id = ?)
                UNION ALL
                SELECT '试卷模板',
                       EXISTS (SELECT 1 FROM paper_template_questions WHERE dictionary_id = ?)
                UNION ALL
                SELECT '试卷发布记录',
                       EXISTS (SELECT 1 FROM paper_release_questions WHERE dictionary_id = ?)
            ) references_by_type
            WHERE referenced = TRUE
            """;

    private final JdbcTemplate jdbcTemplate;

    public DictionaryDependencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findBlockingReferenceTypes(Long dictionaryId) {
        return jdbcTemplate.queryForList(
                BLOCKING_REFERENCE_SQL,
                String.class,
                dictionaryId,
                dictionaryId,
                dictionaryId,
                dictionaryId,
                dictionaryId,
                dictionaryId,
                dictionaryId,
                dictionaryId
        );
    }

    public void deleteOwnedContent(Long dictionaryId) {
        jdbcTemplate.update("DELETE FROM dictionary_words WHERE dictionary_id = ?", dictionaryId);
        jdbcTemplate.update(
                "DELETE FROM tag_relations WHERE tag_id IN (SELECT id FROM tags WHERE dictionary_id = ?)",
                dictionaryId
        );
        jdbcTemplate.update("DELETE FROM tags WHERE dictionary_id = ?", dictionaryId);
    }
}
