package com.example.words.repository;

import com.example.words.model.TeacherStudentRelation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherStudentRelationRepository extends JpaRepository<TeacherStudentRelation, Long> {

    List<TeacherStudentRelation> findByTeacherId(Long teacherId);

    List<TeacherStudentRelation> findByStudentId(Long studentId);

    boolean existsByTeacherIdAndStudentId(Long teacherId, Long studentId);

    void deleteByTeacherIdAndStudentId(Long teacherId, Long studentId);
}
