package com.example.words.repository;

import com.example.words.model.StudyRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyRecordRepository extends JpaRepository<StudyRecord, Long> {

    List<StudyRecord> findByStudentStudyPlanIdAndTaskDate(Long studentStudyPlanId, LocalDate taskDate);
}
