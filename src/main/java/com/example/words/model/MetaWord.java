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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meta_words")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MetaWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "word", nullable = false, columnDefinition = "TEXT")
    private String word;

    @Column(name = "phonetic", columnDefinition = "TEXT")
    private String phonetic;

    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition;

    @Column(name = "part_of_speech", columnDefinition = "TEXT")
    private String partOfSpeech;

    @Column(name = "example_sentence", columnDefinition = "TEXT")
    private String exampleSentence;

    @Column(name = "translation", columnDefinition = "TEXT")
    private String translation;

    @Column(name = "difficulty")
    private Integer difficulty;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public MetaWord(String word, String phonetic, String definition, String partOfSpeech) {
        this.word = word;
        this.phonetic = phonetic;
        this.definition = definition;
        this.partOfSpeech = partOfSpeech;
    }
}
