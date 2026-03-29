package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConfigTestResponse {

    private Long configId;
    private String providerName;
    private String modelName;
    private boolean success;
    private String reply;
}
