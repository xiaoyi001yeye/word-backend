package com.example.words.repository;

import com.example.words.model.StudentAttentionDailyStat;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentAttentionDailyStatRepository extends JpaRepository<StudentAttentionDailyStat, Long> {

    List<StudentAttentionDailyStat> findByStudentStudyPlanIdAndTaskDateOrderByCreatedAtAsc(
            Long studentStudyPlanId,
            LocalDate taskDate);

    List<StudentAttentionDailyStat> findByStudentStudyPlanIdOrderByTaskDateDesc(Long studentStudyPlanId);
}
