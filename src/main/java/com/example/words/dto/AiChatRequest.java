package com.example.words.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @NotNull(message = "configId is required")
    private Long configId;

    @Valid
    @NotEmpty(message = "messages must not be empty")
    private List<AiChatMessageRequest> messages;
}
