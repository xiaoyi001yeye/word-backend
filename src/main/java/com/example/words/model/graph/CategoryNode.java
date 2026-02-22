package com.example.words.model.graph;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Category")
public class CategoryNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property("name")
    private String name;

    @Property("type")
    private String type;

    @Relationship(type = "HAS_FILE", direction = Relationship.Direction.INCOMING)
    private Set<VocabularyFileNode> files = new HashSet<>();

    public CategoryNode() {
    }

    public CategoryNode(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Set<VocabularyFileNode> getFiles() {
        return files;
    }

    public void setFiles(Set<VocabularyFileNode> files) {
        this.files = files;
    }
}
