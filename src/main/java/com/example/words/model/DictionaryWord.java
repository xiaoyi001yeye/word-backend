package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dictionary_words")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DictionaryWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dictionary_id", nullable = false)
    private Long dictionaryId;

    @Column(name = "meta_word_id", nullable = false)
    private Long metaWordId;

    @Column(name = "chapter_tag_id")
    private Long chapterTagId;

    @Column(name = "entry_order", nullable = false)
    private Integer entryOrder = 1;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public DictionaryWord(Long dictionaryId, Long metaWordId) {
        this.dictionaryId = dictionaryId;
        this.metaWordId = metaWordId;
    }

    public DictionaryWord(Long dictionaryId, Long metaWordId, Long chapterTagId, Integer entryOrder) {
        this.dictionaryId = dictionaryId;
        this.metaWordId = metaWordId;
        this.chapterTagId = chapterTagId;
        this.entryOrder = entryOrder;
    }

    public DictionaryWord(Long id, Long dictionaryId, Long metaWordId, LocalDateTime createdAt) {
        this.id = id;
        this.dictionaryId = dictionaryId;
        this.metaWordId = metaWordId;
        this.createdAt = createdAt;
        this.entryOrder = 1;
    }
}
