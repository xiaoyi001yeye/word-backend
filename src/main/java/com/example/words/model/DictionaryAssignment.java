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
@Table(name = "dictionary_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dictionary_id", nullable = false)
    private Long dictionaryId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "assigned_by_user_id", nullable = false)
    private Long assignedByUserId;

    @Column(name = "assigned_at", insertable = false, updatable = false)
    private LocalDateTime assignedAt;
}
