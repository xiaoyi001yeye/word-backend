package com.example.words.dto;

import com.example.words.model.AiConfigStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAiConfigStatusRequest {

    @NotNull(message = "status is required")
    private AiConfigStatus status;
}
