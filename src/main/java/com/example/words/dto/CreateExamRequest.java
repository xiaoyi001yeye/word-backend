package com.example.words.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateExamRequest {

    @NotNull(message = "dictionaryId is required")
    private Long dictionaryId;

    @NotNull(message = "questionCount is required")
    @Min(value = 1, message = "questionCount must be greater than 0")
    private Integer questionCount;

    @NotNull(message = "targetUserId is required")
    private Long targetUserId;
}
