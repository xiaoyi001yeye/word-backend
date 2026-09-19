package com.example.words.service;

/** The outcome of loading and safely parsing a teaching-material Markdown entry. */
public enum LearningMaterialStatus {
    FOUND,
    NOT_FOUND,
    PARSE_WARNING,
    READ_ERROR,
    SOURCE_CHANGED
}
