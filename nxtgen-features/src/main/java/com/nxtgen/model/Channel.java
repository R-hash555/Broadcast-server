package com.nxtgen.model;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Channel {

    private static final int MAX_HISTORY = 100;

    private final String name;
    private final String description;
    private final String emoji;
    private final boolean isPrivate;
    private final Instant createdAt;
    private final Set<String> memberIds;
    private final Deque<ChatMessage> history;
    private final AtomicLong totalMessages = new AtomicLong();

    public Channel(String name, String description, String emoji, boolean isPrivate) {
        this.name        = name;
        this.description = description != null ? description : "";
        this.emoji       = emoji != null ? emoji : "💬";
        this.isPrivate   = isPrivate;
        this.createdAt   = Instant.now();
        this.memberIds   = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.history     = new ConcurrentLinkedDeque<>();
    }

    public void addMember(String userId)    { memberIds.add(userId); }
    public void removeMember(String userId) { memberIds.remove(userId); }
    public boolean hasMember(String userId) { return memberIds.contains(userId); }
    public int getMemberCount()             { return memberIds.size(); }

    public void recordMessage(ChatMessage msg) {
        totalMessages.incrementAndGet();
        history.addLast(msg);
        while (history.size() > MAX_HISTORY) history.pollFirst();
    }

    public List<ChatMessage> getHistory() {
        return new ArrayList<>(history);
    }

    // Getters
    public String   getName()          { return name; }
    public String   getDescription()   { return description; }
    public String   getEmoji()         { return emoji; }
    public boolean  isPrivate()        { return isPrivate; }
    public Instant  getCreatedAt()     { return createdAt; }
    public Set<String> getMemberIds()  { return Collections.unmodifiableSet(memberIds); }
    public long     getTotalMessages() { return totalMessages.get(); }
}
