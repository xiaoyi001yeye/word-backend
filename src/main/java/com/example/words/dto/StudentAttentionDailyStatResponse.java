package com.example.words.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAttentionDailyStatResponse {

    private LocalDate taskDate;
    private Integer wordsVisited;
    private Integer wordsCompleted;
    private Integer totalFocusSeconds;
    private BigDecimal avgFocusSecondsPerWord;
    private BigDecimal medianFocusSecondsPerWord;
    private Integer maxFocusSecondsPerWord;
    private Integer longStayWordCount;
    private Integer idleInterruptCount;
    private BigDecimal attentionScore;
}
