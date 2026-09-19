package com.example.words.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordFamilyItem {

    private String word;
    private String pos;
    private String translation;
}
