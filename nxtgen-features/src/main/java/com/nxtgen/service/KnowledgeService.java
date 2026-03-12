package com.nxtgen.service;

import com.nxtgen.model.KnowledgeCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.*;

/**
 * Feature 1 – Real-Time Knowledge Extraction
 *
 * Detects problem/solution patterns in messages and creates Knowledge Cards.
 * Patterns recognised:
 *   • "How to X?" / "How do I X?" → problem; next reply containing answer keywords → solution
 *   • Messages starting with "Fix:", "Solution:", "Answer:" → solution
 *   • Q&A style detected via keyword heuristics
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final int MAX_CARDS = 200;

    // Pending questions waiting for an answer: channel → last question
    private final Map<String, String> pendingQuestions = new LinkedHashMap<>();

    private final Deque<KnowledgeCard> cards = new ConcurrentLinkedDeque<>();

    // Patterns that signal a question/problem
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "(?i)(how\\s+(?:to|do\\s+i|can\\s+i)|why\\s+(?:is|does|am|are)|" +
        "(?:fix|resolve|solve|debug|error|issue|problem|help)\\b.*\\?)", Pattern.DOTALL);

    // Patterns that signal an answer/solution
    private static final Pattern SOLUTION_PATTERN = Pattern.compile(
        "(?i)^\\s*(fix\\s*:|solution\\s*:|answer\\s*:|try\\s*:|resolved\\s*:|run\\s+|" +
        "you\\s+(?:can|should|need)|just\\s+|the\\s+fix|use\\s+)", Pattern.DOTALL);

    /**
     * Analyse a message and return a KnowledgeCard if one can be extracted, else null.
     */
    public KnowledgeCard analyse(String channel, String content, String author) {
        if (content == null || content.isBlank()) return null;

        // Check if it's a solution to a pending question
        String pending = pendingQuestions.get(channel);
        if (pending != null && SOLUTION_PATTERN.matcher(content).find()) {
            pendingQuestions.remove(channel);
            KnowledgeCard card = new KnowledgeCard(channel, pending, content.trim(), author);
            store(card);
            log.info("Knowledge card extracted in #{}: {}", channel, pending.substring(0, Math.min(50, pending.length())));
            return card;
        }

        // Check if it's a question
        if (QUESTION_PATTERN.matcher(content).find()) {
            pendingQuestions.put(channel, content.trim());
        }

        return null;
    }

    private void store(KnowledgeCard card) {
        cards.addLast(card);
        while (cards.size() > MAX_CARDS) cards.pollFirst();
    }

    public List<KnowledgeCard> getAll() { return new ArrayList<>(cards); }

    public List<KnowledgeCard> getByChannel(String channel) {
        return cards.stream().filter(c -> c.getChannel().equals(channel)).toList();
    }
}
