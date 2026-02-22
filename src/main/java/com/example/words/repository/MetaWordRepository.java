package com.example.words.repository;

import com.example.words.model.MetaWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetaWordRepository extends JpaRepository<MetaWord, Long> {

    Optional<MetaWord> findByWord(String word);

    boolean existsByWord(String word);

    List<MetaWord> findByDifficulty(Integer difficulty);

    List<MetaWord> findByWordStartingWith(String prefix);
}
