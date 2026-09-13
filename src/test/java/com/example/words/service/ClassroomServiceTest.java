package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.ClassroomResponse;
import com.example.words.dto.CreateClassroomRequest;
import com.example.words.dto.UpdateClassroomRequest;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomGroupFeedMessageRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.PaperReleaseTargetRepository;
import com.example.words.repository.StudyPlanClassroomRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;

    @Mock
    private StudyPlanClassroomRepository studyPlanClassroomRepository;

    @Mock
    private ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;

    @Mock
    private ClassroomGroupFeedMessageRepository classroomGroupFeedMessageRepository;

    @Mock
    private PaperReleaseTargetRepository paperReleaseTargetRepository;

    @Mock
    private UserService userService;

    @Mock
    private StudyPlanService studyPlanService;

    private ClassroomService classroomService;

    @BeforeEach
    void setUp() {
        classroomService = new ClassroomService(
                classroomRepository,
                classroomMemberRepository,
                studyPlanClassroomRepository,
                classroomDictionaryAssignmentRepository,
                classroomGroupFeedMessageRepository,
                paperReleaseTargetRepository,
                userService,
                studyPlanService
        );
    }

    @Test
    void createClassroomShouldRejectDuplicateNameAcrossTeachers() {
        AppUser admin = admin();
        Classroom existing = classroom(100L, "一班", 7L);

        when(classroomRepository.findAll()).thenReturn(List.of(existing));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> classroomService.createClassroom(
                        new CreateClassroomRequest(" 一班 ", "新班级", 8L),
                        admin
                )
        );

        assertEquals("Classroom name already exists: 一班", exception.getMessage());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    void updateClassroomShouldRejectDuplicateName() {
        AppUser admin = admin();
        Classroom existing = classroom(100L, "一班", 7L);
        Classroom target = classroom(101L, "二班", 7L);

        when(classroomRepository.findById(101L)).thenReturn(Optional.of(target));
        when(classroomRepository.findAll()).thenReturn(List.of(existing, target));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> classroomService.updateClassroom(
                        101L,
                        new UpdateClassroomRequest("一班", null, 7L),
                        admin
                )
        );

        assertEquals("Classroom name already exists: 一班", exception.getMessage());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    void deleteClassroomShouldRemoveEveryRelationBeforeDeletingClassroom() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(paperReleaseTargetRepository.existsBySourceClassroomId(100L)).thenReturn(false);
        when(classroomMemberRepository.findByClassroomId(100L)).thenReturn(List.of(
                new ClassroomMember(1L, 100L, 20L, null),
                new ClassroomMember(2L, 100L, 21L, null)
        ));

        classroomService.deleteClassroom(100L, teacher);

        InOrder inOrder = inOrder(
                studyPlanService,
                classroomMemberRepository,
                studyPlanClassroomRepository,
                classroomDictionaryAssignmentRepository,
                classroomGroupFeedMessageRepository,
                classroomRepository);
        inOrder.verify(studyPlanService).dropStudentFromClassroomStudyPlans(100L, 20L, teacher);
        inOrder.verify(studyPlanService).dropStudentFromClassroomStudyPlans(100L, 21L, teacher);
        inOrder.verify(studyPlanService).archiveStudyPlansForClassroom(100L, teacher);
        inOrder.verify(classroomMemberRepository).deleteByClassroomId(100L);
        inOrder.verify(studyPlanClassroomRepository).deleteByClassroomId(100L);
        inOrder.verify(classroomDictionaryAssignmentRepository).deleteByClassroomId(100L);
        inOrder.verify(classroomGroupFeedMessageRepository).deleteByClassroomId(100L);
        inOrder.verify(classroomRepository).delete(classroom);
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    void deleteEmptyClassroomShouldSkipRelationCleanupAndDeleteClassroom() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(paperReleaseTargetRepository.existsBySourceClassroomId(100L)).thenReturn(false);
        when(classroomMemberRepository.findByClassroomId(100L)).thenReturn(List.of());

        classroomService.deleteClassroom(100L, teacher);

        verify(studyPlanService, never()).dropStudentFromClassroomStudyPlans(any(), any(), any());
        verify(classroomRepository).delete(classroom);
    }

    @Test
    void deleteClassroomShouldReportArchivedStudyPlanCount() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(paperReleaseTargetRepository.existsBySourceClassroomId(100L)).thenReturn(false);
        when(classroomMemberRepository.findByClassroomId(100L)).thenReturn(List.of());
        when(studyPlanService.archiveStudyPlansForClassroom(100L, teacher)).thenReturn(2);

        int archivedStudyPlanCount = classroomService.deleteClassroom(100L, teacher);

        assertEquals(2, archivedStudyPlanCount);
        verify(classroomRepository).delete(classroom);
    }

    @Test
    void deleteClassroomShouldBeRejectedWhenReleasedExamTargetsExist() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(paperReleaseTargetRepository.existsBySourceClassroomId(100L)).thenReturn(true);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> classroomService.deleteClassroom(100L, teacher)
        );

        assertEquals(
                "Classroom has released exam records and cannot be deleted; archive it instead",
                exception.getMessage());
        verify(classroomRepository, never()).delete(any(Classroom.class));
        verify(classroomMemberRepository, never()).deleteByClassroomId(any());
        verify(studyPlanClassroomRepository, never()).deleteByClassroomId(any());
        verify(studyPlanService, never()).archiveStudyPlansForClassroom(any(), any());
    }

    @Test
    void archiveClassroomShouldKeepRelationsAndOnlyFlagArchive() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));

        classroomService.archiveClassroom(100L, teacher);

        assertEquals(ClassroomStatus.ARCHIVED, classroom.getStatus());
        assertNotNull(classroom.getArchivedAt());
        verify(classroomRepository, never()).delete(any(Classroom.class));
        verify(classroomMemberRepository, never()).deleteByClassroomId(any());
        verify(studyPlanClassroomRepository, never()).deleteByClassroomId(any());
        verify(classroomRepository).save(classroom);
    }

    @Test
    void archiveClassroomShouldRejectAlreadyArchivedClassroom() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);
        classroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> classroomService.archiveClassroom(100L, teacher)
        );

        assertEquals("Classroom is already archived: 100", exception.getMessage());
        verify(classroomRepository, never()).save(any(Classroom.class));
    }

    @Test
    void findVisibleClassroomsShouldHideArchivedClassrooms() {
        Classroom activeClassroom = classroom(100L, "一班", 7L);
        activeClassroom.setStatus(ClassroomStatus.ACTIVE);
        Classroom archivedClassroom = classroom(101L, "旧班级", 7L);
        archivedClassroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomRepository.findAll()).thenReturn(List.of(activeClassroom, archivedClassroom));
        when(userService.getUserEntity(7L)).thenReturn(teacher(7L));
        when(classroomMemberRepository.countByClassroomId(100L)).thenReturn(3L);

        List<String> names = classroomService.findVisibleClassrooms(admin()).stream()
                .map(ClassroomResponse::getName)
                .toList();

        assertEquals(List.of("一班"), names);
    }

    @Test
    void createClassroomShouldReuseNameOfArchivedClassroom() {
        Classroom archivedClassroom = classroom(100L, "一班", 7L);
        archivedClassroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomRepository.findAll()).thenReturn(List.of(archivedClassroom));
        when(userService.getUserEntity(7L)).thenReturn(teacher(7L));
        when(classroomRepository.save(any(Classroom.class))).thenAnswer(invocation -> {
            Classroom saved = invocation.getArgument(0);
            saved.setId(102L);
            return saved;
        });
        when(classroomMemberRepository.countByClassroomId(102L)).thenReturn(0L);

        ClassroomResponse response = classroomService.createClassroom(
                new CreateClassroomRequest("一班", null, 7L),
                admin()
        );

        assertEquals("一班", response.getName());
    }

    @Test
    void archivedClassroomShouldRejectNewMembers() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);
        classroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> classroomService.addStudentToClassroom(100L, 20L, teacher)
        );

        assertEquals("Archived classroom cannot accept new students", exception.getMessage());
        verify(classroomMemberRepository, never()).save(any(ClassroomMember.class));
    }

    @Test
    void addStudentToClassroomShouldEnrollStudentInPublishedStudyPlans() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);
        AppUser student = student(20L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(userService.getUserEntity(20L)).thenReturn(student);
        when(classroomMemberRepository.existsByClassroomIdAndStudentId(100L, 20L)).thenReturn(false);

        classroomService.addStudentToClassroom(100L, 20L, teacher);

        verify(classroomMemberRepository).save(any(ClassroomMember.class));
        verify(studyPlanService).enrollStudentInPublishedPlansForClassroom(100L, 20L, teacher);
    }

    @Test
    void addExistingStudentToClassroomShouldRepairMissingStudyPlanEnrollment() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);
        AppUser student = student(20L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(userService.getUserEntity(20L)).thenReturn(student);
        when(classroomMemberRepository.existsByClassroomIdAndStudentId(100L, 20L)).thenReturn(true);

        classroomService.addStudentToClassroom(100L, 20L, teacher);

        verify(classroomMemberRepository, never()).save(any(ClassroomMember.class));
        verify(studyPlanService).enrollStudentInPublishedPlansForClassroom(100L, 20L, teacher);
    }

    @Test
    void removeStudentFromClassroomShouldDropStudentFromClassroomStudyPlans() {
        AppUser teacher = teacher(7L);
        Classroom classroom = classroom(100L, "一班", 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));

        classroomService.removeStudentFromClassroom(100L, 20L, teacher);

        verify(classroomMemberRepository).deleteByClassroomIdAndStudentId(100L, 20L);
        verify(studyPlanService).dropStudentFromClassroomStudyPlans(100L, 20L, teacher);
    }

    @Test
    void listStudentClassroomsShouldReturnActiveMembershipClassroomsOnly() {
        AppUser student = student(20L);
        Classroom activeClassroom = classroom(100L, "一班", 7L);
        Classroom archivedClassroom = classroom(101L, "旧班级", 7L);
        archivedClassroom.setStatus(ClassroomStatus.ARCHIVED);

        when(classroomMemberRepository.findByStudentId(20L)).thenReturn(List.of(
                new ClassroomMember(1L, 100L, 20L, null),
                new ClassroomMember(2L, 101L, 20L, null)
        ));
        when(classroomRepository.findAllById(Set.of(100L, 101L))).thenReturn(List.of(activeClassroom, archivedClassroom));
        when(userService.getUserEntity(7L)).thenReturn(teacher(7L));
        when(classroomMemberRepository.countByClassroomId(100L)).thenReturn(12L);

        List<String> classroomNames = classroomService.findStudentClassrooms(student).stream()
                .map(response -> response.getName())
                .toList();

        assertEquals(List.of("一班"), classroomNames);
    }

    private AppUser admin() {
        AppUser admin = new AppUser();
        admin.setId(1L);
        admin.setRole(UserRole.ADMIN);
        return admin;
    }

    private AppUser teacher(Long id) {
        AppUser teacher = new AppUser();
        teacher.setId(id);
        teacher.setRole(UserRole.TEACHER);
        teacher.setDisplayName("Teacher " + id);
        return teacher;
    }

    private AppUser student(Long id) {
        AppUser student = new AppUser();
        student.setId(id);
        student.setRole(UserRole.STUDENT);
        student.setDisplayName("Student " + id);
        return student;
    }

    private Classroom classroom(Long id, String name, Long teacherId) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setName(name);
        classroom.setTeacherId(teacherId);
        return classroom;
    }
}
