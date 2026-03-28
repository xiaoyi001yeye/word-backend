package com.example.words.service;

public interface BooksImportProgressListener {

    BooksImportProgressListener NOOP = new BooksImportProgressListener() {
    };

    default void onFilesDiscovered(int totalFiles) {
    }

    default void onFileStarted(String fileName, int processedFiles, int totalFiles) {
    }

    default void onFileCompleted(
            String fileName,
            int processedFiles,
            int totalFiles,
            int importedDictionaryCount,
            long importedWordCount,
            boolean success) {
    }
}
