package com.nxtgen.server;

import com.nxtgen.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class NxtGenRestController {

    @Autowired private MessagingService     messagingService;
    @Autowired private KnowledgeService     knowledgeService;
    @Autowired private SentimentService     sentimentService;
    @Autowired private TimeCapsuleService   timeCapsuleService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(messagingService.stats());
    }

    // Feature 1: Knowledge cards
    @GetMapping("/knowledge")
    public ResponseEntity<?> allKnowledge() {
        return ResponseEntity.ok(Map.of("cards", knowledgeService.getAll()));
    }

    @GetMapping("/knowledge/{channel}")
    public ResponseEntity<?> channelKnowledge(@PathVariable String channel) {
        return ResponseEntity.ok(Map.of("cards", knowledgeService.getByChannel(channel)));
    }

    // Feature 5: Sentiment
    @GetMapping("/sentiment")
    public ResponseEntity<?> allSentiment() {
        return ResponseEntity.ok(sentimentService.getAllStats());
    }

    @GetMapping("/sentiment/{channel}")
    public ResponseEntity<?> channelSentiment(@PathVariable String channel) {
        return ResponseEntity.ok(sentimentService.getStats(channel));
    }

    // Feature 6: Time capsules
    @GetMapping("/capsules")
    public ResponseEntity<?> pendingCapsules() {
        return ResponseEntity.ok(Map.of("pending", timeCapsuleService.getPendingSummaries()));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "version", "2.0.0-features"));
    }
}
