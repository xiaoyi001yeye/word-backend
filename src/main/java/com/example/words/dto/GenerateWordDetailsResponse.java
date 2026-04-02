package com.example.words.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateWordDetailsResponse {

    private Long configId;
    private String providerName;
    private String modelName;
    private String word;
    private String translation;
    private String partOfSpeech;
    private String phonetic;
    private String definition;
    private String exampleSentence;
}
