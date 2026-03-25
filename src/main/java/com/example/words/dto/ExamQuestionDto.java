package com.example.words.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestionDto {

    private Long questionId;
    private String word;
    private List<ExamOptionDto> options;
}
