package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetaWordSuggestionDto {

    private Long id;
    private String word;
    private String phonetic;
    private String definition;
    private String partOfSpeech;
    private String exampleSentence;
    private String translation;
    private Integer difficulty;
}
