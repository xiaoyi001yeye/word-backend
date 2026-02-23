package com.example.words.repository;

import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryCreationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {

    Optional<Dictionary> findByName(String name);

    boolean existsByName(String name);

    List<Dictionary> findByCategory(String category);

    @Modifying
    @Query("DELETE FROM Dictionary d WHERE d.creationType = :creationType")
    int deleteByCreationType(@Param("creationType") DictionaryCreationType creationType);
}
