package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaWordReferenceLocationDto {

    private Long chapterTagId;
    private String chapterDisplayPath;
    private Integer entryOrder;
}
