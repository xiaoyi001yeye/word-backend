package com.example.words.service;

import com.example.words.dto.UserResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.TeacherStudentRelation;
import com.example.words.model.UserRole;
import com.example.words.repository.TeacherStudentRelationRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherStudentService {

    private final TeacherStudentRelationRepository teacherStudentRelationRepository;
    private final UserService userService;

    public TeacherStudentService(
            TeacherStudentRelationRepository teacherStudentRelationRepository,
            UserService userService) {
        this.teacherStudentRelationRepository = teacherStudentRelationRepository;
        this.userService = userService;
    }

    @Transactional
    public void assignStudent(Long teacherId, Long studentId) {
        AppUser teacher = userService.getUserEntity(teacherId);
        AppUser student = userService.getUserEntity(studentId);
        validateTeacherAndStudent(teacher, student);

        if (teacherStudentRelationRepository.existsByTeacherIdAndStudentId(teacherId, studentId)) {
            return;
        }

        teacherStudentRelationRepository.save(new TeacherStudentRelation(null, teacherId, studentId, null));
    }

    @Transactional
    public void removeStudent(Long teacherId, Long studentId) {
        teacherStudentRelationRepository.deleteByTeacherIdAndStudentId(teacherId, studentId);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getStudentsForTeacher(Long teacherId) {
        Set<Long> studentIds = teacherStudentRelationRepository.findByTeacherId(teacherId).stream()
                .map(TeacherStudentRelation::getStudentId)
                .collect(Collectors.toSet());

        return studentIds.stream()
                .map(userService::findById)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isTeacherResponsibleForStudent(Long teacherId, Long studentId) {
        return teacherStudentRelationRepository.existsByTeacherIdAndStudentId(teacherId, studentId);
    }

    private void validateTeacherAndStudent(AppUser teacher, AppUser student) {
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BadRequestException("User is not a teacher: " + teacher.getId());
        }
        if (student.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("User is not a student: " + student.getId());
        }
    }
}
