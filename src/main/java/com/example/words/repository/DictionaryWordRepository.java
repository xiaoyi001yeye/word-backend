package com.example.words.repository;

import com.example.words.model.DictionaryWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DictionaryWordRepository extends JpaRepository<DictionaryWord, Long> {

    List<DictionaryWord> findByDictionaryId(Long dictionaryId);

    List<DictionaryWord> findByMetaWordId(Long metaWordId);

    Optional<DictionaryWord> findByDictionaryIdAndMetaWordId(Long dictionaryId, Long metaWordId);

    boolean existsByDictionaryIdAndMetaWordId(Long dictionaryId, Long metaWordId);

    void deleteByDictionaryId(Long dictionaryId);
}
