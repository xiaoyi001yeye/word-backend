package com.example.words.service;

import com.example.words.dto.AiChatMessageRequest;
import com.example.words.dto.AiConfigResponse;
import com.example.words.dto.AiConfigTestResponse;
import com.example.words.dto.CreateAiConfigRequest;
import com.example.words.dto.UpdateAiConfigRequest;
import com.example.words.dto.UpdateAiConfigStatusRequest;
import com.example.words.exception.ConflictException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.exception.BadRequestException;
import com.example.words.model.AiConfig;
import com.example.words.model.AiConfigStatus;
import com.example.words.model.AppUser;
import com.example.words.repository.AiConfigRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConfigService {

    private static final List<AiChatMessageRequest> TEST_MESSAGES = List.of(
            new AiChatMessageRequest("system", "You are an API connectivity test assistant."),
            new AiChatMessageRequest("user", "Reply with test-ok only.")
    );

    private final AiConfigRepository aiConfigRepository;
    private final AiConfigCryptoService aiConfigCryptoService;
    private final CurrentUserService currentUserService;
    private final AccessControlService accessControlService;
    private final AiGatewayService aiGatewayService;

    public AiConfigService(
            AiConfigRepository aiConfigRepository,
            AiConfigCryptoService aiConfigCryptoService,
            CurrentUserService currentUserService,
            AccessControlService accessControlService,
            AiGatewayService aiGatewayService) {
        this.aiConfigRepository = aiConfigRepository;
        this.aiConfigCryptoService = aiConfigCryptoService;
        this.currentUserService = currentUserService;
        this.accessControlService = accessControlService;
        this.aiGatewayService = aiGatewayService;
    }

    @Transactional(readOnly = true)
    public List<AiConfigResponse> listMyConfigs(AiConfigStatus status, String providerName) {
        AppUser actor = currentUserService.getCurrentUser();
        String normalizedProviderName = trimToNull(providerName);
        List<AiConfig> configs = status == null
                ? aiConfigRepository.findByUserIdOrderByUpdatedAtDesc(actor.getId())
                : aiConfigRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(actor.getId(), status);
        return configs.stream()
                .filter(config -> matchesProviderName(config, normalizedProviderName))
                .map(AiConfigResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiConfigResponse getMyConfigResponse(Long id) {
        return AiConfigResponse.from(getMyConfig(id));
    }

    @Transactional
    public AiConfigResponse create(CreateAiConfigRequest request) {
        AppUser actor = currentUserService.getCurrentUser();

        String providerName = normalizeRequiredText(request.getProviderName(), "providerName");
        String apiUrl = normalizeUrl(request.getApiUrl());
        String apiKey = normalizeApiKey(request.getApiKey(), true);
        String modelName = normalizeRequiredText(request.getModelName(), "modelName");
        String remark = trimToNull(request.getRemark());

        if (aiConfigRepository.existsByUserIdAndProviderNameAndApiUrlAndModelName(
                actor.getId(),
                providerName,
                apiUrl,
                modelName
        )) {
            throw new ConflictException("AI config already exists");
        }

        AiConfig config = new AiConfig();
        config.setUserId(actor.getId());
        config.setUserRole(actor.getRole());
        config.setProviderName(providerName);
        config.setApiUrl(apiUrl);
        config.setApiKeyEncrypted(aiConfigCryptoService.encrypt(apiKey));
        config.setApiKeyMasked(aiConfigCryptoService.mask(apiKey));
        config.setModelName(modelName);
        config.setStatus(request.getStatus());
        config.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        config.setRemark(remark);

        boolean shouldAutoDefault = aiConfigRepository.countByUserId(actor.getId()) == 0
                && request.getStatus() == AiConfigStatus.ENABLED;
        if (Boolean.TRUE.equals(config.getIsDefault()) || shouldAutoDefault) {
            ensureEnabled(config.getStatus());
            config.setIsDefault(Boolean.TRUE);
            aiConfigRepository.clearDefaultByUserId(actor.getId());
        }

        return saveWithConflictHandling(config);
    }

    @Transactional
    public AiConfigResponse update(Long id, UpdateAiConfigRequest request) {
        AiConfig config = getMyConfig(id);

        String providerName = normalizeRequiredText(request.getProviderName(), "providerName");
        String apiUrl = normalizeUrl(request.getApiUrl());
        String modelName = normalizeRequiredText(request.getModelName(), "modelName");
        String apiKey = normalizeApiKey(request.getApiKey(), false);
        String remark = trimToNull(request.getRemark());

        if (aiConfigRepository.existsByUserIdAndProviderNameAndApiUrlAndModelNameAndIdNot(
                config.getUserId(),
                providerName,
                apiUrl,
                modelName,
                config.getId()
        )) {
            throw new ConflictException("AI config already exists");
        }

        config.setProviderName(providerName);
        config.setApiUrl(apiUrl);
        config.setModelName(modelName);
        config.setRemark(remark);
        config.setStatus(request.getStatus());

        if (apiKey != null) {
            config.setApiKeyEncrypted(aiConfigCryptoService.encrypt(apiKey));
            config.setApiKeyMasked(aiConfigCryptoService.mask(apiKey));
        }

        boolean requestedDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (requestedDefault) {
            ensureEnabled(request.getStatus());
            aiConfigRepository.clearDefaultByUserIdAndIdNot(config.getUserId(), config.getId());
            config.setIsDefault(Boolean.TRUE);
        } else if (request.getStatus() == AiConfigStatus.DISABLED || Boolean.FALSE.equals(request.getIsDefault())) {
            config.setIsDefault(Boolean.FALSE);
        }

        return saveWithConflictHandling(config);
    }

    @Transactional
    public void delete(Long id) {
        AiConfig config = getMyConfig(id);
        aiConfigRepository.delete(config);
    }

    @Transactional
    public AiConfigResponse updateStatus(Long id, UpdateAiConfigStatusRequest request) {
        AiConfig config = getMyConfig(id);
        config.setStatus(request.getStatus());
        if (request.getStatus() == AiConfigStatus.DISABLED) {
            config.setIsDefault(Boolean.FALSE);
        }
        return saveWithConflictHandling(config);
    }

    @Transactional
    public AiConfigResponse setDefault(Long id) {
        AiConfig config = getMyConfig(id);
        ensureEnabled(config.getStatus());
        aiConfigRepository.clearDefaultByUserIdAndIdNot(config.getUserId(), config.getId());
        config.setIsDefault(Boolean.TRUE);
        return saveWithConflictHandling(config);
    }

    @Transactional(readOnly = true)
    public AiConfigTestResponse test(Long id) {
        AiConfig config = getMyEnabledConfig(id);
        String reply = aiGatewayService.generateText(config, TEST_MESSAGES);
        return new AiConfigTestResponse(
                config.getId(),
                config.getProviderName(),
                config.getModelName(),
                true,
                reply
        );
    }

    @Transactional(readOnly = true)
    public AiConfig getMyConfig(Long id) {
        AppUser actor = currentUserService.getCurrentUser();
        AiConfig config = aiConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AI config not found: " + id));
        accessControlService.ensureCanManageAiConfig(actor, config);
        return config;
    }

    @Transactional(readOnly = true)
    public AiConfig getMyEnabledConfig(Long id) {
        AiConfig config = getMyConfig(id);
        ensureEnabled(config.getStatus());
        return config;
    }

    @Transactional(readOnly = true)
    public AiConfig resolveActiveConfig(Long configId) {
        if (configId != null) {
            return getMyEnabledConfig(configId);
        }

        AppUser actor = currentUserService.getCurrentUser();
        AiConfig config = aiConfigRepository.findByUserIdAndIsDefaultTrue(actor.getId())
                .orElseThrow(() -> new BadRequestException("No default AI config is available"));
        if (config.getStatus() != AiConfigStatus.ENABLED) {
            throw new BadRequestException("Default AI config is disabled");
        }
        return config;
    }

    private AiConfigResponse saveWithConflictHandling(AiConfig config) {
        try {
            return AiConfigResponse.from(aiConfigRepository.save(config));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("AI config already exists");
        }
    }

    private boolean matchesProviderName(AiConfig config, String providerName) {
        if (providerName == null) {
            return true;
        }
        return config.getProviderName() != null
                && config.getProviderName().toLowerCase(Locale.ROOT).contains(providerName.toLowerCase(Locale.ROOT));
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeApiKey(String value, boolean required) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            if (required) {
                throw new BadRequestException("apiKey is required");
            }
            return null;
        }
        if (normalized.length() < 8 || normalized.length() > 512) {
            throw new BadRequestException("apiKey length must be between 8 and 512");
        }
        return normalized;
    }

    private String normalizeUrl(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException("apiUrl is required");
        }

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("apiUrl is invalid");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new BadRequestException("apiUrl must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BadRequestException("apiUrl host is required");
        }
        return normalized;
    }

    private void ensureEnabled(AiConfigStatus status) {
        if (status != AiConfigStatus.ENABLED) {
            throw new BadRequestException("AI config must be enabled");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
