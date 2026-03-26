package com.example.words.service;

import com.example.words.dto.CreateExamRequest;
import com.example.words.dto.ExamAnswerDto;
import com.example.words.dto.ExamHistoryItemDto;
import com.example.words.dto.ExamOptionDto;
import com.example.words.dto.ExamQuestionDto;
import com.example.words.dto.ExamResponse;
import com.example.words.dto.ExamResultQuestionDto;
import com.example.words.dto.SubmitExamRequest;
import com.example.words.dto.SubmitExamResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Definition;
import com.example.words.model.Dictionary;
import com.example.words.model.Exam;
import com.example.words.model.ExamQuestion;
import com.example.words.model.ExamStatus;
import com.example.words.model.MetaWord;
import com.example.words.model.PartOfSpeech;
import com.example.words.repository.ExamQuestionRepository;
import com.example.words.repository.ExamRepository;
import com.example.words.repository.MetaWordRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamService {

    private static final List<String> OPTION_KEYS = List.of("A", "B", "C", "D");

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final DictionaryService dictionaryService;
    private final MetaWordRepository metaWordRepository;
    private final AccessControlService accessControlService;
    private final UserService userService;

    public ExamService(
            ExamRepository examRepository,
            ExamQuestionRepository examQuestionRepository,
            DictionaryService dictionaryService,
            MetaWordRepository metaWordRepository,
            AccessControlService accessControlService,
            UserService userService) {
        this.examRepository = examRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.dictionaryService = dictionaryService;
        this.metaWordRepository = metaWordRepository;
        this.accessControlService = accessControlService;
        this.userService = userService;
    }

    @Transactional
    public ExamResponse createExam(CreateExamRequest request) {
        Dictionary dictionary = dictionaryService.findById(request.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + request.getDictionaryId()));

        List<MetaWord> eligibleDictionaryWords = metaWordRepository.findAllByDictionaryId(request.getDictionaryId()).stream()
                .filter(this::hasUsableMeaning)
                .toList();
        if (eligibleDictionaryWords.size() < request.getQuestionCount()) {
            throw new BadRequestException("Not enough words with translations in the selected dictionary");
        }

        List<MetaWord> distractorPool = metaWordRepository.findAll().stream()
                .filter(this::hasUsableMeaning)
                .toList();

        Set<String> uniqueMeanings = new LinkedHashSet<>();
        for (MetaWord metaWord : distractorPool) {
            uniqueMeanings.add(resolveMeaning(metaWord));
        }
        if (uniqueMeanings.size() < OPTION_KEYS.size()) {
            throw new BadRequestException("Not enough translated words to generate four options");
        }

        List<MetaWord> shuffledWords = new ArrayList<>(eligibleDictionaryWords);
        Collections.shuffle(shuffledWords);
        List<MetaWord> selectedWords = shuffledWords.subList(0, request.getQuestionCount());

        Exam exam = new Exam();
        exam.setDictionaryId(dictionary.getId());
        exam.setQuestionCount(request.getQuestionCount());
        exam.setAnsweredCount(0);
        exam.setCorrectCount(0);
        exam.setScore(0);
        exam.setStatus(ExamStatus.GENERATED);
        Exam savedExam = examRepository.save(exam);

        List<ExamQuestion> questions = new ArrayList<>();
        int order = 1;
        for (MetaWord metaWord : selectedWords) {
            OptionSet optionSet = buildOptionSet(metaWord, distractorPool);

            ExamQuestion question = new ExamQuestion();
            question.setExamId(savedExam.getId());
            question.setMetaWordId(metaWord.getId());
            question.setQuestionOrder(order++);
            question.setWord(metaWord.getWord());
            question.setOptionA(optionSet.options().get(0));
            question.setOptionB(optionSet.options().get(1));
            question.setOptionC(optionSet.options().get(2));
            question.setOptionD(optionSet.options().get(3));
            question.setCorrectOption(optionSet.correctOption());
            questions.add(question);
        }

        List<ExamQuestion> savedQuestions = examQuestionRepository.saveAll(questions);
        return toExamResponse(savedExam, dictionary.getName(), savedQuestions);
    }

    @Transactional
    public ExamResponse createExam(CreateExamRequest request, AppUser actor) {
        userService.getUserEntity(request.getTargetUserId());
        Dictionary dictionary = dictionaryService.findById(request.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + request.getDictionaryId()));
        accessControlService.ensureCanViewDictionary(actor, dictionary);
        accessControlService.ensureCanCreateExamForStudent(actor, request.getTargetUserId());

        ExamResponse response = createExam(request);
        Exam savedExam = examRepository.findById(response.getExamId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + response.getExamId()));
        savedExam.setCreatedByUserId(actor.getId());
        savedExam.setTargetUserId(request.getTargetUserId());
        savedExam.setAssignedAt(LocalDateTime.now());
        examRepository.save(savedExam);
        return getExam(savedExam.getId(), actor);
    }

    @Transactional(readOnly = true)
    public ExamResponse getExam(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));
        Dictionary dictionary = dictionaryService.findById(exam.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + exam.getDictionaryId()));
        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(examId);
        return toExamResponse(exam, dictionary.getName(), questions);
    }

    @Transactional(readOnly = true)
    public ExamResponse getExam(Long examId, AppUser actor) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));
        accessControlService.ensureCanViewExam(actor, exam);
        return getExam(examId);
    }

    @Transactional(readOnly = true)
    public List<ExamHistoryItemDto> getExamHistory(Long dictionaryId) {
        return getExamHistorySource(dictionaryId).stream()
                .map(this::toExamHistoryItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamHistoryItemDto> getExamHistory(Long dictionaryId, AppUser actor) {
        return getExamHistorySource(dictionaryId).stream()
                .filter(exam -> canViewExam(actor, exam))
                .map(this::toExamHistoryItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmitExamResponse getExamResult(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));
        if (exam.getStatus() != ExamStatus.SUBMITTED) {
            throw new BadRequestException("Exam has not been submitted yet");
        }

        Dictionary dictionary = dictionaryService.findById(exam.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + exam.getDictionaryId()));
        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(examId);
        return buildSubmitExamResponse(exam, dictionary.getName(), questions);
    }

    @Transactional(readOnly = true)
    public SubmitExamResponse getExamResult(Long examId, AppUser actor) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));
        accessControlService.ensureCanViewExam(actor, exam);
        return getExamResult(examId);
    }

    @Transactional
    public SubmitExamResponse submitExam(Long examId, SubmitExamRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));
        if (exam.getStatus() == ExamStatus.SUBMITTED) {
            throw new BadRequestException("Exam has already been submitted");
        }

        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderByQuestionOrderAsc(examId);
        Map<Long, String> answers = normalizeAnswers(request.getAnswers(), questions);

        List<ExamResultQuestionDto> results = new ArrayList<>();
        int answeredCount = 0;
        int correctCount = 0;
        for (ExamQuestion question : questions) {
            String selectedOption = answers.get(question.getId());
            question.setSelectedOption(selectedOption);
            if (selectedOption != null) {
                answeredCount++;
            }

            boolean correct = Objects.equals(question.getCorrectOption(), selectedOption);
            if (correct) {
                correctCount++;
            }

            results.add(new ExamResultQuestionDto(
                    question.getId(),
                    question.getWord(),
                    selectedOption,
                    resolveOptionText(question, selectedOption),
                    question.getCorrectOption(),
                    resolveOptionText(question, question.getCorrectOption()),
                    correct
            ));
        }

        int score = questions.isEmpty() ? 0 : (int) Math.round(correctCount * 100.0 / questions.size());
        LocalDateTime submittedAt = LocalDateTime.now();

        exam.setAnsweredCount(answeredCount);
        exam.setCorrectCount(correctCount);
        exam.setScore(score);
        exam.setStatus(ExamStatus.SUBMITTED);
        exam.setSubmittedAt(submittedAt);
        examRepository.save(exam);
        examQuestionRepository.saveAll(questions);

        Dictionary dictionary = dictionaryService.findById(exam.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + exam.getDictionaryId()));

        return new SubmitExamResponse(
                exam.getId(),
                exam.getDictionaryId(),
                dictionary.getName(),
                questions.size(),
                answeredCount,
                correctCount,
                score,
                exam.getStatus().name(),
                submittedAt,
                results
        );
    }

    @Transactional
    public SubmitExamResponse submitExam(Long examId, SubmitExamRequest request, AppUser actor) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found: " + examId));
        accessControlService.ensureCanSubmitExam(actor, exam);
        if (exam.getStartedAt() == null) {
            exam.setStartedAt(LocalDateTime.now());
            examRepository.save(exam);
        }
        return submitExam(examId, request);
    }

    private Map<Long, String> normalizeAnswers(List<ExamAnswerDto> answerDtos, List<ExamQuestion> questions) {
        Map<Long, String> answers = new LinkedHashMap<>();
        Set<Long> validQuestionIds = questions.stream()
                .map(ExamQuestion::getId)
                .collect(java.util.stream.Collectors.toSet());

        if (answerDtos == null) {
            return answers;
        }

        for (ExamAnswerDto answerDto : answerDtos) {
            if (!validQuestionIds.contains(answerDto.getQuestionId())) {
                throw new BadRequestException("Question does not belong to this exam: " + answerDto.getQuestionId());
            }

            String selectedOption = answerDto.getSelectedOption().trim().toUpperCase(Locale.ROOT);
            if (!OPTION_KEYS.contains(selectedOption)) {
                throw new BadRequestException("selectedOption must be one of A, B, C, D");
            }

            if (answers.put(answerDto.getQuestionId(), selectedOption) != null) {
                throw new BadRequestException("Duplicate answer for question: " + answerDto.getQuestionId());
            }
        }
        return answers;
    }

    private ExamResponse toExamResponse(Exam exam, String dictionaryName, List<ExamQuestion> questions) {
        List<ExamQuestionDto> questionDtos = questions.stream()
                .map(this::toQuestionDto)
                .toList();

        return new ExamResponse(
                exam.getId(),
                exam.getDictionaryId(),
                dictionaryName,
                exam.getQuestionCount(),
                exam.getAnsweredCount(),
                exam.getCorrectCount(),
                exam.getScore(),
                exam.getStatus().name(),
                exam.getCreatedAt(),
                exam.getSubmittedAt(),
                questionDtos
        );
    }

    private ExamHistoryItemDto toExamHistoryItem(Exam exam) {
        Dictionary dictionary = dictionaryService.findById(exam.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + exam.getDictionaryId()));
        return new ExamHistoryItemDto(
                exam.getId(),
                exam.getDictionaryId(),
                dictionary.getName(),
                exam.getQuestionCount(),
                exam.getAnsweredCount(),
                exam.getCorrectCount(),
                exam.getScore(),
                exam.getStatus().name(),
                exam.getCreatedAt(),
                exam.getSubmittedAt()
        );
    }

    private List<Exam> getExamHistorySource(Long dictionaryId) {
        return dictionaryId == null
                ? examRepository.findByStatusOrderBySubmittedAtDescCreatedAtDesc(ExamStatus.SUBMITTED)
                : examRepository.findByDictionaryIdAndStatusOrderBySubmittedAtDescCreatedAtDesc(
                        dictionaryId,
                        ExamStatus.SUBMITTED
                );
    }

    private boolean canViewExam(AppUser actor, Exam exam) {
        try {
            accessControlService.ensureCanViewExam(actor, exam);
            return true;
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return false;
        }
    }

    private ExamQuestionDto toQuestionDto(ExamQuestion question) {
        return new ExamQuestionDto(
                question.getId(),
                question.getWord(),
                List.of(
                        new ExamOptionDto("A", question.getOptionA()),
                        new ExamOptionDto("B", question.getOptionB()),
                        new ExamOptionDto("C", question.getOptionC()),
                        new ExamOptionDto("D", question.getOptionD())
                )
        );
    }

    private OptionSet buildOptionSet(MetaWord correctWord, List<MetaWord> distractorPool) {
        String correctMeaning = resolveMeaning(correctWord);
        List<String> distractorMeanings = distractorPool.stream()
                .filter(metaWord -> !Objects.equals(metaWord.getId(), correctWord.getId()))
                .map(this::resolveMeaning)
                .filter(meaning -> !Objects.equals(meaning, correctMeaning))
                .distinct()
                .toList();

        if (distractorMeanings.size() < OPTION_KEYS.size() - 1) {
            throw new BadRequestException("Not enough distractor translations to generate exam");
        }

        List<String> shuffledDistractors = new ArrayList<>(distractorMeanings);
        Collections.shuffle(shuffledDistractors);

        List<String> options = new ArrayList<>(Arrays.asList(
                correctMeaning,
                shuffledDistractors.get(0),
                shuffledDistractors.get(1),
                shuffledDistractors.get(2)
        ));
        Collections.shuffle(options);

        return new OptionSet(options, OPTION_KEYS.get(options.indexOf(correctMeaning)));
    }

    private boolean hasUsableMeaning(MetaWord metaWord) {
        return resolveMeaning(metaWord) != null;
    }

    private String resolveMeaning(MetaWord metaWord) {
        if (metaWord.getTranslation() != null && !metaWord.getTranslation().trim().isEmpty()) {
            return metaWord.getTranslation().trim();
        }
        if (metaWord.getDefinition() != null && !metaWord.getDefinition().trim().isEmpty()) {
            return metaWord.getDefinition().trim();
        }
        if (metaWord.getPartOfSpeechDetail() == null || metaWord.getPartOfSpeechDetail().isEmpty()) {
            return null;
        }

        for (PartOfSpeech partOfSpeech : metaWord.getPartOfSpeechDetail()) {
            if (partOfSpeech.getDefinitions() == null) {
                continue;
            }
            for (Definition definition : partOfSpeech.getDefinitions()) {
                if (definition.getTranslation() != null && !definition.getTranslation().trim().isEmpty()) {
                    return definition.getTranslation().trim();
                }
                if (definition.getDefinition() != null && !definition.getDefinition().trim().isEmpty()) {
                    return definition.getDefinition().trim();
                }
            }
        }
        return null;
    }

    private String resolveOptionText(ExamQuestion question, String optionKey) {
        if (optionKey == null) {
            return null;
        }
        return switch (optionKey) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            case "D" -> question.getOptionD();
            default -> null;
        };
    }

    private SubmitExamResponse buildSubmitExamResponse(Exam exam, String dictionaryName, List<ExamQuestion> questions) {
        List<ExamResultQuestionDto> results = questions.stream()
                .map(question -> new ExamResultQuestionDto(
                        question.getId(),
                        question.getWord(),
                        question.getSelectedOption(),
                        resolveOptionText(question, question.getSelectedOption()),
                        question.getCorrectOption(),
                        resolveOptionText(question, question.getCorrectOption()),
                        Objects.equals(question.getCorrectOption(), question.getSelectedOption())
                ))
                .toList();

        return new SubmitExamResponse(
                exam.getId(),
                exam.getDictionaryId(),
                dictionaryName,
                exam.getQuestionCount(),
                exam.getAnsweredCount(),
                exam.getCorrectCount(),
                exam.getScore(),
                exam.getStatus().name(),
                exam.getSubmittedAt(),
                results
        );
    }

    private record OptionSet(List<String> options, String correctOption) {
    }
}
