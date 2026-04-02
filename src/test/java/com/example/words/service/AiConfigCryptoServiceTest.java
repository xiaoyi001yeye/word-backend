package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AiConfigCryptoServiceTest {

    private static final String VALID_BASE64_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void encryptAndDecryptShouldBeReversible() {
        AiConfigCryptoService cryptoService = new AiConfigCryptoService(VALID_BASE64_KEY);

        String encrypted = cryptoService.encrypt("sk-test-12345678");

        assertNotEquals("sk-test-12345678", encrypted);
        assertEquals("sk-test-12345678", cryptoService.decrypt(encrypted));
    }

    @Test
    void maskShouldHideMiddleCharacters() {
        AiConfigCryptoService cryptoService = new AiConfigCryptoService(VALID_BASE64_KEY);

        assertEquals("sk-t****5678", cryptoService.mask("sk-test-12345678"));
        assertEquals("****", cryptoService.mask("short"));
    }

    @Test
    void constructorShouldRejectInvalidKeyLength() {
        String invalidKey = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThrows(IllegalStateException.class, () -> new AiConfigCryptoService(invalidKey));
    }
}
