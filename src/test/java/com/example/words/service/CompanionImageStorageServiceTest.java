package com.example.words.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class CompanionImageStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storeReturnsPublicImageEndpoint() {
        CompanionImageStorageService service = new CompanionImageStorageService(temporaryDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[] {1, 2, 3});

        var response = service.store(file);

        assertTrue(response.getUrl().startsWith("/api/classrooms/companion-images/"));
        assertTrue(response.getUrl().endsWith(response.getFileName()));
    }
}
