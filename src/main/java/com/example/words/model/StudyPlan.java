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
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "study_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "dictionary_id", nullable = false)
    private Long dictionaryId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "daily_new_count", nullable = false)
    private Integer dailyNewCount;

    @Column(name = "daily_review_limit", nullable = false)
    private Integer dailyReviewLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_mode", nullable = false)
    private ReviewMode reviewMode = ReviewMode.EBBINGHAUS;

    @Column(name = "review_intervals_json", nullable = false, columnDefinition = "TEXT")
    private String reviewIntervalsJson;

    @Column(name = "completion_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionThreshold = BigDecimal.valueOf(100);

    @Column(name = "daily_deadline_time", nullable = false)
    private LocalTime dailyDeadlineTime;

    @Column(name = "attention_tracking_enabled", nullable = false)
    private Boolean attentionTrackingEnabled = Boolean.TRUE;

    @Column(name = "min_focus_seconds_per_word", nullable = false)
    private Integer minFocusSecondsPerWord = 3;

    @Column(name = "max_focus_seconds_per_word", nullable = false)
    private Integer maxFocusSecondsPerWord = 120;

    @Column(name = "long_stay_warning_seconds", nullable = false)
    private Integer longStayWarningSeconds = 60;

    @Column(name = "idle_timeout_seconds", nullable = false)
    private Integer idleTimeoutSeconds = 15;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudyPlanStatus status = StudyPlanStatus.DRAFT;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
