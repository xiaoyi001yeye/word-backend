package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.words.dto.AiConfigResponse;
import com.example.words.dto.CreateAiConfigRequest;
import com.example.words.dto.UpdateAiConfigRequest;
import com.example.words.dto.UpdateAiConfigStatusRequest;
import com.example.words.model.AiConfig;
import com.example.words.model.AiConfigStatus;
import com.example.words.model.AppUser;
import com.example.words.model.UserRole;
import com.example.words.repository.AiConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiConfigServiceTest {

    @Mock
    private AiConfigRepository aiConfigRepository;

    @Mock
    private AiConfigCryptoService aiConfigCryptoService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AiGatewayService aiGatewayService;

    private AiConfigService aiConfigService;

    @BeforeEach
    void setUp() {
        aiConfigService = new AiConfigService(
                aiConfigRepository,
                aiConfigCryptoService,
                currentUserService,
                accessControlService,
                aiGatewayService
        );
    }

    @Test
    void createShouldAutoPromoteFirstEnabledConfigToDefault() {
        AppUser actor = actor();
        CreateAiConfigRequest request = new CreateAiConfigRequest(
                "OpenAI",
                "https://api.openai.com/v1/chat/completions",
                "sk-test-12345678",
                "gpt-4o-mini",
                AiConfigStatus.ENABLED,
                Boolean.FALSE,
                "reading"
        );

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(aiConfigRepository.existsByUserIdAndProviderNameAndApiUrlAndModelName(
                actor.getId(),
                "OpenAI",
                "https://api.openai.com/v1/chat/completions",
                "gpt-4o-mini"
        )).thenReturn(false);
        when(aiConfigRepository.countByUserId(actor.getId())).thenReturn(0L);
        when(aiConfigCryptoService.encrypt("sk-test-12345678")).thenReturn("v1:encrypted");
        when(aiConfigCryptoService.mask("sk-test-12345678")).thenReturn("sk-t****5678");
        when(aiConfigRepository.save(any(AiConfig.class))).thenAnswer(invocation -> {
            AiConfig config = invocation.getArgument(0);
            config.setId(101L);
            return config;
        });

        AiConfigResponse response = aiConfigService.create(request);

        assertEquals(101L, response.getId());
        assertTrue(response.getIsDefault());
        verify(aiConfigRepository).clearDefaultByUserId(actor.getId());
    }

    @Test
    void updateShouldKeepExistingEncryptedKeyWhenApiKeyIsBlank() {
        AppUser actor = actor();
        AiConfig existing = existingConfig();
        UpdateAiConfigRequest request = new UpdateAiConfigRequest(
                "OpenAI",
                "https://api.openai.com/v1/chat/completions",
                "   ",
                "gpt-4o-mini",
                AiConfigStatus.ENABLED,
                Boolean.TRUE,
                "updated"
        );

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(aiConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(aiConfigRepository.existsByUserIdAndProviderNameAndApiUrlAndModelNameAndIdNot(
                actor.getId(),
                "OpenAI",
                "https://api.openai.com/v1/chat/completions",
                "gpt-4o-mini",
                existing.getId()
        )).thenReturn(false);
        when(aiConfigRepository.save(any(AiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiConfigResponse response = aiConfigService.update(existing.getId(), request);

        assertEquals("v1:existing", existing.getApiKeyEncrypted());
        assertEquals("sk-o****7890", existing.getApiKeyMasked());
        assertEquals("updated", response.getRemark());
        verify(aiConfigCryptoService, never()).encrypt(any());
    }

    @Test
    void updateStatusShouldClearDefaultWhenDisablingConfig() {
        AppUser actor = actor();
        AiConfig existing = existingConfig();
        existing.setIsDefault(Boolean.TRUE);

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(aiConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(aiConfigRepository.save(any(AiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiConfigResponse response = aiConfigService.updateStatus(
                existing.getId(),
                new UpdateAiConfigStatusRequest(AiConfigStatus.DISABLED)
        );

        assertEquals(AiConfigStatus.DISABLED, response.getStatus());
        assertFalse(response.getIsDefault());
    }

    @Test
    void setDefaultShouldClearOtherDefaultFlagsFirst() {
        AppUser actor = actor();
        AiConfig existing = existingConfig();

        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(aiConfigRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(aiConfigRepository.save(any(AiConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        aiConfigService.setDefault(existing.getId());

        ArgumentCaptor<AiConfig> captor = ArgumentCaptor.forClass(AiConfig.class);
        verify(aiConfigRepository).save(captor.capture());
        verify(aiConfigRepository).clearDefaultByUserIdAndIdNot(actor.getId(), existing.getId());
        assertTrue(captor.getValue().getIsDefault());
    }

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId(7L);
        actor.setRole(UserRole.TEACHER);
        return actor;
    }

    private AiConfig existingConfig() {
        AiConfig config = new AiConfig();
        config.setId(11L);
        config.setUserId(7L);
        config.setUserRole(UserRole.TEACHER);
        config.setProviderName("OpenAI");
        config.setApiUrl("https://api.openai.com/v1/chat/completions");
        config.setApiKeyEncrypted("v1:existing");
        config.setApiKeyMasked("sk-o****7890");
        config.setModelName("gpt-4o-mini");
        config.setStatus(AiConfigStatus.ENABLED);
        config.setIsDefault(Boolean.FALSE);
        return config;
    }
}
