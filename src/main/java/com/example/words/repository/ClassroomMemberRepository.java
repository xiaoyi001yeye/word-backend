package com.example.words.repository;

import com.example.words.model.ClassroomMember;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomMemberRepository extends JpaRepository<ClassroomMember, Long> {

    List<ClassroomMember> findByClassroomId(Long classroomId);

    List<ClassroomMember> findByClassroomIdIn(Collection<Long> classroomIds);

    List<ClassroomMember> findByStudentId(Long studentId);

    List<ClassroomMember> findByStudentIdIn(Collection<Long> studentIds);

    boolean existsByClassroomIdAndStudentId(Long classroomId, Long studentId);

    boolean existsByClassroomIdInAndStudentId(Collection<Long> classroomIds, Long studentId);

    long countByClassroomId(Long classroomId);

    void deleteByClassroomIdAndStudentId(Long classroomId, Long studentId);
}
