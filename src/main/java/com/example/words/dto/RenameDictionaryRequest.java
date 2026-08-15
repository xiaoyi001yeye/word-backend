package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameDictionaryRequest {

    @NotBlank
    @Size(max = 500)
    private String name;
}
