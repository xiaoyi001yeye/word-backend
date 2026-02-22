package com.example.words.model.graph;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("VocabularyFile")
public class VocabularyFileNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property("fileName")
    private String fileName;

    @Property("filePath")
    private String filePath;

    @Property("fileSize")
    private Long fileSize;

    @Property("category")
    private String category;

    @Relationship(type = "HAS_CATEGORY", direction = Relationship.Direction.OUTGOING)
    private Set<CategoryNode> categories = new HashSet<>();

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private Set<VocabularyFileNode> relatedFiles = new HashSet<>();

    public VocabularyFileNode() {
    }

    public VocabularyFileNode(String fileName, String filePath, Long fileSize, String category) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Set<CategoryNode> getCategories() {
        return categories;
    }

    public void setCategories(Set<CategoryNode> categories) {
        this.categories = categories;
    }

    public Set<VocabularyFileNode> getRelatedFiles() {
        return relatedFiles;
    }

    public void setRelatedFiles(Set<VocabularyFileNode> relatedFiles) {
        this.relatedFiles = relatedFiles;
    }
}
