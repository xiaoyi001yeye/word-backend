package com.example.words.service;

public record TencentVodMediaInfo(
        String fileId,
        String mediaUrl,
        String coverUrl,
        Long durationSeconds,
        boolean ready,
        boolean preferredPlaybackReady) {
}
