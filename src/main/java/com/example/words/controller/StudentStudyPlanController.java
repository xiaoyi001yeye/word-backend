package com.example.words.controller;

import com.example.words.dto.RecordStudyRequest;
import com.example.words.dto.StudentAttentionDailyStatResponse;
import com.example.words.dto.StudentStudyPlanSummaryResponse;
import com.example.words.dto.StudyTaskResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.StudyPlanService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/me/study-plans")
public class StudentStudyPlanController {

    private final StudyPlanService studyPlanService;
    private final CurrentUserService currentUserService;

    public StudentStudyPlanController(StudyPlanService studyPlanService, CurrentUserService currentUserService) {
        this.studyPlanService = studyPlanService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentStudyPlanSummaryResponse>> listStudentStudyPlans() {
        return ResponseEntity.ok(studyPlanService.listStudentStudyPlans(currentUserService.getCurrentUser()));
    }

    @GetMapping("/{id}/today")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudyTaskResponse> getTodayTask(@PathVariable Long id) {
        return ResponseEntity.ok(studyPlanService.getTodayTask(id, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{id}/records")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudyTaskResponse> recordStudy(
            @PathVariable Long id,
            @Valid @RequestBody RecordStudyRequest request) {
        return ResponseEntity.ok(studyPlanService.recordStudy(id, request, currentUserService.getCurrentUser()));
    }

    @GetMapping("/{id}/attention")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentAttentionDailyStatResponse>> getAttention(@PathVariable Long id) {
        return ResponseEntity.ok(studyPlanService.getStudentAttentionStats(id, currentUserService.getCurrentUser()));
    }
}
