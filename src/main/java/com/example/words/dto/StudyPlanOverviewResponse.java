package com.example.words.dto;

import com.example.words.model.StudyPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanOverviewResponse {

    private Long studyPlanId;
    private String studyPlanName;
    private StudyPlanStatus status;
    private LocalDate taskDate;
    private Long totalStudents;
    private Long completedStudents;
    private Long notStartedStudents;
    private Long inProgressStudents;
    private Long missedStudents;
    private BigDecimal averageCompletionRate;
    private BigDecimal averageAttentionScore;
}
