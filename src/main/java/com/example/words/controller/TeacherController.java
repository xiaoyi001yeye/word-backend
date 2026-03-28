package com.example.words.controller;

import com.example.words.dto.UserResponse;
import com.example.words.service.CurrentUserService;
import com.example.words.service.TeacherStudentService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

    private final TeacherStudentService teacherStudentService;
    private final CurrentUserService currentUserService;

    public TeacherController(
            TeacherStudentService teacherStudentService,
            CurrentUserService currentUserService) {
        this.teacherStudentService = teacherStudentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{teacherId}/students/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignStudent(
            @PathVariable Long teacherId,
            @PathVariable Long studentId) {
        teacherStudentService.assignStudent(teacherId, studentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{teacherId}/students/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeStudent(
            @PathVariable Long teacherId,
            @PathVariable Long studentId) {
        teacherStudentService.removeStudent(teacherId, studentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/students")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<UserResponse>> getMyStudents() {
        return ResponseEntity.ok(teacherStudentService.getStudentsForTeacher(currentUserService.getCurrentUser().getId()));
    }

    @GetMapping("/me/students/page")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Page<UserResponse>> getMyStudentsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(
                teacherStudentService.getStudentsForTeacher(currentUserService.getCurrentUser().getId(), page, size, name)
        );
    }

    @GetMapping("/{teacherId}/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getStudentsForTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(teacherStudentService.getStudentsForTeacher(teacherId));
    }

    @GetMapping("/{teacherId}/students/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getStudentsForTeacherPage(
            @PathVariable Long teacherId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(teacherStudentService.getStudentsForTeacher(teacherId, page, size, name));
    }
}
