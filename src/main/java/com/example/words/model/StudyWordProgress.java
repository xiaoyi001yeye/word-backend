package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "study_word_progresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyWordProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_study_plan_id", nullable = false)
    private Long studentStudyPlanId;

    @Column(name = "meta_word_id", nullable = false)
    private Long metaWordId;

    @Column(name = "phase", nullable = false)
    private Integer phase = 0;

    @Column(name = "mastery_level", nullable = false, precision = 5, scale = 2)
    private BigDecimal masteryLevel = BigDecimal.ZERO;

    @Column(name = "assigned_date")
    private LocalDate assignedDate;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "last_review_at")
    private LocalDateTime lastReviewAt;

    @Column(name = "correct_times", nullable = false)
    private Integer correctTimes = 0;

    @Column(name = "wrong_times", nullable = false)
    private Integer wrongTimes = 0;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_result")
    private StudyRecordResult lastResult;

    @Column(name = "last_focus_seconds")
    private Integer lastFocusSeconds;

    @Column(name = "avg_focus_seconds", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgFocusSeconds = BigDecimal.ZERO;

    @Column(name = "max_focus_seconds", nullable = false)
    private Integer maxFocusSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudyWordProgressStatus status = StudyWordProgressStatus.NEW;
}
