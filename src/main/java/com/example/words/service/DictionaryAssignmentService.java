package com.example.words.service;

import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryAssignment;
import com.example.words.model.UserRole;
import com.example.words.repository.DictionaryAssignmentRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionaryAssignmentService {

    private final DictionaryAssignmentRepository dictionaryAssignmentRepository;
    private final UserService userService;
    private final TeacherStudentService teacherStudentService;

    public DictionaryAssignmentService(
            DictionaryAssignmentRepository dictionaryAssignmentRepository,
            UserService userService,
            TeacherStudentService teacherStudentService) {
        this.dictionaryAssignmentRepository = dictionaryAssignmentRepository;
        this.userService = userService;
        this.teacherStudentService = teacherStudentService;
    }

    @Transactional
    public int assignDictionaryToStudents(Dictionary dictionary, AppUser actor, List<Long> studentIds) {
        int assignedCount = 0;
        for (Long studentId : studentIds) {
            AppUser student = userService.getUserEntity(studentId);
            if (student.getRole() != UserRole.STUDENT) {
                throw new BadRequestException("User is not a student: " + studentId);
            }

            if (actor.getRole() == UserRole.TEACHER
                    && !teacherStudentService.isTeacherResponsibleForStudent(actor.getId(), studentId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Teacher cannot assign dictionaries to unrelated students");
            }

            if (dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(dictionary.getId(), studentId)) {
                continue;
            }

            DictionaryAssignment assignment = new DictionaryAssignment();
            assignment.setDictionaryId(dictionary.getId());
            assignment.setStudentId(studentId);
            assignment.setAssignedByUserId(actor.getId());
            dictionaryAssignmentRepository.save(assignment);
            assignedCount++;
        }
        return assignedCount;
    }

    @Transactional(readOnly = true)
    public boolean isDictionaryAssignedToStudent(Long dictionaryId, Long studentId) {
        return dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(dictionaryId, studentId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getAssignedDictionaryIdsForStudent(Long studentId) {
        return dictionaryAssignmentRepository.findByStudentId(studentId).stream()
                .map(DictionaryAssignment::getDictionaryId)
                .collect(Collectors.toSet());
    }
}
