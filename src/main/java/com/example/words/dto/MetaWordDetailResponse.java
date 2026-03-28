package com.example.words.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaWordDetailResponse {

    private Long id;
    private String word;
    private String phonetic;
    private String definition;
    private String partOfSpeech;
    private String exampleSentence;
    private String translation;
    private Integer difficulty;
    private List<MetaWordDictionaryReferenceDto> dictionaryReferences = new ArrayList<>();
}
