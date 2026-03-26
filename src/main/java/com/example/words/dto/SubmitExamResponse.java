package com.example.words.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitExamResponse {

    private Long examId;
    private Long dictionaryId;
    private String dictionaryName;
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private Integer correctCount;
    private Integer score;
    private String status;
    private LocalDateTime submittedAt;
    private List<ExamResultQuestionDto> results;
}
