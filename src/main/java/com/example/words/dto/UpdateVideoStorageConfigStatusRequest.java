package com.example.words.dto;

import com.example.words.model.VideoStorageConfigStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVideoStorageConfigStatusRequest {

    @NotNull(message = "status is required")
    private VideoStorageConfigStatus status;
}
