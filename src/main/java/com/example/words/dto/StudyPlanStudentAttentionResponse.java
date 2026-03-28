package com.example.words.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanStudentAttentionResponse {

    private Long studentId;
    private String studentName;
    private Long planId;
    private List<StudentAttentionDailyStatResponse> dailyStats;
}
