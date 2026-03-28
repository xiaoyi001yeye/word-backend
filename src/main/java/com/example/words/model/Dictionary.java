package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dictionaries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Dictionary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "category")
    private String category;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "entry_count")
    private Integer entryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_type", nullable = false)
    private DictionaryCreationType creationType = DictionaryCreationType.USER_CREATED;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ResourceScopeType scopeType = ResourceScopeType.SYSTEM;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Dictionary(String name, String filePath, Long fileSize, String category) {
        this.name = name;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.category = category;
        this.wordCount = 0;
        this.entryCount = 0;
        this.creationType = DictionaryCreationType.USER_CREATED;
        this.scopeType = ResourceScopeType.TEACHER;
    }

    public Dictionary(String name, String filePath, Long fileSize, String category, DictionaryCreationType creationType) {
        this.name = name;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.category = category;
        this.wordCount = 0;
        this.entryCount = 0;
        this.creationType = creationType;
        this.scopeType = creationType == DictionaryCreationType.IMPORTED ? ResourceScopeType.SYSTEM : ResourceScopeType.TEACHER;
    }
}
