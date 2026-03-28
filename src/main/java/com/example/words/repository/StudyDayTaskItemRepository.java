package com.example.words.repository;

import com.example.words.model.StudyDayTaskItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyDayTaskItemRepository extends JpaRepository<StudyDayTaskItem, Long> {

    List<StudyDayTaskItem> findByStudyDayTaskIdOrderByTaskOrderAsc(Long studyDayTaskId);

    List<StudyDayTaskItem> findByStudyDayTaskIdAndMetaWordIdOrderByCreatedAtAsc(Long studyDayTaskId, Long metaWordId);

    long countByStudyDayTaskIdAndCompletedAtIsNotNull(Long studyDayTaskId);
}
