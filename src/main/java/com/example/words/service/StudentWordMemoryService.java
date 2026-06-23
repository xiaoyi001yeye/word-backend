package com.example.words.service;

import com.example.words.dto.StudentWordMemoryResponse;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.ClassroomMember;
import com.example.words.model.DictionaryWord;
import com.example.words.model.MetaWord;
import com.example.words.model.StudentWordMemory;
import com.example.words.model.StudentWordMemoryEvent;
import com.example.words.model.StudentWordMemorySourceType;
import com.example.words.model.StudyRecordResult;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.DictionaryAssignmentRepository;
import com.example.words.repository.DictionaryWordRepository;
import com.example.words.repository.MetaWordRepository;
import com.example.words.repository.StudentWordMemoryEventRepository;
import com.example.words.repository.StudentWordMemoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentWordMemoryService {

    private static final List<Integer> REVIEW_INTERVALS = List.of(0, 1, 2, 4, 7, 15, 30);
    private static final int AUTO_WRONG_CLEAR_BOX_LEVEL = 5;
    private static final int AUTO_WRONG_CLEAR_STREAK = 3;

    private final StudentWordMemoryRepository memoryRepository;
    private final StudentWordMemoryEventRepository eventRepository;
    private final MetaWordRepository metaWordRepository;
    private final DictionaryAssignmentRepository dictionaryAssignmentRepository;
    private final DictionaryWordRepository dictionaryWordRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;

    public StudentWordMemoryService(
            StudentWordMemoryRepository memoryRepository,
            StudentWordMemoryEventRepository eventRepository,
            MetaWordRepository metaWordRepository,
            DictionaryAssignmentRepository dictionaryAssignmentRepository,
            DictionaryWordRepository dictionaryWordRepository,
            ClassroomMemberRepository classroomMemberRepository,
            ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository) {
        this.memoryRepository = memoryRepository;
        this.eventRepository = eventRepository;
        this.metaWordRepository = metaWordRepository;
        this.dictionaryAssignmentRepository = dictionaryAssignmentRepository;
        this.dictionaryWordRepository = dictionaryWordRepository;
        this.classroomMemberRepository = classroomMemberRepository;
        this.classroomDictionaryAssignmentRepository = classroomDictionaryAssignmentRepository;
    }

    @Transactional
    public StudentWordMemory recordPlanStudy(
            Long studentId,
            Long metaWordId,
            Long sourceId,
            Long dictionaryId,
            StudyRecordResult result,
            LocalDateTime occurredAt) {
        StudentWordMemory memory = getOrCreateMemory(studentId, metaWordId);
        int boxBefore = value(memory.getBoxLevel());

        applyResult(memory, result, occurredAt);
        StudentWordMemory savedMemory = memoryRepository.save(memory);

        StudentWordMemoryEvent event = new StudentWordMemoryEvent();
        event.setStudentWordMemoryId(savedMemory.getId());
        event.setStudentId(studentId);
        event.setMetaWordId(metaWordId);
        event.setSourceType(StudentWordMemorySourceType.PLAN_STUDY);
        event.setSourceId(sourceId);
        event.setDictionaryId(dictionaryId);
        event.setResult(result);
        event.setBoxLevelBefore(boxBefore);
        event.setBoxLevelAfter(value(savedMemory.getBoxLevel()));
        eventRepository.save(event);

        return savedMemory;
    }

    @Transactional(readOnly = true)
    public List<StudentWordMemoryResponse> listMemories(AppUser actor) {
        ensureStudent(actor);
        return toResponses(memoryRepository.findByStudentIdOrderByUpdatedAtDesc(actor.getId()));
    }

    @Transactional(readOnly = true)
    public List<StudentWordMemoryResponse> listWrongWords(AppUser actor) {
        ensureStudent(actor);
        return toResponses(memoryRepository.findByStudentIdAndAutoWrongTrueOrderByUpdatedAtDesc(actor.getId()));
    }

    @Transactional(readOnly = true)
    public List<StudentWordMemoryResponse> listFavoriteWords(AppUser actor) {
        ensureStudent(actor);
        return toResponses(memoryRepository.findByStudentIdAndFavoriteTrueOrderByUpdatedAtDesc(actor.getId()));
    }

    @Transactional
    public StudentWordMemory updateFavorite(Long metaWordId, boolean favorite, AppUser actor) {
        ensureStudent(actor);
        MetaWord metaWord = metaWordRepository.findById(metaWordId)
                .orElseThrow(() -> new ResourceNotFoundException("Meta word not found: " + metaWordId));
        if (!isVisibleToStudent(actor.getId(), metaWord.getId())) {
            throw new AccessDeniedException("Word is not in an assigned dictionary");
        }

        StudentWordMemory memory = getOrCreateMemory(actor.getId(), metaWord.getId());
        memory.setFavorite(favorite);
        return memoryRepository.save(memory);
    }

    @Transactional
    public StudentWordMemoryResponse updateFavoriteResponse(Long metaWordId, boolean favorite, AppUser actor) {
        return toResponse(updateFavorite(metaWordId, favorite, actor));
    }

    private StudentWordMemory getOrCreateMemory(Long studentId, Long metaWordId) {
        return memoryRepository.findByStudentIdAndMetaWordId(studentId, metaWordId)
                .orElseGet(() -> {
                    StudentWordMemory memory = new StudentWordMemory();
                    memory.setStudentId(studentId);
                    memory.setMetaWordId(metaWordId);
                    memory.setBoxLevel(0);
                    memory.setMasteryLevel(BigDecimal.ZERO);
                    memory.setCorrectTimes(0);
                    memory.setWrongTimes(0);
                    memory.setCorrectStreak(0);
                    memory.setAutoWrong(false);
                    memory.setFavorite(false);
                    return memory;
                });
    }

    private void applyResult(StudentWordMemory memory, StudyRecordResult result, LocalDateTime occurredAt) {
        int boxLevel = value(memory.getBoxLevel());
        int correctTimes = value(memory.getCorrectTimes());
        int wrongTimes = value(memory.getWrongTimes());
        int correctStreak = value(memory.getCorrectStreak());

        if (result == StudyRecordResult.CORRECT) {
            boxLevel = Math.min(boxLevel + 1, REVIEW_INTERVALS.size() - 1);
            correctTimes++;
            correctStreak++;
        } else {
            boxLevel = Math.max(0, boxLevel - 1);
            wrongTimes++;
            correctStreak = 0;
            memory.setAutoWrong(true);
        }

        memory.setBoxLevel(boxLevel);
        memory.setCorrectTimes(correctTimes);
        memory.setWrongTimes(wrongTimes);
        memory.setCorrectStreak(correctStreak);
        memory.setLastResult(result);
        memory.setLastSource(StudentWordMemorySourceType.PLAN_STUDY);
        memory.setLastStudiedAt(occurredAt);
        memory.setNextReviewDate(nextReviewDate(occurredAt.toLocalDate(), boxLevel));
        memory.setMasteryLevel(masteryLevel(correctTimes, wrongTimes));

        if (boxLevel >= AUTO_WRONG_CLEAR_BOX_LEVEL && correctStreak >= AUTO_WRONG_CLEAR_STREAK) {
            memory.setAutoWrong(false);
        }
    }

    private boolean isVisibleToStudent(Long studentId, Long metaWordId) {
        return dictionaryWordRepository.findByMetaWordId(metaWordId).stream()
                .map(DictionaryWord::getDictionaryId)
                .anyMatch(dictionaryId -> dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(
                        dictionaryId,
                        studentId
                ) || isAssignedThroughClassroom(studentId, dictionaryId));
    }

    private boolean isAssignedThroughClassroom(Long studentId, Long dictionaryId) {
        List<Long> classroomIds = classroomMemberRepository.findByStudentId(studentId).stream()
                .map(ClassroomMember::getClassroomId)
                .distinct()
                .toList();
        return !classroomIds.isEmpty()
                && classroomDictionaryAssignmentRepository.existsByDictionaryIdAndClassroomIdIn(dictionaryId, classroomIds);
    }

    private List<StudentWordMemoryResponse> toResponses(List<StudentWordMemory> memories) {
        return memories.stream()
                .map(this::toResponse)
                .toList();
    }

    private StudentWordMemoryResponse toResponse(StudentWordMemory memory) {
        MetaWord metaWord = metaWordRepository.findById(memory.getMetaWordId())
                .orElseThrow(() -> new ResourceNotFoundException("Meta word not found: " + memory.getMetaWordId()));
        return new StudentWordMemoryResponse(
                metaWord.getId(),
                metaWord.getWord(),
                metaWord.getPhonetic(),
                metaWord.getPhoneticDetail(),
                metaWord.getSyllableDetail(),
                metaWord.getDefinition(),
                metaWord.getTranslation(),
                metaWord.getPartOfSpeech(),
                metaWord.getExampleSentence(),
                memory.getBoxLevel(),
                memory.getMasteryLevel(),
                memory.getNextReviewDate(),
                memory.getCorrectTimes(),
                memory.getWrongTimes(),
                memory.getCorrectStreak(),
                memory.getLastResult(),
                memory.getLastSource(),
                memory.getAutoWrong(),
                memory.getFavorite(),
                memory.getLastStudiedAt()
        );
    }

    private LocalDate nextReviewDate(LocalDate date, int boxLevel) {
        return date.plusDays(REVIEW_INTERVALS.get(Math.min(boxLevel, REVIEW_INTERVALS.size() - 1)));
    }

    private BigDecimal masteryLevel(int correctTimes, int wrongTimes) {
        int total = correctTimes + wrongTimes;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(correctTimes)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private void ensureStudent(AppUser actor) {
        if (actor.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can access word memory");
        }
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }
}
