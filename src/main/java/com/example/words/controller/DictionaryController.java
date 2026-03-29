package com.example.words.controller;

import com.example.words.dto.AssignStudentsRequest;
import com.example.words.dto.AssignClassroomsRequest;
import com.example.words.dto.BooksImportJobResponse;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.service.AccessControlService;
import com.example.words.service.BooksImportJobService;
import com.example.words.service.ClassroomDictionaryAssignmentService;
import com.example.words.service.CurrentUserService;
import com.example.words.service.DictionaryAssignmentService;
import com.example.words.service.DictionaryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController {

    private final DictionaryService dictionaryService;
    private final CurrentUserService currentUserService;
    private final DictionaryAssignmentService dictionaryAssignmentService;
    private final ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService;
    private final AccessControlService accessControlService;
    private final BooksImportJobService booksImportJobService;

    public DictionaryController(
            DictionaryService dictionaryService,
            CurrentUserService currentUserService,
            DictionaryAssignmentService dictionaryAssignmentService,
            ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService,
            AccessControlService accessControlService,
            BooksImportJobService booksImportJobService) {
        this.dictionaryService = dictionaryService;
        this.currentUserService = currentUserService;
        this.dictionaryAssignmentService = dictionaryAssignmentService;
        this.classroomDictionaryAssignmentService = classroomDictionaryAssignmentService;
        this.accessControlService = accessControlService;
        this.booksImportJobService = booksImportJobService;
    }

    @GetMapping
    public List<Dictionary> list(@RequestParam(required = false) List<Long> classroomIds) {
        return dictionaryService.findVisibleDictionariesForClassrooms(classroomIds, currentUserService.getCurrentUser());
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
    public ResponseEntity<BooksImportJobResponse> importDictionaries() {
        return ResponseEntity.accepted().body(booksImportJobService.createAndStart(currentUserService.getCurrentUser().getId()));
    }

    @GetMapping("/import/latest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BooksImportJobResponse> getLatestImportJob() {
        return ResponseEntity.ok(booksImportJobService.getLatestJob());
    }

    @GetMapping("/import/{jobId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BooksImportJobResponse> getImportJob(@PathVariable String jobId) {
        return ResponseEntity.ok(booksImportJobService.getJob(jobId));
    }

    @GetMapping(value = "/import/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public SseEmitter streamImportJob(@PathVariable String jobId) {
        return booksImportJobService.subscribe(jobId);
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

        int assignedCount = classroomDictionaryAssignmentService.assignDictionaryToClassrooms(
                dictionary.getId(),
                request.getClassroomIds(),
                actor
        );
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
