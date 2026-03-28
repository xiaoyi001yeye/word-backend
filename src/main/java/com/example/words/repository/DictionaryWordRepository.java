package com.example.words.repository;

import com.example.words.model.DictionaryWord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DictionaryWordRepository extends JpaRepository<DictionaryWord, Long> {

    List<DictionaryWord> findByDictionaryId(Long dictionaryId);

    @Query("""
            SELECT dw
            FROM DictionaryWord dw
            LEFT JOIN Tag t ON dw.chapterTagId = t.id
            WHERE dw.dictionaryId = :dictionaryId
            ORDER BY COALESCE(t.sortKey, ''), dw.entryOrder, dw.id
            """)
    List<DictionaryWord> findByDictionaryIdOrderByDisplayOrder(@Param("dictionaryId") Long dictionaryId);

    @Query("""
            SELECT dw
            FROM DictionaryWord dw
            LEFT JOIN Tag t ON dw.chapterTagId = t.id
            WHERE dw.dictionaryId = :dictionaryId
            ORDER BY COALESCE(t.sortKey, ''), dw.entryOrder, dw.id
            """)
    List<DictionaryWord> findByDictionaryIdOrderByIdAsc(@Param("dictionaryId") Long dictionaryId);

    @Query(value = """
            SELECT dw
            FROM DictionaryWord dw
            LEFT JOIN Tag t ON dw.chapterTagId = t.id
            WHERE dw.dictionaryId = :dictionaryId
            ORDER BY COALESCE(t.sortKey, ''), dw.entryOrder, dw.id
            """,
            countQuery = "SELECT COUNT(dw) FROM DictionaryWord dw WHERE dw.dictionaryId = :dictionaryId")
    Page<DictionaryWord> findPageByDictionaryIdOrderByDisplayOrder(@Param("dictionaryId") Long dictionaryId, Pageable pageable);

    @Query(value = """
            SELECT dw
            FROM DictionaryWord dw
            JOIN MetaWord m ON dw.metaWordId = m.id
            LEFT JOIN Tag t ON dw.chapterTagId = t.id
            WHERE dw.dictionaryId = :dictionaryId
              AND LOWER(m.word) LIKE CONCAT(LOWER(:keyword), '%')
            ORDER BY COALESCE(t.sortKey, ''), dw.entryOrder, dw.id
            """,
            countQuery = """
                    SELECT COUNT(dw)
                    FROM DictionaryWord dw
                    JOIN MetaWord m ON dw.metaWordId = m.id
                    WHERE dw.dictionaryId = :dictionaryId
                      AND LOWER(m.word) LIKE CONCAT(LOWER(:keyword), '%')
                    """)
    Page<DictionaryWord> findPageByDictionaryIdAndKeywordOrderByDisplayOrder(
            @Param("dictionaryId") Long dictionaryId,
            @Param("keyword") String keyword,
            Pageable pageable);

    List<DictionaryWord> findByMetaWordId(Long metaWordId);

    Optional<DictionaryWord> findByDictionaryIdAndMetaWordId(Long dictionaryId, Long metaWordId);

    boolean existsByDictionaryIdAndMetaWordId(Long dictionaryId, Long metaWordId);

    long countByDictionaryId(Long dictionaryId);

    long countByChapterTagId(Long chapterTagId);

    @Query("SELECT COUNT(DISTINCT dw.metaWordId) FROM DictionaryWord dw WHERE dw.dictionaryId = :dictionaryId")
    long countDistinctMetaWordIdByDictionaryId(@Param("dictionaryId") Long dictionaryId);

    @Query("SELECT COALESCE(MAX(dw.entryOrder), 0) FROM DictionaryWord dw WHERE dw.dictionaryId = :dictionaryId AND dw.chapterTagId = :chapterTagId")
    Integer findMaxEntryOrderByDictionaryIdAndChapterTagId(
            @Param("dictionaryId") Long dictionaryId,
            @Param("chapterTagId") Long chapterTagId);

    void deleteByDictionaryId(Long dictionaryId);
}
