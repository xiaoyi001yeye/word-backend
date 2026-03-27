package com.example.words.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomResponse {

    private Long id;

    private String name;

    private String description;

    private Long teacherId;

    private String teacherName;

    private long studentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
