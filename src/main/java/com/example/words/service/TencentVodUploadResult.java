package com.example.words.service;

public record TencentVodUploadResult(
        String fileId,
        String mediaUrl,
        String coverUrl,
        boolean transcodeRequested) {
}
