package com.example.words.util;

import java.util.Locale;

public final class WordNormalizationUtils {

    private WordNormalizationUtils() {
    }

    public static String normalize(String word) {
        if (word == null) {
            return null;
        }

        String trimmed = word.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
