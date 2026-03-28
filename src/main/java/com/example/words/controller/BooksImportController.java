package com.example.words.controller;

import com.example.words.dto.BooksImportBatchFileResponse;
import com.example.words.dto.BooksImportConflictResponse;
import com.example.words.dto.BooksImportJobResponse;
import com.example.words.dto.ResolveBooksImportConflictRequest;
import com.example.words.model.ImportConflictType;
import com.example.words.service.BooksImportJobService;
import com.example.words.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/books-import/batches")
@PreAuthorize("hasRole('ADMIN')")
public class BooksImportController {

    private final BooksImportJobService booksImportJobService;
    private final CurrentUserService currentUserService;

    public BooksImportController(BooksImportJobService booksImportJobService, CurrentUserService currentUserService) {
        this.booksImportJobService = booksImportJobService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<BooksImportJobResponse> createBatch() {
        return ResponseEntity.accepted().body(booksImportJobService.createAndStart(currentUserService.getCurrentUser().getId()));
    }

    @GetMapping("/latest")
    public ResponseEntity<BooksImportJobResponse> getLatestBatch() {
        return ResponseEntity.ok(booksImportJobService.getLatestJob());
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<BooksImportJobResponse> getBatch(@PathVariable String batchId) {
        return ResponseEntity.ok(booksImportJobService.getJob(batchId));
    }

    @GetMapping("/{batchId}/files")
    public ResponseEntity<List<BooksImportBatchFileResponse>> getBatchFiles(@PathVariable String batchId) {
        return ResponseEntity.ok(booksImportJobService.getFiles(batchId));
    }

    @GetMapping("/{batchId}/files/page")
    public ResponseEntity<Page<BooksImportBatchFileResponse>> getBatchFilesPage(
            @PathVariable String batchId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(booksImportJobService.getFilesPage(batchId, page, size));
    }

    @PostMapping("/{batchId}/auto-merge")
    public ResponseEntity<BooksImportJobResponse> startAutoMerge(@PathVariable String batchId) {
        return ResponseEntity.accepted().body(booksImportJobService.startAutoMerge(batchId));
    }

    @GetMapping("/{batchId}/conflicts")
    public ResponseEntity<List<BooksImportConflictResponse>> getConflicts(
            @PathVariable String batchId,
            @RequestParam(required = false) ImportConflictType conflictType,
            @RequestParam(required = false) Boolean resolved) {
        return ResponseEntity.ok(booksImportJobService.getConflicts(batchId, conflictType, resolved));
    }

    @PostMapping("/{batchId}/conflicts/{conflictId}/resolve")
    public ResponseEntity<BooksImportConflictResponse> resolveConflict(
            @PathVariable String batchId,
            @PathVariable Long conflictId,
            @Valid @RequestBody ResolveBooksImportConflictRequest request) {
        return ResponseEntity.ok(booksImportJobService.resolveConflict(
                batchId,
                conflictId,
                request,
                currentUserService.getCurrentUser().getId()
        ));
    }

    @PostMapping("/{batchId}/publish")
    public ResponseEntity<BooksImportJobResponse> publish(@PathVariable String batchId) {
        return ResponseEntity.accepted().body(booksImportJobService.startPublish(batchId));
    }

    @PostMapping("/{batchId}/discard")
    public ResponseEntity<BooksImportJobResponse> discard(@PathVariable String batchId) {
        return ResponseEntity.ok(booksImportJobService.discard(batchId));
    }

    @GetMapping(value = "/{batchId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBatch(@PathVariable String batchId) {
        return booksImportJobService.subscribe(batchId);
    }
}
