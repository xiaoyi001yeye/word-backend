package com.example.words.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateReadingRequest {

    private Long configId;

    @NotBlank(message = "topic is required")
    @Size(max = 200, message = "topic must not exceed 200 characters")
    private String topic;

    @NotNull(message = "wordCount is required")
    @Min(value = 50, message = "wordCount must be at least 50")
    @Max(value = 1000, message = "wordCount must be at most 1000")
    private Integer wordCount;

    @NotBlank(message = "difficulty is required")
    @Size(max = 100, message = "difficulty must not exceed 100 characters")
    private String difficulty;
}
