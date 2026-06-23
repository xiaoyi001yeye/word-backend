package com.example.words.service;

import com.example.words.model.VideoStorageConfig;
import java.nio.file.Path;

public interface TencentVodGateway {

    TencentVodUploadResult upload(
            VideoStorageConfig config,
            Path filePath,
            String originalFileName,
            String title,
            String description);

    TencentVodMediaInfo describeMedia(VideoStorageConfig config, String fileId);

    void deleteMedia(VideoStorageConfig config, String fileId);

    void validate(VideoStorageConfig config);
}
