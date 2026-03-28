package com.example.words.dto;

import com.example.words.model.BooksImportJobStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BooksImportJobResponse {

    private String jobId;
    private BooksImportJobStatus status;
    private String batchType;
    private Integer totalFiles;
    private Integer processedFiles;
    private Integer failedFiles;
    private Long totalRows;
    private Long processedRows;
    private Long successRows;
    private Long failedRows;
    private Integer importedDictionaryCount;
    private Long importedWordCount;
    private String currentFile;
    private Long candidateCount;
    private Long conflictCount;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime publishStartedAt;
    private LocalDateTime publishFinishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
