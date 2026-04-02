package com.example.words.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessageRequest {

    @NotBlank(message = "role is required")
    @Pattern(regexp = "system|user|assistant", message = "role must be one of system, user, assistant")
    private String role;

    @NotBlank(message = "content is required")
    @Size(max = 8000, message = "content must not exceed 8000 characters")
    private String content;
}
