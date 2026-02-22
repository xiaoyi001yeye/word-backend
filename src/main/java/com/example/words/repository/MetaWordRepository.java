package com.example.words.repository;

import com.example.words.model.MetaWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetaWordRepository extends JpaRepository<MetaWord, Long> {

    Optional<MetaWord> findByWord(String word);

    boolean existsByWord(String word);

    List<MetaWord> findByDifficulty(Integer difficulty);

    List<MetaWord> findByWordStartingWith(String prefix);

    @Query(value = "SELECT m.* FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId) LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<MetaWord> findByDictionaryIdWithPagination(@Param("dictionaryId") Long dictionaryId, @Param("limit") int limit, @Param("offset") int offset);

    Page<MetaWord> findByWordStartingWith(String prefix, Pageable pageable);

    @Query(value = "SELECT m.* FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId) AND m.word LIKE CONCAT(:keyword, '%')",
           countQuery = "SELECT COUNT(*) FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId) AND m.word LIKE CONCAT(:keyword, '%')",
           nativeQuery = true)
    Page<MetaWord> findByDictionaryIdAndWordStartingWith(@Param("dictionaryId") Long dictionaryId, @Param("keyword") String keyword, Pageable pageable);

    @Query(value = "SELECT m.* FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId)",
           countQuery = "SELECT COUNT(*) FROM meta_words m WHERE m.id IN (SELECT dw.meta_word_id FROM dictionary_words dw WHERE dw.dictionary_id = :dictionaryId)",
           nativeQuery = true)
    Page<MetaWord> findByDictionaryId(@Param("dictionaryId") Long dictionaryId, Pageable pageable);
}
