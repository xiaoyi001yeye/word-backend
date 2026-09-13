package com.example.words.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningDetailDto {
    @Size(max = 6000, message = "learning material must not exceed 6000 characters")
    private String learningMaterial;
    @Size(max = 80, message = "memory hint must not exceed 80 characters")
    private String memoryHint;
    private List<SamePatternWordDto> samePatternWords;
    private List<String> examPhrases;
    private List<WordFamilyItemDto> wordFamily;
    private List<ConfusableWordDto> confusableWords;
}
