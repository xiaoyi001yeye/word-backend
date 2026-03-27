package com.example.words.repository;

import com.example.words.model.DictionaryAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DictionaryAssignmentRepository extends JpaRepository<DictionaryAssignment, Long> {

    List<DictionaryAssignment> findByStudentId(Long studentId);

    List<DictionaryAssignment> findByDictionaryId(Long dictionaryId);

    Optional<DictionaryAssignment> findByDictionaryIdAndStudentId(Long dictionaryId, Long studentId);

    boolean existsByDictionaryIdAndStudentId(Long dictionaryId, Long studentId);
}
