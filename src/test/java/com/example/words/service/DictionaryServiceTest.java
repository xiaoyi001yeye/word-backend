package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryAssignment;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryAssignmentRepository;
import com.example.words.repository.DictionaryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DictionaryServiceTest {

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private DictionaryAssignmentService dictionaryAssignmentService;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private DictionaryAssignmentRepository dictionaryAssignmentRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;

    private DictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        dictionaryService = new DictionaryService(
                dictionaryRepository,
                dictionaryAssignmentService,
                accessControlService,
                dictionaryAssignmentRepository,
                classroomRepository,
                classroomMemberRepository
        );
    }

    @Test
    void findVisibleDictionariesForClassroomsShouldReturnSingleClassroomCommonDictionaries() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        Dictionary dictionary1 = dictionary(1L, "高考核心", ResourceScopeType.SYSTEM);
        Dictionary dictionary2 = dictionary(2L, "高考进阶", ResourceScopeType.SYSTEM);
        Dictionary dictionary3 = dictionary(3L, "阅读专项", ResourceScopeType.SYSTEM);
        Classroom classroom = new Classroom(100L, "一班", null, 7L, null, null);

        when(dictionaryRepository.findAll()).thenReturn(List.of(dictionary1, dictionary2, dictionary3));
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomMemberRepository.findByClassroomIdIn(List.of(100L))).thenReturn(List.of(
                new ClassroomMember(1L, 100L, 11L, null),
                new ClassroomMember(2L, 100L, 12L, null)
        ));
        when(dictionaryAssignmentRepository.findByStudentIdIn(anyCollection())).thenReturn(List.of(
                new DictionaryAssignment(1L, 1L, 11L, 7L, null),
                new DictionaryAssignment(2L, 2L, 11L, 7L, null),
                new DictionaryAssignment(3L, 1L, 12L, 7L, null),
                new DictionaryAssignment(4L, 2L, 12L, 7L, null),
                new DictionaryAssignment(5L, 3L, 12L, 7L, null)
        ));

        List<Dictionary> result = dictionaryService.findVisibleDictionariesForClassrooms(List.of(100L), teacher);

        assertEquals(List.of(1L, 2L), result.stream().map(Dictionary::getId).toList());
    }

    @Test
    void findVisibleDictionariesForClassroomsShouldIntersectAcrossMultipleClassrooms() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        Dictionary dictionary1 = dictionary(1L, "高考核心", ResourceScopeType.SYSTEM);
        Dictionary dictionary2 = dictionary(2L, "高考进阶", ResourceScopeType.SYSTEM);
        Dictionary dictionary3 = dictionary(3L, "阅读专项", ResourceScopeType.SYSTEM);
        Classroom classroom1 = new Classroom(100L, "一班", null, 7L, null, null);
        Classroom classroom2 = new Classroom(101L, "二班", null, 7L, null, null);

        when(dictionaryRepository.findAll()).thenReturn(List.of(dictionary1, dictionary2, dictionary3));
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom1));
        when(classroomRepository.findById(101L)).thenReturn(Optional.of(classroom2));
        when(classroomMemberRepository.findByClassroomIdIn(List.of(100L, 101L))).thenReturn(List.of(
                new ClassroomMember(1L, 100L, 11L, null),
                new ClassroomMember(2L, 100L, 12L, null),
                new ClassroomMember(3L, 101L, 21L, null),
                new ClassroomMember(4L, 101L, 22L, null)
        ));
        when(dictionaryAssignmentRepository.findByStudentIdIn(anyCollection())).thenReturn(List.of(
                new DictionaryAssignment(1L, 1L, 11L, 7L, null),
                new DictionaryAssignment(2L, 2L, 11L, 7L, null),
                new DictionaryAssignment(3L, 1L, 12L, 7L, null),
                new DictionaryAssignment(4L, 2L, 12L, 7L, null),
                new DictionaryAssignment(5L, 3L, 12L, 7L, null),
                new DictionaryAssignment(6L, 2L, 21L, 7L, null),
                new DictionaryAssignment(7L, 3L, 21L, 7L, null),
                new DictionaryAssignment(8L, 2L, 22L, 7L, null)
        ));

        List<Dictionary> result = dictionaryService.findVisibleDictionariesForClassrooms(List.of(100L, 101L), teacher);

        assertEquals(List.of(2L), result.stream().map(Dictionary::getId).toList());
    }

    private Dictionary dictionary(Long id, String name, ResourceScopeType scopeType) {
        Dictionary dictionary = new Dictionary();
        dictionary.setId(id);
        dictionary.setName(name);
        dictionary.setScopeType(scopeType);
        dictionary.setOwnerUserId(7L);
        dictionary.setCreatedBy(7L);
        return dictionary;
    }
}
