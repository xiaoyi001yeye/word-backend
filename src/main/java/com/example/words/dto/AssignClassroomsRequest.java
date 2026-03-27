package com.example.words.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignClassroomsRequest {

    @NotEmpty(message = "classroomIds cannot be empty")
    private List<Long> classroomIds;
}
