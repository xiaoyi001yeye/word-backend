package com.example.words.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SamePatternWord {

    private String word;
    private String translation;
    private String rootBreakdown;
}
