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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "study_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StudyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_study_plan_id", nullable = false)
    private Long studentStudyPlanId;

    @Column(name = "meta_word_id", nullable = false)
    private Long metaWordId;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private StudyActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private StudyRecordResult result;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "focus_seconds")
    private Integer focusSeconds;

    @Column(name = "idle_seconds")
    private Integer idleSeconds;

    @Column(name = "interaction_count", nullable = false)
    private Integer interactionCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "attention_state")
    private AttentionState attentionState;

    @Column(name = "stage_before")
    private Integer stageBefore;

    @Column(name = "stage_after")
    private Integer stageAfter;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
