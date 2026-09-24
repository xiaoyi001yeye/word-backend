package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateClassroomRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    private Long teacherId;

    private Long companionVideoId;

    private List<String> companionImageUrls;

    private List<String> companionTags;

    public CreateClassroomRequest(String name, String description, Long teacherId) {
        this.name = name;
        this.description = description;
        this.teacherId = teacherId;
    }
}
