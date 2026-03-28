package com.example.words.dto;

import com.example.words.model.ReviewMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateStudyPlanRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotNull(message = "dictionaryId is required")
    private Long dictionaryId;

    @NotEmpty(message = "classroomIds cannot be empty")
    private List<Long> classroomIds;

    @NotNull(message = "startDate is required")
    private LocalDate startDate;

    private LocalDate endDate;

    @NotBlank(message = "timezone is required")
    private String timezone;

    @NotNull(message = "dailyNewCount is required")
    @Min(value = 1, message = "dailyNewCount must be greater than 0")
    private Integer dailyNewCount;

    @NotNull(message = "dailyReviewLimit is required")
    @Min(value = 0, message = "dailyReviewLimit must be greater than or equal to 0")
    private Integer dailyReviewLimit;

    @NotNull(message = "reviewMode is required")
    private ReviewMode reviewMode;

    @NotEmpty(message = "reviewIntervals cannot be empty")
    private List<Integer> reviewIntervals;

    @NotNull(message = "completionThreshold is required")
    @DecimalMin(value = "0.0", message = "completionThreshold must be at least 0")
    @DecimalMax(value = "100.0", message = "completionThreshold must be at most 100")
    private BigDecimal completionThreshold;

    @NotNull(message = "dailyDeadlineTime is required")
    private LocalTime dailyDeadlineTime;

    @NotNull(message = "attentionTrackingEnabled is required")
    private Boolean attentionTrackingEnabled;

    @NotNull(message = "minFocusSecondsPerWord is required")
    @Min(value = 0, message = "minFocusSecondsPerWord must be greater than or equal to 0")
    private Integer minFocusSecondsPerWord;

    @NotNull(message = "maxFocusSecondsPerWord is required")
    @Min(value = 1, message = "maxFocusSecondsPerWord must be greater than 0")
    private Integer maxFocusSecondsPerWord;

    @NotNull(message = "longStayWarningSeconds is required")
    @Min(value = 1, message = "longStayWarningSeconds must be greater than 0")
    private Integer longStayWarningSeconds;

    @NotNull(message = "idleTimeoutSeconds is required")
    @Min(value = 1, message = "idleTimeoutSeconds must be greater than 0")
    private Integer idleTimeoutSeconds;
}
