package com.example.words.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartOfSpeech {
    private String pos;
    private List<Definition> definitions;
    private Inflection inflection;
    private List<String> synonyms;
    private List<String> antonyms;
}