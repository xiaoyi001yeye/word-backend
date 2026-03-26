package com.example.words.service;

import com.example.words.dto.UserResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.TeacherStudentRelation;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.TeacherStudentRelationRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherStudentService {

    private final TeacherStudentRelationRepository teacherStudentRelationRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final UserService userService;

    public TeacherStudentService(
            TeacherStudentRelationRepository teacherStudentRelationRepository,
            ClassroomRepository classroomRepository,
            ClassroomMemberRepository classroomMemberRepository,
            UserService userService) {
        this.teacherStudentRelationRepository = teacherStudentRelationRepository;
        this.classroomRepository = classroomRepository;
        this.classroomMemberRepository = classroomMemberRepository;
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

        List<Long> classroomIds = classroomRepository.findByTeacherId(teacherId).stream()
                .map(Classroom::getId)
                .toList();

        if (!classroomIds.isEmpty()) {
            classroomMemberRepository.findByClassroomIdIn(classroomIds).stream()
                    .map(ClassroomMember::getStudentId)
                    .forEach(studentIds::add);
        }

        return studentIds.stream()
                .map(userService::findById)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isTeacherResponsibleForStudent(Long teacherId, Long studentId) {
        if (teacherStudentRelationRepository.existsByTeacherIdAndStudentId(teacherId, studentId)) {
            return true;
        }

        List<Long> classroomIds = classroomRepository.findByTeacherId(teacherId).stream()
                .map(Classroom::getId)
                .toList();

        return !classroomIds.isEmpty()
                && classroomMemberRepository.existsByClassroomIdInAndStudentId(classroomIds, studentId);
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
