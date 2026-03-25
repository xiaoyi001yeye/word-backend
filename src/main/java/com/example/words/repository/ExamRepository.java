package com.example.words.repository;

import com.example.words.model.Exam;
import com.example.words.model.ExamStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByStatusOrderBySubmittedAtDescCreatedAtDesc(ExamStatus status);

    List<Exam> findByDictionaryIdAndStatusOrderBySubmittedAtDescCreatedAtDesc(Long dictionaryId, ExamStatus status);
}
