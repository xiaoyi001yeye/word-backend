package com.example.words.service;

import com.example.words.dto.StudentPointSummaryResponse;
import com.example.words.dto.StudentPointTransactionResponse;
import com.example.words.dto.TeacherStudentPointResponse;
import com.example.words.dto.UserResponse;
import com.example.words.exception.StudentPointOperationException;
import com.example.words.model.StudentPointAccount;
import com.example.words.repository.StudentPointAccountRepository;
import com.example.words.repository.StudentPointTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentPointQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StudentPointAccountRepository accountRepository;
    private final StudentPointAccountService accountService;
    private final StudentPointTransactionRepository transactionRepository;
    private final TeacherStudentService teacherStudentService;

    @Transactional
    public StudentPointSummaryResponse getSummary(Long studentId) {
        StudentPointAccount account = requireAccount(studentId);
        return StudentPointSummaryResponse.from(account, todayEarned(studentId));
    }

    @Transactional
    public Page<StudentPointTransactionResponse> getTransactions(Long studentId, int page, int size) {
        requireAccount(studentId);
        return transactionRepository.findByStudentId(studentId, page(page, size))
                .map(StudentPointTransactionResponse::from);
    }

    @Transactional
    public Page<TeacherStudentPointResponse> getManagedStudents(
            Long teacherId,
            int page,
            int size,
            String name
    ) {
        Page<UserResponse> students = teacherStudentService.getStudentsForTeacher(teacherId, page + 1, size, name);
        List<Long> studentIds = students.stream().map(UserResponse::getId).toList();
        Map<Long, StudentPointAccount> accounts = accountService.getOrCreateForStudents(studentIds).stream()
                .collect(Collectors.toMap(StudentPointAccount::getStudentId, Function.identity()));
        Map<Long, BigDecimal> todayEarned = todayEarned(studentIds);
        return students.map(student -> {
            StudentPointAccount account = accounts.get(student.getId());
            return new TeacherStudentPointResponse(
                    student.getId(),
                    student.getDisplayName(),
                    account.getAvailablePoints(),
                    account.getLifetimeEarnedPoints(),
                    account.getLifetimeSpentPoints(),
                    todayEarned.getOrDefault(student.getId(), BigDecimal.ZERO)
            );
        });
    }

    @Transactional
    public StudentPointSummaryResponse getManagedStudentSummary(Long teacherId, Long studentId) {
        requireManagedStudent(teacherId, studentId);
        return getSummary(studentId);
    }

    @Transactional
    public Page<StudentPointTransactionResponse> getManagedStudentTransactions(
            Long teacherId,
            Long studentId,
            int page,
            int size
    ) {
        requireManagedStudent(teacherId, studentId);
        return getTransactions(studentId, page, size);
    }

    public Pageable page(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private void requireManagedStudent(Long teacherId, Long studentId) {
        if (!teacherStudentService.isTeacherResponsibleForStudent(teacherId, studentId)) {
            throw error("POINT_STUDENT_NOT_MANAGED", HttpStatus.FORBIDDEN,
                    "Teacher is not responsible for this student");
        }
    }

    private StudentPointAccount requireAccount(Long studentId) {
        return accountRepository.findByStudentId(studentId)
                .orElseGet(() -> accountService.getOrCreateForStudent(studentId));
    }

    private BigDecimal todayEarned(Long studentId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return transactionRepository.sumEarnedByStudentIdBetween(studentId, start, start.plusDays(1));
    }

    private Map<Long, BigDecimal> todayEarned(java.util.List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return transactionRepository.sumEarnedByStudentIdsBetween(studentIds, start, start.plusDays(1)).stream()
                .collect(Collectors.toMap(
                        StudentPointTransactionRepository.StudentPointEarnedTotal::getStudentId,
                        StudentPointTransactionRepository.StudentPointEarnedTotal::getTotal
                ));
    }

    private StudentPointOperationException error(String code, HttpStatus status, String message) {
        return new StudentPointOperationException(code, status, message);
    }
}
