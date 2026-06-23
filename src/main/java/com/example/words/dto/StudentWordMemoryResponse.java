package com.example.words.dto;

import com.example.words.model.Phonetic;
import com.example.words.model.StudentWordMemorySourceType;
import com.example.words.model.StudyRecordResult;
import com.example.words.model.SyllableDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentWordMemoryResponse {

    private Long metaWordId;
    private String word;
    private String phonetic;
    private Phonetic phoneticDetail;
    private SyllableDetail syllableDetail;
    private String definition;
    private String translation;
    private String partOfSpeech;
    private String exampleSentence;
    private Integer boxLevel;
    private BigDecimal masteryLevel;
    private LocalDate nextReviewDate;
    private Integer correctTimes;
    private Integer wrongTimes;
    private Integer correctStreak;
    private StudyRecordResult lastResult;
    private StudentWordMemorySourceType lastSource;
    private Boolean autoWrong;
    private Boolean favorite;
    private LocalDateTime lastStudiedAt;
}
