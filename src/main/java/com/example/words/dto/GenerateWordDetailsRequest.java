package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateWordDetailsRequest {

    private Long configId;

    @NotBlank(message = "word is required")
    @Size(max = 100, message = "word must not exceed 100 characters")
    private String word;
}
