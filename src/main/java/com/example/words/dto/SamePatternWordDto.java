package com.example.words.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamePatternWordDto {
    @Size(max = 100, message = "same-pattern word must not exceed 100 characters")
    private String word;

    @Size(max = 100, message = "same-pattern translation must not exceed 100 characters")
    private String translation;

    @Size(max = 160, message = "root breakdown must not exceed 160 characters")
    private String rootBreakdown;
}
