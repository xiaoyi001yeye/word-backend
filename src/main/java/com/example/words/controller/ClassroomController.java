package com.example.words.controller;

import com.example.words.dto.ClassroomResponse;
import com.example.words.dto.CreateClassroomRequest;
import com.example.words.dto.UserResponse;
import com.example.words.service.ClassroomService;
import com.example.words.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classrooms")
public class ClassroomController {

    private final ClassroomService classroomService;
    private final CurrentUserService currentUserService;

    public ClassroomController(ClassroomService classroomService, CurrentUserService currentUserService) {
        this.classroomService = classroomService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<ClassroomResponse>> listClassrooms() {
        return ResponseEntity.ok(classroomService.findVisibleClassrooms(currentUserService.getCurrentUser()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<ClassroomResponse> createClassroom(@Valid @RequestBody CreateClassroomRequest request) {
        return ResponseEntity.ok(classroomService.createClassroom(request, currentUserService.getCurrentUser()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> deleteClassroom(@PathVariable Long id) {
        classroomService.deleteClassroom(id, currentUserService.getCurrentUser());
        return ResponseEntity.ok(Map.of(
                "message", "Classroom deleted successfully",
                "id", id
        ));
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<UserResponse>> getClassroomStudents(@PathVariable Long id) {
        return ResponseEntity.ok(classroomService.getStudentsForClassroom(id, currentUserService.getCurrentUser()));
    }

    @PostMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> addStudentToClassroom(@PathVariable Long id, @PathVariable Long studentId) {
        classroomService.addStudentToClassroom(id, studentId, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Void> removeStudentFromClassroom(@PathVariable Long id, @PathVariable Long studentId) {
        classroomService.removeStudentFromClassroom(id, studentId, currentUserService.getCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
