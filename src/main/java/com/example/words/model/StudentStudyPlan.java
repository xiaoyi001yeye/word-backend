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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "student_study_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudentStudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_plan_id", nullable = false)
    private Long studyPlanId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudentStudyPlanStatus status = StudentStudyPlanStatus.ACTIVE;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "completed_days", nullable = false)
    private Integer completedDays = 0;

    @Column(name = "missed_days", nullable = false)
    private Integer missedDays = 0;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak = 0;

    @Column(name = "last_study_at")
    private LocalDateTime lastStudyAt;

    @Column(name = "overall_progress", nullable = false, precision = 5, scale = 2)
    private BigDecimal overallProgress = BigDecimal.ZERO;

    @Column(name = "avg_focus_seconds", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgFocusSeconds = BigDecimal.ZERO;

    @Column(name = "attention_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal attentionScore = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
