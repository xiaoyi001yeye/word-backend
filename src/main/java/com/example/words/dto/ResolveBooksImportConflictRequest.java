package com.example.words.dto;

import com.example.words.model.ImportConflictResolution;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ResolveBooksImportConflictRequest {

    @NotNull
    private ImportConflictResolution resolution;

    private String finalWord;

    private String finalDefinition;

    private Integer finalDifficulty;

    private PhoneticDto finalPhonetic;

    private List<PartOfSpeechDto> finalPartOfSpeech;

    private String comment;
}
