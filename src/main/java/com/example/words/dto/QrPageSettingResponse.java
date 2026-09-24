package com.example.words.dto;

public record QrPageSettingResponse(
        String title,
        String backgroundImageUrl,
        String qrImageUrl,
        String shareUrl,
        String qrDataUrl) {
}
