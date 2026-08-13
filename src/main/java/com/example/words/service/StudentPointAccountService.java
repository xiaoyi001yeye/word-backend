package com.example.words.service;

import com.example.words.model.StudentPointAccount;
import com.example.words.repository.StudentPointAccountRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentPointAccountService {

    private final StudentPointAccountRepository studentPointAccountRepository;

    public StudentPointAccountService(StudentPointAccountRepository studentPointAccountRepository) {
        this.studentPointAccountRepository = studentPointAccountRepository;
    }

    public void createForStudent(Long studentId) {
        studentPointAccountRepository.save(StudentPointAccount.create(studentId));
    }

    @Transactional
    public StudentPointAccount getOrCreateForStudent(Long studentId) {
        return studentPointAccountRepository.findByStudentId(studentId)
                .orElseGet(() -> createMissingAccount(studentId));
    }

    @Transactional
    public List<StudentPointAccount> getOrCreateForStudents(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctStudentIds = studentIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long, StudentPointAccount> accounts = studentPointAccountRepository.findAllByStudentIdIn(distinctStudentIds)
                .stream()
                .collect(Collectors.toMap(StudentPointAccount::getStudentId, Function.identity()));
        for (Long studentId : distinctStudentIds) {
            accounts.computeIfAbsent(studentId, this::createMissingAccount);
        }
        return distinctStudentIds.stream()
                .map(accounts::get)
                .toList();
    }

    private StudentPointAccount createMissingAccount(Long studentId) {
        try {
            return studentPointAccountRepository.save(StudentPointAccount.create(studentId));
        } catch (DataIntegrityViolationException ex) {
            return studentPointAccountRepository.findByStudentId(studentId)
                    .orElseThrow(() -> ex);
        }
    }
}
