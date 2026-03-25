package com.example.words.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamResponse {

    private Long examId;
    private Long dictionaryId;
    private String dictionaryName;
    private Integer questionCount;
    private Integer answeredCount;
    private Integer correctCount;
    private Integer score;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private List<ExamQuestionDto> questions;
}
