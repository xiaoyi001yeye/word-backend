package com.example.words.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "famous_quotes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamousQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quote_text", nullable = false, columnDefinition = "TEXT")
    private String quoteText;

    @Column(name = "quote_translation", columnDefinition = "TEXT")
    private String quoteTranslation;

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
