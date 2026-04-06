package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.words.exception.BadRequestException;
import com.example.words.model.VideoStorageConfig;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TencentVodSdkGatewayTest {

    private VideoStorageConfigCryptoService cryptoService;
    private TencentVodSdkGateway gateway;

    @BeforeEach
    void setUp() {
        cryptoService = new VideoStorageConfigCryptoService(
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes())
        );
        gateway = new TencentVodSdkGateway(cryptoService);
    }

    @Test
    void validateShouldRejectPlaceholderSecretsBeforeCallingTencentCloud() {
        VideoStorageConfig config = new VideoStorageConfig();
        config.setRegion("ap-guangzhou");
        config.setSecretIdEncrypted(cryptoService.encrypt("1"));
        config.setSecretKeyEncrypted(cryptoService.encrypt("1"));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> gateway.validate(config));

        assertEquals(
                "Current video storage config still uses placeholder SecretId/SecretKey. "
                        + "Please save real Tencent Cloud API credentials before testing",
                exception.getMessage()
        );
    }
}
