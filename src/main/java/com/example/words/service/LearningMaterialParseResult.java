package com.example.words.service;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Parsed teaching material and the source evidence used to obtain it. */
@Data
@AllArgsConstructor
public class LearningMaterialParseResult {

    private LearningMaterialStatus status;
    private String sourceMarkdown;
    private String learningMaterial;
    private String sourcePath;
    private Integer startLine;
    private Integer endLine;
    private List<LearningMaterialWarning> warnings;
}
