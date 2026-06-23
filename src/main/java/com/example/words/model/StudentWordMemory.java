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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "student_word_memories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentWordMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "meta_word_id", nullable = false)
    private Long metaWordId;

    @Column(name = "box_level", nullable = false)
    private Integer boxLevel = 0;

    @Column(name = "mastery_level", nullable = false, precision = 5, scale = 2)
    private BigDecimal masteryLevel = BigDecimal.ZERO;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "correct_times", nullable = false)
    private Integer correctTimes = 0;

    @Column(name = "wrong_times", nullable = false)
    private Integer wrongTimes = 0;

    @Column(name = "correct_streak", nullable = false)
    private Integer correctStreak = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_result")
    private StudyRecordResult lastResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_source")
    private StudentWordMemorySourceType lastSource;

    @Column(name = "auto_wrong", nullable = false)
    private Boolean autoWrong = false;

    @Column(name = "favorite", nullable = false)
    private Boolean favorite = false;

    @Column(name = "last_studied_at")
    private LocalDateTime lastStudiedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
