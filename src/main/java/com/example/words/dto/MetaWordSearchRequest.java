package com.example.words.dto;

import lombok.Data;

@Data
public class MetaWordSearchRequest {
    private String keyword;
    private Long dictionaryId;
    private Integer page = 0;
    private Integer size = 10;
}