package com.example.words.dto;

import com.example.words.model.AiConfig;
import com.example.words.model.AiConfigStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConfigResponse {

    private Long id;
    private String providerName;
    private String apiUrl;
    private String apiKeyMasked;
    private String modelName;
    private AiConfigStatus status;
    private Boolean isDefault;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AiConfigResponse from(AiConfig config) {
        return new AiConfigResponse(
                config.getId(),
                config.getProviderName(),
                config.getApiUrl(),
                config.getApiKeyMasked(),
                config.getModelName(),
                config.getStatus(),
                config.getIsDefault(),
                config.getRemark(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
