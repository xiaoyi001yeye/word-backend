package com.example.words.repository;

import com.example.words.model.StudentWordMemoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentWordMemoryEventRepository extends JpaRepository<StudentWordMemoryEvent, Long> {
}
