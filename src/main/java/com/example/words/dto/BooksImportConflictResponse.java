package com.example.words.dto;

import com.example.words.model.ImportConflictResolution;
import com.example.words.model.ImportConflictType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BooksImportConflictResponse {

    private Long id;
    private Long candidateId;
    private String normalizedWord;
    private String displayWord;
    private ImportConflictType conflictType;
    private List<String> dictionaryNames;
    private ImportConflictResolution resolution;
    private Map<String, Object> existingPayload;
    private Map<String, Object> importedPayload;
    private Map<String, Object> resolvedPayload;
    private String comment;
    private LocalDateTime resolvedAt;
}
