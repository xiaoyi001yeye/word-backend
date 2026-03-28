package com.example.words.dto;

import com.example.words.model.BooksImportBatchFileStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BooksImportBatchFileResponse {

    private Long id;
    private String batchId;
    private String fileName;
    private String dictionaryName;
    private BooksImportBatchFileStatus status;
    private Long rowCount;
    private Long successRows;
    private Long failedRows;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
