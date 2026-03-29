package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.Dictionary;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryRepository;
import java.util.LinkedHashSet;
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
    private ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private ClassroomRepository classroomRepository;

    private DictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        dictionaryService = new DictionaryService(
                dictionaryRepository,
                dictionaryAssignmentService,
                classroomDictionaryAssignmentService,
                accessControlService,
                classroomRepository
        );
    }

    @Test
    void findVisibleDictionariesForClassroomsShouldReturnSingleClassroomAssignedDictionaries() {
        AppUser teacher = new AppUser();
        teacher.setId(7L);
        teacher.setRole(UserRole.TEACHER);

        Dictionary dictionary1 = dictionary(1L, "高考核心", ResourceScopeType.SYSTEM);
        Dictionary dictionary2 = dictionary(2L, "高考进阶", ResourceScopeType.SYSTEM);
        Dictionary dictionary3 = dictionary(3L, "阅读专项", ResourceScopeType.SYSTEM);
        Classroom classroom = new Classroom(100L, "一班", null, 7L, null, null);

        when(dictionaryRepository.findAll()).thenReturn(List.of(dictionary1, dictionary2, dictionary3));
        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomDictionaryAssignmentService.intersectAssignedDictionaryIdsForClassrooms(List.of(100L)))
                .thenReturn(new LinkedHashSet<>(List.of(1L, 2L)));
        when(classroomDictionaryAssignmentService.getAssignedDictionaryIdsForTeacher(7L))
                .thenReturn(new LinkedHashSet<>(List.of(1L, 2L)));

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
        when(classroomDictionaryAssignmentService.intersectAssignedDictionaryIdsForClassrooms(List.of(100L, 101L)))
                .thenReturn(new LinkedHashSet<>(List.of(2L)));
        when(classroomDictionaryAssignmentService.getAssignedDictionaryIdsForTeacher(7L))
                .thenReturn(new LinkedHashSet<>(List.of(1L, 2L, 3L)));

        List<Dictionary> result = dictionaryService.findVisibleDictionariesForClassrooms(List.of(100L, 101L), teacher);

        assertEquals(List.of(2L), result.stream().map(Dictionary::getId).toList());
    }

    @Test
    void findAssignedDictionariesForStudentShouldIncludeDirectAndClassroomAssignments() {
        Dictionary dictionary1 = dictionary(1L, "高考核心", ResourceScopeType.SYSTEM);
        Dictionary dictionary2 = dictionary(2L, "高考进阶", ResourceScopeType.SYSTEM);
        Dictionary dictionary3 = dictionary(3L, "阅读专项", ResourceScopeType.SYSTEM);

        when(dictionaryRepository.findAll()).thenReturn(List.of(dictionary1, dictionary2, dictionary3));
        when(dictionaryAssignmentService.getAssignedDictionaryIdsForStudent(88L))
                .thenReturn(new LinkedHashSet<>(List.of(1L)));
        when(classroomDictionaryAssignmentService.getAssignedDictionaryIdsForStudent(88L))
                .thenReturn(new LinkedHashSet<>(List.of(2L, 3L)));

        List<Dictionary> result = dictionaryService.findAssignedDictionariesForStudent(88L);

        assertEquals(List.of(1L, 2L, 3L), result.stream().map(Dictionary::getId).toList());
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
