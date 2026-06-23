package com.example.words.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.model.AppUser;
import com.example.words.model.ClassroomDictionaryAssignment;
import com.example.words.model.ClassroomMember;
import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.model.StudentWordMemory;
import com.example.words.model.StudentWordMemoryEvent;
import com.example.words.model.StudentWordMemorySourceType;
import com.example.words.model.StudyRecordResult;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.DictionaryAssignmentRepository;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.StudentWordMemoryEventRepository;
import com.example.words.repository.StudentWordMemoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

class StudentWordMemoryServiceTest {

    private StudentWordMemoryRepository memoryRepository;
    private StudentWordMemoryEventRepository eventRepository;
    private MetaWordRepository metaWordRepository;
    private DictionaryAssignmentRepository dictionaryAssignmentRepository;
    private DictionaryWordRepository dictionaryWordRepository;
    private ClassroomMemberRepository classroomMemberRepository;
    private ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;
    private StudentWordMemoryService service;

    @BeforeEach
    void setUp() {
        memoryRepository = org.mockito.Mockito.mock(StudentWordMemoryRepository.class);
        eventRepository = org.mockito.Mockito.mock(StudentWordMemoryEventRepository.class);
        metaWordRepository = org.mockito.Mockito.mock(MetaWordRepository.class);
        dictionaryAssignmentRepository = org.mockito.Mockito.mock(DictionaryAssignmentRepository.class);
        dictionaryWordRepository = org.mockito.Mockito.mock(DictionaryWordRepository.class);
        classroomMemberRepository = org.mockito.Mockito.mock(ClassroomMemberRepository.class);
        classroomDictionaryAssignmentRepository = org.mockito.Mockito.mock(ClassroomDictionaryAssignmentRepository.class);
        service = new StudentWordMemoryService(
                memoryRepository,
                eventRepository,
                metaWordRepository,
                dictionaryAssignmentRepository,
                dictionaryWordRepository,
                classroomMemberRepository,
                classroomDictionaryAssignmentRepository
        );
    }

