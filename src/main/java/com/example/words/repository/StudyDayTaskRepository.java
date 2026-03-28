package com.example.words.repository;

import com.example.words.model.StudyDayTask;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyDayTaskRepository extends JpaRepository<StudyDayTask, Long> {

    List<StudyDayTask> findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(
            Long studentStudyPlanId,
            LocalDate taskDate);

    List<StudyDayTask> findByStudentStudyPlanIdAndTaskDateBeforeOrderByTaskDateAsc(
            Long studentStudyPlanId,
            LocalDate taskDate);
}
