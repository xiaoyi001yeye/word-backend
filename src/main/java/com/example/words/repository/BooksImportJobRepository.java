package com.example.words.repository;

import com.example.words.model.BooksImportJob;
import com.example.words.model.BooksImportJobStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BooksImportJobRepository extends JpaRepository<BooksImportJob, String> {

    boolean existsByStatusIn(Collection<BooksImportJobStatus> statuses);

    Optional<BooksImportJob> findTopByOrderByCreatedAtDesc();
}
