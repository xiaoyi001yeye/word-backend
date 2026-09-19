package com.example.words.dto;

import java.util.List;
import com.example.words.service.LearningMaterialStatus;
import com.example.words.service.LearningMaterialWarning;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateDictionaryWordWithAiResponse {

    private Long dictionaryId;
    private Long metaWordId;
    private Long configId;
    private String providerName;
    private String modelName;
    private String word;
    private String translation;
    private String partOfSpeech;
    private String phonetic;
    private String definition;
    private String exampleSentence;
    private int total;
    private int existed;
    private int created;
    private int added;
    private int failed;
    private LearningMaterialStatus learningMaterialStatus;
    private List<LearningMaterialWarning> learningMaterialWarnings;
    private String learningMaterialSourcePath;
    private Integer learningMaterialStartLine;
    private Integer learningMaterialEndLine;
}
