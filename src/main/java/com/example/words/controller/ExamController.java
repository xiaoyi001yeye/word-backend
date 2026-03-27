package com.example.words.controller;

import com.example.words.dto.CreateExamRequest;
import com.example.words.dto.ExamHistoryItemDto;
import com.example.words.dto.ExamResponse;
import com.example.words.dto.SubmitExamRequest;
import com.example.words.dto.SubmitExamResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.ExamService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exams")
@Validated
public class ExamController {

    private final ExamService examService;
    private final CurrentUserService currentUserService;

    public ExamController(ExamService examService, CurrentUserService currentUserService) {
        this.examService = examService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody CreateExamRequest request) {
        return ResponseEntity.ok(examService.createExam(request, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{examId}")
    public ResponseEntity<ExamResponse> getExam(@PathVariable Long examId) {
        return ResponseEntity.ok(examService.getExam(examId, currentUserService.getCurrentUser()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ExamHistoryItemDto>> getExamHistory(
            @RequestParam(required = false) Long dictionaryId) {
        return ResponseEntity.ok(examService.getExamHistory(dictionaryId, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{examId}/result")
    public ResponseEntity<SubmitExamResponse> getExamResult(@PathVariable Long examId) {
        return ResponseEntity.ok(examService.getExamResult(examId, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{examId}/submit")
    public ResponseEntity<SubmitExamResponse> submitExam(
            @PathVariable Long examId,
            @Valid @RequestBody SubmitExamRequest request) {
        return ResponseEntity.ok(examService.submitExam(examId, request, currentUserService.getCurrentUser()));
    }
}
