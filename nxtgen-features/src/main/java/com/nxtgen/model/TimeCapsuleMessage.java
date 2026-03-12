package com.nxtgen.model;

public class TimeCapsuleMessage {
    private final ChatMessage message;
    private final Long deliverAt;           // epoch millis; null if trigger is user-count
    private final Long deliverAtUserCount;  // user count trigger; null if time-based

    public TimeCapsuleMessage(ChatMessage message, Long deliverAt, Long deliverAtUserCount) {
        this.message             = message;
        this.deliverAt           = deliverAt;
        this.deliverAtUserCount  = deliverAtUserCount;
    }

    public ChatMessage getMessage()            { return message; }
    public Long        getDeliverAt()          { return deliverAt; }
    public Long        getDeliverAtUserCount() { return deliverAtUserCount; }
}
