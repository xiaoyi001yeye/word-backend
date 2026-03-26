package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.CreateExamRequest;
import com.example.words.dto.ExamAnswerDto;
import com.example.words.dto.ExamHistoryItemDto;
import com.example.words.dto.ExamQuestionDto;
import com.example.words.dto.ExamResponse;
import com.example.words.dto.SubmitExamRequest;
import com.example.words.dto.SubmitExamResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.model.Dictionary;
import com.example.words.model.Exam;
import com.example.words.model.ExamQuestion;
import com.example.words.model.ExamStatus;
import com.example.words.model.MetaWord;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.ExamQuestionRepository;
import com.example.words.repository.ExamRepository;
import com.example.words.repository.MetaWordRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private ExamQuestionRepository examQuestionRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private MetaWordRepository metaWordRepository;

    private ExamService examService;
    private DictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        dictionaryService = new DictionaryService(dictionaryRepository);
        examService = new ExamService(examRepository, examQuestionRepository, dictionaryService, metaWordRepository);
    }

    @Test
    void createExamShouldGenerateRequestedQuestionsWithFourOptions() {
        Dictionary dictionary = new Dictionary();
        dictionary.setId(10L);
        dictionary.setName("考研词汇");

        List<MetaWord> dictionaryWords = List.of(
                metaWord(1L, "apple", "苹果"),
                metaWord(2L, "book", "书"),
                metaWord(3L, "chair", "椅子")
        );
        List<MetaWord> globalWords = List.of(
                metaWord(1L, "apple", "苹果"),
                metaWord(2L, "book", "书"),
                metaWord(3L, "chair", "椅子"),
                metaWord(4L, "desk", "桌子"),
                metaWord(5L, "earth", "地球"),
                metaWord(6L, "flower", "花")
        );

        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dictionary));
        when(metaWordRepository.findAllByDictionaryId(10L)).thenReturn(dictionaryWords);
        when(metaWordRepository.findAll()).thenReturn(globalWords);
        when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> {
            Exam exam = invocation.getArgument(0);
            exam.setId(99L);
            exam.setCreatedAt(LocalDateTime.now());
            return exam;
        });
        when(examQuestionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ExamQuestion> questions = invocation.getArgument(0);
            List<ExamQuestion> savedQuestions = new ArrayList<>();
            long id = 100L;
            for (ExamQuestion question : questions) {
                question.setId(id++);
                savedQuestions.add(question);
            }
            return savedQuestions;
        });

        ExamResponse response = examService.createExam(new CreateExamRequest(10L, 2));

        assertEquals(99L, response.getExamId());
        assertEquals("考研词汇", response.getDictionaryName());
        assertEquals(2, response.getQuestionCount());
        assertEquals(2, response.getQuestions().size());

        Set<String> allowedWords = dictionaryWords.stream()
                .map(MetaWord::getWord)
                .collect(Collectors.toSet());
        for (ExamQuestionDto question : response.getQuestions()) {
            assertTrue(allowedWords.contains(question.getWord()));
            assertEquals(4, question.getOptions().size());
            assertEquals(4, question.getOptions().stream().map(option -> option.getKey()).collect(Collectors.toSet()).size());
            assertEquals(4, question.getOptions().stream().map(option -> option.getTranslation()).collect(Collectors.toSet()).size());
        }
    }

    @Test
    void submitExamShouldCalculateScoreFromAnswers() {
        Dictionary dictionary = new Dictionary();
        dictionary.setId(10L);
        dictionary.setName("考研词汇");

        Exam exam = new Exam();
        exam.setId(88L);
        exam.setDictionaryId(10L);
        exam.setQuestionCount(2);
        exam.setStatus(ExamStatus.GENERATED);

        ExamQuestion question1 = examQuestion(101L, 88L, 1, "apple", "苹果", "香蕉", "橘子", "葡萄", "A");
        ExamQuestion question2 = examQuestion(102L, 88L, 2, "book", "书", "桌子", "椅子", "窗户", "A");

        SubmitExamRequest request = new SubmitExamRequest(List.of(
                new ExamAnswerDto(101L, "A"),
                new ExamAnswerDto(102L, "C")
        ));

        when(examRepository.findById(88L)).thenReturn(Optional.of(exam));
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dictionary));
        when(examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(88L)).thenReturn(List.of(question1, question2));
        when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(examQuestionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SubmitExamResponse response = examService.submitExam(88L, request);

        assertEquals("考研词汇", response.getDictionaryName());
        assertEquals(2, response.getTotalQuestions());
        assertEquals(2, response.getAnsweredQuestions());
        assertEquals(1, response.getCorrectCount());
        assertEquals(50, response.getScore());
        assertEquals("SUBMITTED", response.getStatus());
        assertNotNull(response.getSubmittedAt());
        assertEquals("A", response.getResults().get(0).getCorrectOption());
        assertEquals("苹果", response.getResults().get(0).getCorrectTranslation());
        verify(examRepository).save(any(Exam.class));
    }

    @Test
    void submitExamShouldRejectDuplicateAnswers() {
        Exam exam = new Exam();
        exam.setId(88L);
        exam.setDictionaryId(10L);
        exam.setQuestionCount(1);
        exam.setStatus(ExamStatus.GENERATED);

        ExamQuestion question = examQuestion(101L, 88L, 1, "apple", "苹果", "香蕉", "橘子", "葡萄", "A");
        SubmitExamRequest request = new SubmitExamRequest(List.of(
                new ExamAnswerDto(101L, "A"),
                new ExamAnswerDto(101L, "B")
        ));

        when(examRepository.findById(88L)).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(88L)).thenReturn(List.of(question));

        assertThrows(BadRequestException.class, () -> examService.submitExam(88L, request));
    }

    @Test
    void getExamHistoryShouldReturnSubmittedExamsForDictionary() {
        Dictionary dictionary = new Dictionary();
        dictionary.setId(10L);
        dictionary.setName("考研词汇");

        Exam exam = new Exam();
        exam.setId(88L);
        exam.setDictionaryId(10L);
        exam.setQuestionCount(20);
        exam.setAnsweredCount(20);
        exam.setCorrectCount(15);
        exam.setScore(75);
        exam.setStatus(ExamStatus.SUBMITTED);
        exam.setCreatedAt(LocalDateTime.now().minusHours(1));
        exam.setSubmittedAt(LocalDateTime.now());

        when(examRepository.findByDictionaryIdAndStatusOrderBySubmittedAtDescCreatedAtDesc(10L, ExamStatus.SUBMITTED))
                .thenReturn(List.of(exam));
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dictionary));

        List<ExamHistoryItemDto> history = examService.getExamHistory(10L);

        assertEquals(1, history.size());
        assertEquals(88L, history.get(0).getExamId());
        assertEquals("考研词汇", history.get(0).getDictionaryName());
        assertEquals(75, history.get(0).getScore());
    }

    @Test
    void getExamResultShouldReturnPersistedSelections() {
        Dictionary dictionary = new Dictionary();
        dictionary.setId(10L);
        dictionary.setName("考研词汇");

        Exam exam = new Exam();
        exam.setId(88L);
        exam.setDictionaryId(10L);
        exam.setQuestionCount(1);
        exam.setAnsweredCount(1);
        exam.setCorrectCount(1);
        exam.setScore(100);
        exam.setStatus(ExamStatus.SUBMITTED);
        exam.setSubmittedAt(LocalDateTime.now());

        ExamQuestion question = examQuestion(101L, 88L, 1, "apple", "苹果", "香蕉", "橘子", "葡萄", "A");
        question.setSelectedOption("A");

        when(examRepository.findById(88L)).thenReturn(Optional.of(exam));
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dictionary));
        when(examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(88L)).thenReturn(List.of(question));

        SubmitExamResponse result = examService.getExamResult(88L);

        assertEquals("考研词汇", result.getDictionaryName());
        assertEquals(1, result.getResults().size());
        assertEquals("A", result.getResults().get(0).getSelectedOption());
        assertEquals("苹果", result.getResults().get(0).getSelectedTranslation());
    }

    private MetaWord metaWord(Long id, String word, String translation) {
        MetaWord metaWord = new MetaWord();
        metaWord.setId(id);
        metaWord.setWord(word);
        metaWord.setTranslation(translation);
        return metaWord;
    }

    private ExamQuestion examQuestion(
            Long id,
            Long examId,
            Integer order,
            String word,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctOption) {
        ExamQuestion question = new ExamQuestion();
        question.setId(id);
        question.setExamId(examId);
        question.setQuestionOrder(order);
        question.setWord(word);
        question.setOptionA(optionA);
        question.setOptionB(optionB);
        question.setOptionC(optionC);
        question.setOptionD(optionD);
        question.setCorrectOption(correctOption);
        return question;
    }
}
