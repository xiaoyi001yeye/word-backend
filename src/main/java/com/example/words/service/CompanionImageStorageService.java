package com.example.words.service;

import com.example.words.dto.CompanionImageUploadResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CompanionImageStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png",
            MediaType.IMAGE_GIF_VALUE, ".gif",
            "image/webp", ".webp"
    );

    private final Path rootDirectory;

    public CompanionImageStorageService(
            @Value("${companion.image-directory:uploads/companion-classrooms}") String imageDirectory) {
        this.rootDirectory = Path.of(imageDirectory).toAbsolutePath().normalize();
    }

    public CompanionImageUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 5MB");
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("只支持 JPG、PNG、GIF 或 WebP 图片");
        }

        String fileName = UUID.randomUUID() + extension;
        Path target = rootDirectory.resolve(fileName).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("图片路径无效");
        }
        try {
            Files.createDirectories(rootDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("保存伴读图片失败", exception);
        }
        return new CompanionImageUploadResponse("/api/classrooms/companion-images/" + fileName, fileName);
    }

    public Optional<Resource> load(String fileName) {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return Optional.empty();
        }
        Path target = rootDirectory.resolve(fileName).normalize();
        if (!target.startsWith(rootDirectory) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(target));
    }
}
