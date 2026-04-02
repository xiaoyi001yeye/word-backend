package com.example.words.dto;

import com.example.words.model.AiConfigStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAiConfigRequest {

    @NotBlank(message = "providerName is required")
    @Size(min = 2, max = 100, message = "providerName length must be between 2 and 100")
    private String providerName;

    @NotBlank(message = "apiUrl is required")
    @Size(max = 500, message = "apiUrl must not exceed 500 characters")
    private String apiUrl;

    @NotBlank(message = "apiKey is required")
    @Size(min = 8, max = 512, message = "apiKey length must be between 8 and 512")
    private String apiKey;

    @NotBlank(message = "modelName is required")
    @Size(max = 100, message = "modelName must not exceed 100 characters")
    private String modelName;

    @NotNull(message = "status is required")
    private AiConfigStatus status;

    @NotNull(message = "isDefault is required")
    private Boolean isDefault;

    @Size(max = 500, message = "remark must not exceed 500 characters")
    private String remark;
}
