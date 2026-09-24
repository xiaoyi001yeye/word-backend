package com.example.words.repository;

import com.example.words.model.ClassroomCompanionLike;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomCompanionLikeRepository extends JpaRepository<ClassroomCompanionLike, Long> {

    boolean existsByClassroomIdAndUserId(Long classroomId, Long userId);

    List<ClassroomCompanionLike> findTop6ByClassroomIdOrderByCreatedAtDesc(Long classroomId);

    long countByClassroomId(Long classroomId);

    @Modifying
    @Query(value = "INSERT INTO classroom_companion_likes (classroom_id, user_id) VALUES (:classroomId, :userId) "
            + "ON CONFLICT (classroom_id, user_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("classroomId") Long classroomId, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ClassroomCompanionLike item WHERE item.classroomId = :classroomId")
    int deleteByClassroomId(@Param("classroomId") Long classroomId);
}
