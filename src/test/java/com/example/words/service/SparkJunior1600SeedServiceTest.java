package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.words.exception.BadRequestException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryCreationType;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.ReviewMode;
import com.example.words.model.StudentStudyPlan;
import com.example.words.model.StudyPlan;
import com.example.words.model.StudyPlanStatus;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryAssignmentRepository;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.StudentStudyPlanRepository;
import com.example.words.repository.StudyPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@Import(SparkJunior1600SeedService.class)
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                TransactionalTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class SparkJunior1600SeedServiceTest {

    @Autowired
    private SparkJunior1600SeedService seedService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomMemberRepository classroomMemberRepository;

    @Autowired
    private ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;

    @Autowired
    private DictionaryAssignmentRepository dictionaryAssignmentRepository;

    @Autowired
    private DictionaryRepository dictionaryRepository;

    @Autowired
    private StudentStudyPlanRepository studentStudyPlanRepository;

    @Autowired
    private StudyPlanRepository studyPlanRepository;

    @Test
    void resetShouldCreateFixedDataAndRemainIdempotent() {
        Dictionary dictionary = saveSparkDictionary();
        StudyPlan outsiderPlan = saveOutsiderPlan(dictionary.getId());

        SparkJunior1600SeedService.SeedResult firstResult = seedService.reset();
        SparkJunior1600SeedService.SeedResult secondResult = seedService.reset();

        AppUser teacher = requireUser("spark_teacher");
        AppUser student1 = requireUser("spark_student_1");
        AppUser student2 = requireUser("spark_student_2");
        List<Classroom> classrooms = classroomRepository.findByTeacherId(teacher.getId());
        List<StudyPlan> studyPlans = studyPlanRepository.findByTeacherIdOrderByCreatedAtDesc(teacher.getId());

        assertNotNull(firstResult);
        assertNotNull(secondResult);
        assertEquals(3, countSeedUsers());
        assertEquals(UserRole.TEACHER, teacher.getRole());
        assertEquals(UserRole.STUDENT, student1.getRole());
        assertEquals(UserRole.STUDENT, student2.getRole());
        assertEquals(1, classrooms.size());
        assertEquals("星火初中英语词汇1600词班", classrooms.get(0).getName());
        assertEquals(2, classroomMemberRepository.countByClassroomId(classrooms.get(0).getId()));
        assertTrue(classroomDictionaryAssignmentRepository.existsByClassroomIdAndDictionaryId(
                classrooms.get(0).getId(),
                dictionary.getId()));
        assertEquals(1, studyPlans.size());
        assertEquals("25天星火初中英语词汇1600词计划", studyPlans.get(0).getName());
        assertEquals(StudyPlanStatus.PUBLISHED, studyPlans.get(0).getStatus());
        assertEquals(64, studyPlans.get(0).getDailyNewCount());
        assertEquals(24, studyPlans.get(0).getEndDate().toEpochDay() - studyPlans.get(0).getStartDate().toEpochDay());
        assertEquals(2, studentStudyPlanRepository
                .findByStudyPlanIdOrderByStudentIdAsc(studyPlans.get(0).getId())
                .size());
        assertTrue(dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(
                dictionary.getId(),
                student1.getId()));
        assertTrue(dictionaryAssignmentRepository.existsByDictionaryIdAndStudentId(
                dictionary.getId(),
                student2.getId()));
        assertTrue(studyPlanRepository.findById(outsiderPlan.getId()).isPresent());
        assertTrue(appUserRepository.findByUsername("other_teacher").isPresent());
    }

    @Test
    void resetShouldFailWithoutDictionaryAndLeaveNoPartialSeedData() {
        BadRequestException exception = assertThrows(BadRequestException.class, () -> seedService.reset());

        assertEquals("Required dictionary not found: 星火初中英语词汇1600词", exception.getMessage());
        assertEquals(0, countSeedUsers());
        assertTrue(classroomRepository.findAll().isEmpty());
        assertTrue(studyPlanRepository.findAll().isEmpty());
    }

    private Dictionary saveSparkDictionary() {
        Dictionary dictionary = new Dictionary();
        dictionary.setName("星火初中英语词汇1600词");
        dictionary.setFilePath("/app/books/星火初中英语词汇1600词.csv");
        dictionary.setFileSize(1024L);
        dictionary.setCategory("初中");
        dictionary.setWordCount(2692);
        dictionary.setEntryCount(2692);
        dictionary.setCreationType(DictionaryCreationType.IMPORTED);
        dictionary.setScopeType(ResourceScopeType.SYSTEM);
        return dictionaryRepository.save(dictionary);
    }

    private StudyPlan saveOutsiderPlan(Long dictionaryId) {
        AppUser teacher = saveUser("other_teacher", "Other Teacher", UserRole.TEACHER);
        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setName("其他老师的计划");
        studyPlan.setTeacherId(teacher.getId());
        studyPlan.setDictionaryId(dictionaryId);
        studyPlan.setStartDate(LocalDate.of(2026, 1, 1));
        studyPlan.setEndDate(LocalDate.of(2026, 1, 25));
        studyPlan.setTimezone("Asia/Shanghai");
        studyPlan.setDailyNewCount(10);
        studyPlan.setDailyReviewLimit(20);
        studyPlan.setReviewMode(ReviewMode.EBBINGHAUS);
        studyPlan.setReviewIntervalsJson("[0,1,2,4]");
        studyPlan.setCompletionThreshold(BigDecimal.valueOf(100));
        studyPlan.setDailyDeadlineTime(LocalTime.of(21, 30));
        studyPlan.setAttentionTrackingEnabled(true);
        studyPlan.setMinFocusSecondsPerWord(3);
        studyPlan.setMaxFocusSecondsPerWord(120);
        studyPlan.setLongStayWarningSeconds(60);
        studyPlan.setIdleTimeoutSeconds(15);
        studyPlan.setStatus(StudyPlanStatus.PUBLISHED);
        return studyPlanRepository.save(studyPlan);
    }

    private AppUser saveUser(String username, String displayName, UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("{noop}password");
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return appUserRepository.save(user);
    }

    private AppUser requireUser(String username) {
        return appUserRepository.findByUsername(username).orElseThrow();
    }

    private long countSeedUsers() {
        return List.of("spark_teacher", "spark_student_1", "spark_student_2").stream()
                .filter(username -> appUserRepository.findByUsername(username).isPresent())
                .count();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
