package com.example.words.service;

import com.example.words.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VideoStorageConfigCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String VERSION_PREFIX = "v1:";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public VideoStorageConfigCryptoService(@Value("${video.storage.config.encryption-key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("video.storage.config.encryption-key must be configured");
        }
        this.keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("Video storage config encryption key must be 128/192/256 bit");
        }
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv)
            );

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt video storage config secret", ex);
        }
    }

    public String decrypt(String cipherText) {
        try {
            if (cipherText == null || !cipherText.startsWith(VERSION_PREFIX)) {
                throw new BadRequestException("Unsupported encrypted secret format");
            }
            byte[] combined = Base64.getDecoder().decode(cipherText.substring(VERSION_PREFIX.length()));
            if (combined.length <= IV_LENGTH) {
                throw new BadRequestException("Invalid encrypted secret payload");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv)
            );
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt video storage config secret", ex);
        }
    }

    public String mask(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
