package com.example.words.controller;

import com.example.words.dto.StudentWordMemoryResponse;
import com.example.words.dto.UpdateFavoriteRequest;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudentWordMemoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/me")
@PreAuthorize("hasRole('STUDENT')")
public class StudentWordMemoryController {

    private final StudentWordMemoryService memoryService;
    private final CurrentUserService currentUserService;

    public StudentWordMemoryController(
            StudentWordMemoryService memoryService,
            CurrentUserService currentUserService) {
        this.memoryService = memoryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/word-memory")
    public ResponseEntity<List<StudentWordMemoryResponse>> listMemories() {
        return ResponseEntity.ok(memoryService.listMemories(currentUserService.getCurrentUser()));
    }

    @GetMapping("/wrong-words")
    public ResponseEntity<List<StudentWordMemoryResponse>> listWrongWords() {
        return ResponseEntity.ok(memoryService.listWrongWords(currentUserService.getCurrentUser()));
    }

    @GetMapping("/favorite-words")
    public ResponseEntity<List<StudentWordMemoryResponse>> listFavoriteWords() {
        return ResponseEntity.ok(memoryService.listFavoriteWords(currentUserService.getCurrentUser()));
    }

    @PatchMapping("/word-memory/{metaWordId}/favorite")
    public ResponseEntity<StudentWordMemoryResponse> updateFavorite(
            @PathVariable Long metaWordId,
            @Valid @RequestBody UpdateFavoriteRequest request) {
        return ResponseEntity.ok(memoryService.updateFavoriteResponse(
                metaWordId,
                request.getFavorite(),
                currentUserService.getCurrentUser()
        ));
    }
}
