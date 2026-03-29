package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryWordEntryResponse {

    private Long entryId;
    private Long dictionaryId;
    private Long metaWordId;
    private String word;
    private String translation;
    private String phonetic;
    private String definition;
    private Long chapterTagId;
    private String chapterDisplayPath;
    private Integer entryOrder;
}
