package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "books_import_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BooksImportJob {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BooksImportJobStatus status;

    @Column(name = "batch_type", nullable = false, length = 32)
    private String batchType;

    @Column(name = "total_files")
    private Integer totalFiles;

    @Column(name = "processed_files")
    private Integer processedFiles;

    @Column(name = "failed_files")
    private Integer failedFiles;

    @Column(name = "total_rows")
    private Long totalRows;

    @Column(name = "processed_rows")
    private Long processedRows;

    @Column(name = "success_rows")
    private Long successRows;

    @Column(name = "failed_rows")
    private Long failedRows;

    @Column(name = "imported_dictionary_count")
    private Integer importedDictionaryCount;

    @Column(name = "imported_word_count")
    private Long importedWordCount;

    @Column(name = "current_file", length = 500)
    private String currentFile;

    @Column(name = "candidate_count")
    private Long candidateCount;

    @Column(name = "conflict_count")
    private Long conflictCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "publish_started_at")
    private LocalDateTime publishStartedAt;

    @Column(name = "publish_finished_at")
    private LocalDateTime publishFinishedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
