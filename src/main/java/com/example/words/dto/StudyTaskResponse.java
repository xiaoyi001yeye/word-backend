package com.example.words.dto;

import com.example.words.model.StudyDayTaskStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyTaskResponse {

    private Long studentStudyPlanId;
    private LocalDate taskDate;
    private StudyDayTaskStatus status;
    private Integer overdueCount;
    private Integer reviewCount;
    private Integer newCount;
    private Integer completedCount;
    private Integer totalFocusSeconds;
    private BigDecimal completionRate;
    private BigDecimal avgFocusSecondsPerWord;
    private BigDecimal attentionScore;
    private List<StudyTaskItemResponse> queue;
}
