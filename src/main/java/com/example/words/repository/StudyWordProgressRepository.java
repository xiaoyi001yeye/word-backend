package com.example.words.repository;

import com.example.words.model.StudyWordProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyWordProgressRepository extends JpaRepository<StudyWordProgress, Long> {

    List<StudyWordProgress> findByStudentStudyPlanId(Long studentStudyPlanId);

    Optional<StudyWordProgress> findByStudentStudyPlanIdAndMetaWordId(Long studentStudyPlanId, Long metaWordId);

    long countByStudentStudyPlanId(Long studentStudyPlanId);

    long countByStudentStudyPlanIdAndLastReviewAtIsNotNull(Long studentStudyPlanId);
}
