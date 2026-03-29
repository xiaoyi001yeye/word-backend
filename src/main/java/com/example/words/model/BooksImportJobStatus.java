package com.example.words.model;

public enum BooksImportJobStatus {
    PENDING,
    SCANNING,
    STAGING,
    STAGED,
    AUTO_MERGING,
    WAITING_REVIEW,
    READY_TO_PUBLISH,
    PUBLISHING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    DISCARDED
}
