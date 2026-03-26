package com.example.words.controller;

import com.example.words.dto.AssignStudentsRequest;
import com.example.words.dto.AssignClassroomsRequest;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.service.AccessControlService;
import com.example.words.service.CurrentUserService;
import com.example.words.service.ClassroomService;
import com.example.words.service.DictionaryAssignmentService;
import com.example.words.service.DictionaryService;
import com.example.words.service.MetaWordService;
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
@RequestMapping("/api/dictionaries")
public class DictionaryController {

    private final DictionaryService dictionaryService;
    private final MetaWordService metaWordService;
    private final CurrentUserService currentUserService;
    private final DictionaryAssignmentService dictionaryAssignmentService;
    private final AccessControlService accessControlService;
    private final ClassroomService classroomService;

    public DictionaryController(
            DictionaryService dictionaryService,
            MetaWordService metaWordService,
            CurrentUserService currentUserService,
            DictionaryAssignmentService dictionaryAssignmentService,
            AccessControlService accessControlService,
            ClassroomService classroomService) {
        this.dictionaryService = dictionaryService;
        this.metaWordService = metaWordService;
        this.currentUserService = currentUserService;
        this.dictionaryAssignmentService = dictionaryAssignmentService;
        this.accessControlService = accessControlService;
        this.classroomService = classroomService;
    }

    @GetMapping
    public List<Dictionary> list() {
        return dictionaryService.findVisibleDictionaries(currentUserService.getCurrentUser());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dictionary> get(@PathVariable Long id) {
        return dictionaryService.findByIdVisibleToUser(id, currentUserService.getCurrentUser())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    public List<Dictionary> getByCategory(@PathVariable String category) {
        return dictionaryService.findByCategoryVisibleToUser(category, currentUserService.getCurrentUser());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Dictionary> create(@RequestBody Dictionary dictionary) {
        Dictionary savedDictionary = dictionaryService.createDictionary(dictionary, currentUserService.getCurrentUser());
        return ResponseEntity.ok(savedDictionary);
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importDictionaries() {
        MetaWordService.BooksImportResult result = metaWordService.importBooksData();
        return ResponseEntity.ok(Map.of(
                "message", "Books imported successfully",
                "count", result.getDictionaryCount(),
                "wordCount", result.getWordCount()
        ));
    }

    @PostMapping("/{id}/assign/students")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> assignToStudents(
            @PathVariable Long id,
            @RequestBody AssignStudentsRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        Dictionary dictionary = dictionaryService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + id));

        for (Long studentId : request.getStudentIds()) {
            accessControlService.ensureCanAssignDictionaryToStudent(actor, dictionary, studentId);
        }

        int assignedCount = dictionaryAssignmentService.assignDictionaryToStudents(dictionary, actor, request.getStudentIds());
        return ResponseEntity.ok(Map.of(
                "message", "Dictionary assigned successfully",
                "dictionaryId", id,
                "assignedCount", assignedCount
        ));
    }

    @PostMapping("/{id}/assign/classrooms")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> assignToClassrooms(
            @PathVariable Long id,
            @RequestBody AssignClassroomsRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        Dictionary dictionary = dictionaryService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + id));

        List<Long> studentIds = classroomService.getStudentIdsForClassrooms(request.getClassroomIds(), actor).stream().toList();
        for (Long studentId : studentIds) {
            accessControlService.ensureCanAssignDictionaryToStudent(actor, dictionary, studentId);
        }

        int assignedCount = dictionaryAssignmentService.assignDictionaryToStudents(dictionary, actor, studentIds);
        return ResponseEntity.ok(Map.of(
                "message", "Dictionary assigned to classrooms successfully",
                "dictionaryId", id,
                "assignedCount", assignedCount
        ));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAll() {
        dictionaryService.deleteAll();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/user-created")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteUserCreatedDictionaries() {
        int deletedCount = dictionaryService.deleteUserCreatedDictionaries();
        return ResponseEntity.ok(Map.of(
                "message", "User-created dictionaries deleted successfully",
                "deletedCount", deletedCount
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, Object>> deleteById(@PathVariable Long id) {
        boolean deleted = dictionaryService.deleteById(id, currentUserService.getCurrentUser());
        if (deleted) {
            return ResponseEntity.ok(Map.of(
                    "message", "Dictionary deleted successfully",
                    "id", id
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
