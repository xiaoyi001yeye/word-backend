package com.example.words.repository.graph;

import com.example.words.model.graph.CategoryNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryGraphRepository extends Neo4jRepository<CategoryNode, Long> {

    Optional<CategoryNode> findByName(String name);
}
