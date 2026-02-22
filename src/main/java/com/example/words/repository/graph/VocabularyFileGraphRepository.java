package com.example.words.repository.graph;

import com.example.words.model.graph.VocabularyFileNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VocabularyFileGraphRepository extends Neo4jRepository<VocabularyFileNode, Long> {

    Optional<VocabularyFileNode> findByFileName(String fileName);

    boolean existsByFileName(String fileName);

    @Query("MATCH (v:VocabularyFile) WHERE v.category = $category RETURN v")
    Iterable<VocabularyFileNode> findByCategory(String category);

    @Query("MATCH (v:VocabularyFile) RETURN v")
    Iterable<VocabularyFileNode> findAllFiles();
}
