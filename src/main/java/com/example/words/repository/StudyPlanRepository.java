package com.example.words.repository;

import com.example.words.model.StudyPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    List<StudyPlan> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
}
