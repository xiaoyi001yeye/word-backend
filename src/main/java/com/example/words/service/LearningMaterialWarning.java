package com.example.words.service;

import lombok.AllArgsConstructor;
import lombok.Data;

/** A machine-readable parsing warning with an optional one-based source range. */
@Data
@AllArgsConstructor
public class LearningMaterialWarning {

    private String code;
    private String message;
    private Integer startLine;
    private Integer endLine;
}
