package com.example.words.dto;

import com.example.words.model.StudentStudyPlanStatus;
import com.example.words.model.StudyDayTaskStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanStudentSummaryResponse {

    private Long studentId;
    private String studentName;
    private Long studentStudyPlanId;
    private StudentStudyPlanStatus status;
    private LocalDate taskDate;
    private StudyDayTaskStatus todayStatus;
    private Integer completedCount;
    private Integer totalTaskCount;
    private BigDecimal completionRate;
    private Integer totalFocusSeconds;
    private BigDecimal avgFocusSecondsPerWord;
    private BigDecimal attentionScore;
    private Integer currentStreak;
    private LocalDateTime lastStudyAt;
}
