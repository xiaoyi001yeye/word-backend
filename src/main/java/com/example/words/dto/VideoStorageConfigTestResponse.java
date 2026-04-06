package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoStorageConfigTestResponse {

    private Long configId;
    private String configName;
    private boolean success;
    private String message;
}
