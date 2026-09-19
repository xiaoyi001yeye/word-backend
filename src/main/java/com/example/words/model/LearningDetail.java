package com.example.words.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningDetail {

    private String learningMaterial;
    private String memoryHint;
    private List<SamePatternWord> samePatternWords;
    private List<String> examPhrases;
    private List<WordFamilyItem> wordFamily;
    private List<ConfusableWord> confusableWords;
}
