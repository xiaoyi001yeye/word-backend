package com.example.words.repository;

import com.example.words.model.StudyPlanClassroom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StudyPlanClassroomRepository extends JpaRepository<StudyPlanClassroom, Long> {

    List<StudyPlanClassroom> findByStudyPlanId(Long studyPlanId);

    List<StudyPlanClassroom> findByClassroomId(Long classroomId);

    @Modifying
    @Query("delete from StudyPlanClassroom classroom where classroom.studyPlanId = :studyPlanId")
    void deleteByStudyPlanId(@Param("studyPlanId") Long studyPlanId);

    boolean existsByStudyPlanIdAndClassroomId(Long studyPlanId, Long classroomId);

    boolean existsByClassroomId(Long classroomId);

    @Modifying
    @Query("delete from StudyPlanClassroom classroom where classroom.classroomId = :classroomId")
    void deleteByClassroomId(@Param("classroomId") Long classroomId);
}
