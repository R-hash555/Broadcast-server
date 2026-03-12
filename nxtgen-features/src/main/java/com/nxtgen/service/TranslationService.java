package com.nxtgen.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Feature 4 – Automatic Language Translation
 *
 * Uses MyMemory free translation API (no key required, 1000 words/day).
 * Falls back to original text on any error.
 */
@Service
public class TranslationService {

    // MyMemory free API endpoint
    private static final String API = "https://api.mymemory.translated.net/get?q=%s&langpair=%s|%s";

    /**
     * Detect language code heuristic based on common Unicode ranges.
     */
    public String detectLang(String text) {
        if (text == null || text.isBlank()) return "en";
        long devanagari  = text.chars().filter(c -> c >= 0x0900 && c <= 0x097F).count();
        long arabic      = text.chars().filter(c -> c >= 0x0600 && c <= 0x06FF).count();
        long latin       = text.chars().filter(Character::isLetter).count();
        if (devanagari > 2) return "hi";
        if (arabic > 2)     return "ar";
        // Default: assume English for Latin-script text
        return "en";
    }

    /**
     * Translate text from sourceLang to targetLang using MyMemory REST API.
     * Returns the original text if translation fails or langs match.
     */
    public String translate(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return text;
        if (sourceLang.equalsIgnoreCase(targetLang)) return text;
        if (text.length() > 500) text = text.substring(0, 500); // API limit
        try {
            String url = String.format(API,
                java.net.URLEncoder.encode(text, "UTF-8"), sourceLang, targetLang);
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() != 200) return text;
            String body = new String(conn.getInputStream().readAllBytes());
            // Simple JSON parse – extract "translatedText"
            int i = body.indexOf("\"translatedText\":");
            if (i == -1) return text;
            int start = body.indexOf('"', i + 17) + 1;
            int end   = body.indexOf('"', start);
            return body.substring(start, end);
        } catch (Exception e) {
            return text;
        }
    }
}
