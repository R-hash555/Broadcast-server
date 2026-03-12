package com.nxtgen.model;

import java.time.Instant;
import java.util.UUID;

public class KnowledgeCard {
    private final String id;
    private final String channel;
    private final String problem;
    private final String solution;
    private final String extractedBy;
    private final long timestamp;

    public KnowledgeCard(String channel, String problem, String solution, String extractedBy) {
        this.id          = UUID.randomUUID().toString();
        this.channel     = channel;
        this.problem     = problem;
        this.solution    = solution;
        this.extractedBy = extractedBy;
        this.timestamp   = Instant.now().toEpochMilli();
    }

    public String getId()          { return id; }
    public String getChannel()     { return channel; }
    public String getProblem()     { return problem; }
    public String getSolution()    { return solution; }
    public String getExtractedBy() { return extractedBy; }
    public long   getTimestamp()   { return timestamp; }
}
