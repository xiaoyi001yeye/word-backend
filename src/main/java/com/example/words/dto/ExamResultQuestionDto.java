package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamResultQuestionDto {

    private Long questionId;
    private String word;
    private String selectedOption;
    private String selectedTranslation;
    private String correctOption;
    private String correctTranslation;
    private boolean correct;
}