    @Test
    void recordPlanStudyMarksIncorrectWordAsAutoWrongAndWritesEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 9, 30);
        when(memoryRepository.findByStudentIdAndMetaWordId(7L, 11L)).thenReturn(Optional.empty());
        when(memoryRepository.save(any(StudentWordMemory.class))).thenAnswer(invocation -> {
            StudentWordMemory memory = invocation.getArgument(0);
            memory.setId(99L);
            return memory;
        });

        StudentWordMemory memory = service.recordPlanStudy(
                7L,
                11L,
                101L,
                3L,
                StudyRecordResult.INCORRECT,
                now
        );

        assertThat(memory.getStudentId()).isEqualTo(7L);
        assertThat(memory.getMetaWordId()).isEqualTo(11L);
        assertThat(memory.getBoxLevel()).isZero();
        assertThat(memory.getWrongTimes()).isEqualTo(1);
        assertThat(memory.getCorrectTimes()).isZero();
        assertThat(memory.getCorrectStreak()).isZero();
        assertThat(memory.getAutoWrong()).isTrue();
        assertThat(memory.getLastResult()).isEqualTo(StudyRecordResult.INCORRECT);
        assertThat(memory.getLastSource()).isEqualTo(StudentWordMemorySourceType.PLAN_STUDY);
        assertThat(memory.getNextReviewDate()).isEqualTo(now.toLocalDate());

        ArgumentCaptor<StudentWordMemoryEvent> eventCaptor = ArgumentCaptor.forClass(StudentWordMemoryEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        StudentWordMemoryEvent event = eventCaptor.getValue();
        assertThat(event.getStudentWordMemoryId()).isEqualTo(99L);
        assertThat(event.getStudentId()).isEqualTo(7L);
        assertThat(event.getMetaWordId()).isEqualTo(11L);
        assertThat(event.getSourceType()).isEqualTo(StudentWordMemorySourceType.PLAN_STUDY);
        assertThat(event.getSourceId()).isEqualTo(101L);
        assertThat(event.getDictionaryId()).isEqualTo(3L);
        assertThat(event.getResult()).isEqualTo(StudyRecordResult.INCORRECT);
        assertThat(event.getBoxLevelBefore()).isZero();
        assertThat(event.getBoxLevelAfter()).isZero();
    }

    @Test
    void recordPlanStudyTreatsSkippedWordAsAutoWrong() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 9, 45);
        when(memoryRepository.findByStudentIdAndMetaWordId(7L, 11L)).thenReturn(Optional.empty());
        when(memoryRepository.save(any(StudentWordMemory.class))).thenAnswer(invocation -> {
            StudentWordMemory memory = invocation.getArgument(0);
            memory.setId(100L);
            return memory;
        });

        StudentWordMemory memory = service.recordPlanStudy(
                7L,
                11L,
                101L,
                3L,
                StudyRecordResult.SKIPPED,
                now
        );

        assertThat(memory.getBoxLevel()).isZero();
        assertThat(memory.getWrongTimes()).isEqualTo(1);
        assertThat(memory.getCorrectTimes()).isZero();
        assertThat(memory.getCorrectStreak()).isZero();
        assertThat(memory.getAutoWrong()).isTrue();
        assertThat(memory.getLastResult()).isEqualTo(StudyRecordResult.SKIPPED);
        assertThat(memory.getNextReviewDate()).isEqualTo(now.toLocalDate());
    }

    @Test
    void recordPlanStudyClearsAutoWrongAfterHighBoxCorrectStreak() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 0);
        StudentWordMemory existing = new StudentWordMemory();
        existing.setId(77L);
        existing.setStudentId(7L);
        existing.setMetaWordId(11L);
        existing.setBoxLevel(5);
        existing.setCorrectTimes(4);
        existing.setWrongTimes(2);
        existing.setCorrectStreak(2);
        existing.setAutoWrong(true);

        when(memoryRepository.findByStudentIdAndMetaWordId(7L, 11L)).thenReturn(Optional.of(existing));
        when(memoryRepository.save(any(StudentWordMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentWordMemory memory = service.recordPlanStudy(
                7L,
                11L,
                102L,
                3L,
                StudyRecordResult.CORRECT,
                now
        );

        assertThat(memory.getBoxLevel()).isEqualTo(6);
        assertThat(memory.getCorrectTimes()).isEqualTo(5);
        assertThat(memory.getWrongTimes()).isEqualTo(2);
        assertThat(memory.getCorrectStreak()).isEqualTo(3);
        assertThat(memory.getAutoWrong()).isFalse();
        assertThat(memory.getNextReviewDate()).isEqualTo(now.toLocalDate().plusDays(30));
    }

    @Test
    void updateFavoriteRejectsWordsOutsideAssignedDictionaries() {
        AppUser student = student(7L);
        MetaWord metaWord = metaWord(11L, "adapt");
        DictionaryWord dictionaryWord = new DictionaryWord();
        dictionaryWord.setDictionaryId(44L);
        dictionaryWord.setMetaWordId(11L);
        when(metaWordRepository.findById(11L)).thenReturn(Optional.of(metaWord));
        when(dictionaryWordRepository.findByMetaWordId(11L)).thenReturn(List.of(dictionaryWord));
        when(dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(44L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateFavorite(11L, true, student))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("assigned dictionary");

        verify(memoryRepository, never()).save(any(StudentWordMemory.class));
    }

    @Test
    void updateFavoriteCreatesMemoryForAssignedVisibleWord() {
        AppUser student = student(7L);
        MetaWord metaWord = metaWord(11L, "adapt");
        DictionaryWord dictionaryWord = new DictionaryWord();
        dictionaryWord.setDictionaryId(44L);
        dictionaryWord.setMetaWordId(11L);
        when(metaWordRepository.findById(11L)).thenReturn(Optional.of(metaWord));
        when(dictionaryWordRepository.findByMetaWordId(11L)).thenReturn(List.of(dictionaryWord));
        when(dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(44L, 7L)).thenReturn(true);
        when(memoryRepository.findByStudentIdAndMetaWordId(7L, 11L)).thenReturn(Optional.empty());
        when(memoryRepository.save(any(StudentWordMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentWordMemory memory = service.updateFavorite(11L, true, student);

        assertThat(memory.getStudentId()).isEqualTo(7L);
        assertThat(memory.getMetaWordId()).isEqualTo(11L);
        assertThat(memory.getFavorite()).isTrue();
        assertThat(memory.getAutoWrong()).isFalse();
    }

    @Test
    void updateFavoriteCreatesMemoryForClassroomAssignedVisibleWord() {
        AppUser student = student(7L);
        MetaWord metaWord = metaWord(11L, "adapt");
        DictionaryWord dictionaryWord = new DictionaryWord();
        dictionaryWord.setDictionaryId(44L);
        dictionaryWord.setMetaWordId(11L);
        when(metaWordRepository.findById(11L)).thenReturn(Optional.of(metaWord));
        when(dictionaryWordRepository.findByMetaWordId(11L)).thenReturn(List.of(dictionaryWord));
        when(dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(44L, 7L)).thenReturn(false);
        ClassroomMember classroomMember = new ClassroomMember();
        classroomMember.setClassroomId(3L);
        classroomMember.setStudentId(7L);
        when(classroomMemberRepository.findByStudentId(7L)).thenReturn(List.of(classroomMember));
        when(classroomDictionaryAssignmentRepository.existsByDictionaryIdAndClassroomIdIn(44L, List.of(3L)))
                .thenReturn(true);
        when(memoryRepository.findByStudentIdAndMetaWordId(7L, 11L)).thenReturn(Optional.empty());
        when(memoryRepository.save(any(StudentWordMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentWordMemory memory = service.updateFavorite(11L, true, student);

        assertThat(memory.getStudentId()).isEqualTo(7L);
        assertThat(memory.getMetaWordId()).isEqualTo(11L);
        assertThat(memory.getFavorite()).isTrue();
    }

    @Test
    void updateFavoriteKeepsAutoWrongStateWhenRemovingFavorite() {
        AppUser student = student(7L);
        MetaWord metaWord = metaWord(11L, "adapt");
        DictionaryWord dictionaryWord = new DictionaryWord();
        dictionaryWord.setDictionaryId(44L);
        dictionaryWord.setMetaWordId(11L);
        StudentWordMemory existing = new StudentWordMemory();
        existing.setId(80L);
        existing.setStudentId(7L);
        existing.setMetaWordId(11L);
        existing.setAutoWrong(true);
        existing.setFavorite(true);
        when(metaWordRepository.findById(11L)).thenReturn(Optional.of(metaWord));
        when(dictionaryWordRepository.findByMetaWordId(11L)).thenReturn(List.of(dictionaryWord));
        when(dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(44L, 7L)).thenReturn(true);
        when(memoryRepository.findByStudentIdAndMetaWordId(7L, 11L)).thenReturn(Optional.of(existing));
        when(memoryRepository.save(any(StudentWordMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentWordMemory memory = service.updateFavorite(11L, false, student);

        assertThat(memory.getFavorite()).isFalse();
        assertThat(memory.getAutoWrong()).isTrue();
    }

    @Test
    void listWrongWordsReturnsAutoWrongMemoriesWithWordDetails() {
        AppUser student = student(7L);
        StudentWordMemory memory = new StudentWordMemory();
        memory.setStudentId(7L);
        memory.setMetaWordId(11L);
        memory.setBoxLevel(2);
        memory.setWrongTimes(3);
        memory.setCorrectTimes(1);
        memory.setCorrectStreak(0);
        memory.setAutoWrong(true);
        memory.setFavorite(false);
        MetaWord metaWord = metaWord(11L, "adapt");
        metaWord.setTranslation("适应");
        when(memoryRepository.findByStudentIdAndAutoWrongTrueOrderByUpdatedAtDesc(7L)).thenReturn(List.of(memory));
        when(metaWordRepository.findById(11L)).thenReturn(Optional.of(metaWord));

        List<com.example.words.dto.StudentWordMemoryResponse> responses = service.listWrongWords(student);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getMetaWordId()).isEqualTo(11L);
        assertThat(responses.get(0).getWord()).isEqualTo("adapt");
        assertThat(responses.get(0).getTranslation()).isEqualTo("适应");
        assertThat(responses.get(0).getAutoWrong()).isTrue();
    }

    private AppUser student(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("student" + id);
        user.setDisplayName("Student " + id);
        user.setPasswordHash("hash");
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private MetaWord metaWord(Long id, String word) {
        MetaWord metaWord = new MetaWord();
        metaWord.setId(id);
        metaWord.setWord(word);
        metaWord.setDefinition("definition");
        return metaWord;
    }
}
