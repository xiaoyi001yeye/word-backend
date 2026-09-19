package com.example.words.service;

final class AiJsonResponseNormalizer {

    private AiJsonResponseNormalizer() {
    }

    static String stripMarkdownCodeFence(String rawContent) {
        String normalized = rawContent == null ? "" : rawContent.trim();
        if (!normalized.startsWith("```")) {
            return normalized;
        }

        int firstNewLine = normalized.indexOf('\n');
        if (firstNewLine < 0) {
            return normalized;
        }

        String body = normalized.substring(firstNewLine + 1);
        int fenceIndex = body.lastIndexOf("```");
        return (fenceIndex >= 0 ? body.substring(0, fenceIndex) : body).trim();
    }
}
