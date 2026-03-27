package com.example.words.repository;

import com.example.words.model.StudentStudyPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentStudyPlanRepository extends JpaRepository<StudentStudyPlan, Long> {

    List<StudentStudyPlan> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<StudentStudyPlan> findByStudyPlanIdOrderByStudentIdAsc(Long studyPlanId);

    List<StudentStudyPlan> findByStudyPlanIdAndStudentIdOrderByCreatedAtAsc(Long studyPlanId, Long studentId);
}
