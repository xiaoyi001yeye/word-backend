package com.example.words.repository;

import com.example.words.model.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {

    Optional<Dictionary> findByName(String name);

    boolean existsByName(String name);

    List<Dictionary> findByCategory(String category);
}
