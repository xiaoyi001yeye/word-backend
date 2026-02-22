package com.example.words.repository;

import com.example.words.model.WordVocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordVocabularyRepository extends JpaRepository<WordVocabulary, Long> {

    Optional<WordVocabulary> findByFileName(String fileName);

    List<WordVocabulary> findByCategory(String category);

    boolean existsByFileName(String fileName);
}
