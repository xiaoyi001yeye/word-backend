package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnswerDto {

    @NotNull(message = "questionId is required")
    private Long questionId;

    @NotBlank(message = "selectedOption is required")
    private String selectedOption;
}
