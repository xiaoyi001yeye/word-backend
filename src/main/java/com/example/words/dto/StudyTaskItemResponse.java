package com.example.words.dto;

import com.example.words.model.StudyTaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyTaskItemResponse {

    private Long metaWordId;
    private String word;
    private String translation;
    private String phonetic;
    private StudyTaskType taskType;
    private Integer phase;
}
