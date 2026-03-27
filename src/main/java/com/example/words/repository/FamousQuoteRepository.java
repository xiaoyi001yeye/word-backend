package com.example.words.repository;

import com.example.words.model.FamousQuote;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FamousQuoteRepository extends JpaRepository<FamousQuote, Long> {

    @Query(value = "SELECT * FROM famous_quotes ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<FamousQuote> findRandomQuote();
}
