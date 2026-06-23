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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "student_word_memory_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentWordMemoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_word_memory_id", nullable = false)
    private Long studentWordMemoryId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "meta_word_id", nullable = false)
    private Long metaWordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private StudentWordMemorySourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "dictionary_id")
    private Long dictionaryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private StudyRecordResult result;

    @Column(name = "box_level_before", nullable = false)
    private Integer boxLevelBefore;

    @Column(name = "box_level_after", nullable = false)
    private Integer boxLevelAfter;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
