package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CompanionImageUploadResponse {

    private String url;

    private String fileName;
}
