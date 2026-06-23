package com.example.words.repository;

import com.example.words.model.StudentWordMemory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentWordMemoryRepository extends JpaRepository<StudentWordMemory, Long> {

    Optional<StudentWordMemory> findByStudentIdAndMetaWordId(Long studentId, Long metaWordId);

    List<StudentWordMemory> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    List<StudentWordMemory> findByStudentIdAndAutoWrongTrueOrderByUpdatedAtDesc(Long studentId);

    List<StudentWordMemory> findByStudentIdAndFavoriteTrueOrderByUpdatedAtDesc(Long studentId);
}
