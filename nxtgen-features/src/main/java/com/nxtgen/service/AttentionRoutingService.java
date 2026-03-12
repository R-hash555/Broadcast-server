package com.nxtgen.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Feature 7 – Attention Routing
 *
 * Detects technical questions and suggests the best expert to route to
 * based on keyword-to-expert mapping that builds up over time.
 */
@Service
public class AttentionRoutingService {

    // Static domain → expert-role mapping
    private static final Map<String, String> DOMAIN_ROLES = new LinkedHashMap<>(Map.of(
        "docker|kubernetes|k8s|container|deployment|devops|ci/cd|pipeline", "DevOps Expert",
        "java|spring|maven|gradle|jvm|microservice",                         "Java Engineer",
        "react|frontend|css|html|javascript|typescript|vue|angular",         "Frontend Dev",
        "sql|database|postgres|mysql|mongodb|redis|query",                   "Database Admin",
        "security|auth|jwt|oauth|ssl|certificate|firewall",                  "Security Engineer",
        "python|django|flask|pandas|numpy|ml|ai",                            "ML/Python Dev",
        "aws|gcp|azure|terraform|cloud|s3|ec2",                              "Cloud Architect"
    ));

    // Runtime: username → domains they've answered (for dynamic routing)
    private final Map<String, Set<String>> userExpertise = new LinkedHashMap<>();

    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "(?i)(how\\s+to|how\\s+do|why\\s+(?:is|does|am)|fix|error|issue|problem|help\\s+with)", Pattern.DOTALL);

    /** Record that a user answered a message containing certain keywords. */
    public void recordAnswer(String username, String content) {
        String lower = content.toLowerCase();
        for (String domainPattern : DOMAIN_ROLES.keySet()) {
            for (String kw : domainPattern.split("\\|")) {
                if (lower.contains(kw)) {
                    userExpertise.computeIfAbsent(username, k -> new LinkedHashSet<>()).add(kw);
                }
            }
        }
    }

    /**
     * Returns a routing suggestion for the message, or null if no question detected.
     */
    public RoutingSuggestion route(String content) {
        if (content == null || !QUESTION_PATTERN.matcher(content).find()) return null;

        String lower = content.toLowerCase();

        // Try dynamic expertise first
        for (Map.Entry<String, Set<String>> entry : userExpertise.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) {
                    return new RoutingSuggestion("@" + entry.getKey(), kw, "learned-expertise");
                }
            }
        }

        // Fall back to static domain mapping
        for (Map.Entry<String, String> entry : DOMAIN_ROLES.entrySet()) {
            for (String kw : entry.getKey().split("\\|")) {
                if (lower.contains(kw)) {
                    return new RoutingSuggestion(entry.getValue(), kw, "domain-mapping");
                }
            }
        }
        return null;
    }

    public record RoutingSuggestion(String suggestedExpert, String matchedKeyword, String source) {}
}
