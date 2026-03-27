package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "student_attention_daily_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentAttentionDailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_study_plan_id", nullable = false)
    private Long studentStudyPlanId;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "words_visited", nullable = false)
    private Integer wordsVisited = 0;

    @Column(name = "words_completed", nullable = false)
    private Integer wordsCompleted = 0;

    @Column(name = "total_focus_seconds", nullable = false)
    private Integer totalFocusSeconds = 0;

    @Column(name = "avg_focus_seconds_per_word", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgFocusSecondsPerWord = BigDecimal.ZERO;

    @Column(name = "median_focus_seconds_per_word", nullable = false, precision = 10, scale = 2)
    private BigDecimal medianFocusSecondsPerWord = BigDecimal.ZERO;

    @Column(name = "max_focus_seconds_per_word", nullable = false)
    private Integer maxFocusSecondsPerWord = 0;

    @Column(name = "long_stay_word_count", nullable = false)
    private Integer longStayWordCount = 0;

    @Column(name = "idle_interrupt_count", nullable = false)
    private Integer idleInterruptCount = 0;

    @Column(name = "attention_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal attentionScore = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
