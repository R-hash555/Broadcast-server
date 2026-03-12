package com.nxtgen.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Feature 5 – Live Sentiment Monitor
 *
 * Lightweight lexicon-based sentiment analysis. No ML library needed.
 */
@Service
public class SentimentService {

    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

    private static final Set<String> POSITIVE_WORDS = Set.of(
        "great","awesome","good","nice","excellent","wonderful","love","thanks","thank","helpful",
        "perfect","happy","glad","amazing","fantastic","brilliant","yes","cool","👍","😊","🎉","✅","🚀"
    );

    private static final Set<String> NEGATIVE_WORDS = Set.of(
        "bad","awful","terrible","horrible","hate","broken","error","bug","fail","failed","failing",
        "crash","crashes","wrong","issue","problem","can't","cannot","doesn't","no","not","never",
        "sad","angry","frustrated","annoyed","😡","💔","❌","🐛"
    );

    // channel → rolling window of last 50 sentiments
    private final Map<String, Deque<Sentiment>> channelSentiments = new ConcurrentHashMap<>();
    private static final int WINDOW = 50;

    public Sentiment analyse(String text) {
        if (text == null) return Sentiment.NEUTRAL;
        String lower = text.toLowerCase();
        String[] tokens = lower.split("[\\s,!?.]+");
        int score = 0;
        for (String t : tokens) {
            if (POSITIVE_WORDS.contains(t)) score++;
            else if (NEGATIVE_WORDS.contains(t)) score--;
        }
        if (score > 0) return Sentiment.POSITIVE;
        if (score < 0) return Sentiment.NEGATIVE;
        return Sentiment.NEUTRAL;
    }

    public void record(String channel, String text) {
        Sentiment s = analyse(text);
        channelSentiments.computeIfAbsent(channel, k -> new ArrayDeque<>()).add(s);
        Deque<Sentiment> deque = channelSentiments.get(channel);
        while (deque.size() > WINDOW) deque.pollFirst();
    }

    public Map<String, Object> getStats(String channel) {
        Deque<Sentiment> deque = channelSentiments.getOrDefault(channel, new ArrayDeque<>());
        if (deque.isEmpty()) return Map.of("positive", 0, "neutral", 100, "negative", 0, "total", 0);
        Map<Sentiment, AtomicInteger> counts = new EnumMap<>(Sentiment.class);
        for (Sentiment s : Sentiment.values()) counts.put(s, new AtomicInteger());
        deque.forEach(s -> counts.get(s).incrementAndGet());
        int total = deque.size();
        return Map.of(
            "positive", pct(counts.get(Sentiment.POSITIVE).get(), total),
            "neutral",  pct(counts.get(Sentiment.NEUTRAL).get(), total),
            "negative", pct(counts.get(Sentiment.NEGATIVE).get(), total),
            "total",    total
        );
    }

    public Map<String, Map<String, Object>> getAllStats() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        channelSentiments.forEach((ch, v) -> result.put(ch, getStats(ch)));
        return result;
    }

    private int pct(int n, int total) { return total == 0 ? 0 : Math.round(n * 100f / total); }
}
