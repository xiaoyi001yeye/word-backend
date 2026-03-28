package com.example.words.dto;

import com.example.words.model.ReviewMode;
import com.example.words.model.StudyPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanResponse {

    private Long id;
    private String name;
    private String description;
    private Long teacherId;
    private Long dictionaryId;
    private String dictionaryName;
    private List<Long> classroomIds;
    private LocalDate startDate;
    private LocalDate endDate;
    private String timezone;
    private Integer dailyNewCount;
    private Integer dailyReviewLimit;
    private ReviewMode reviewMode;
    private List<Integer> reviewIntervals;
    private BigDecimal completionThreshold;
    private LocalTime dailyDeadlineTime;
    private Boolean attentionTrackingEnabled;
    private Integer minFocusSecondsPerWord;
    private Integer maxFocusSecondsPerWord;
    private Integer longStayWarningSeconds;
    private Integer idleTimeoutSeconds;
    private StudyPlanStatus status;
    private Long studentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
