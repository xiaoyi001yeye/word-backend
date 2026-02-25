package com.example.words.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Inflection {
    private String plural;
    private String past;
    private String pastParticiple;
    private String presentParticiple;
    private String thirdPersonSingular;
    private String comparative;
    private String superlative;
}