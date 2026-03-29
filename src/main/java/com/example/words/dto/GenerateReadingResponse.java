package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateReadingResponse {

    private Long configId;
    private String providerName;
    private String modelName;
    private String title;
    private String content;
}
