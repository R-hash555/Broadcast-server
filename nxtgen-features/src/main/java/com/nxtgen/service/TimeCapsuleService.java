package com.nxtgen.service;

import com.nxtgen.model.ChatMessage;
import com.nxtgen.model.TimeCapsuleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Feature 6 – Time Capsule Messages
 *
 * Stores scheduled messages and delivers them when:
 *   a) The target epoch-millis timestamp is reached, OR
 *   b) The workspace user count reaches/exceeds a threshold.
 */
@Service
public class TimeCapsuleService {

    private static final Logger log = LoggerFactory.getLogger(TimeCapsuleService.class);
    private final List<TimeCapsuleMessage> pending = new CopyOnWriteArrayList<>();
    private Consumer<ChatMessage> deliveryCallback;

    public void setDeliveryCallback(Consumer<ChatMessage> cb) { this.deliveryCallback = cb; }

    public void schedule(ChatMessage msg, Long deliverAt, Long deliverAtUserCount) {
        pending.add(new TimeCapsuleMessage(msg, deliverAt, deliverAtUserCount));
        log.info("Time capsule scheduled: deliverAt={}, userCount={}", deliverAt, deliverAtUserCount);
    }

    /** Called every 10 seconds to check time-based capsules. */
    @Scheduled(fixedDelay = 10_000)
    public void checkTimeBased() {
        long now = System.currentTimeMillis();
        Iterator<TimeCapsuleMessage> it = pending.iterator();
        List<TimeCapsuleMessage> toRemove = new ArrayList<>();
        while (it.hasNext()) {
            TimeCapsuleMessage tc = it.next();
            if (tc.getDeliverAt() != null && now >= tc.getDeliverAt()) {
                deliver(tc.getMessage());
                toRemove.add(tc);
            }
        }
        pending.removeAll(toRemove);
    }

    /** Called whenever user count changes (join/leave). */
    public void checkUserCount(int currentCount) {
        List<TimeCapsuleMessage> toRemove = new ArrayList<>();
        for (TimeCapsuleMessage tc : pending) {
            if (tc.getDeliverAtUserCount() != null && currentCount >= tc.getDeliverAtUserCount()) {
                deliver(tc.getMessage());
                toRemove.add(tc);
            }
        }
        pending.removeAll(toRemove);
    }

    private void deliver(ChatMessage msg) {
        log.info("Delivering time capsule message in #{}", msg.getChannel());
        if (deliveryCallback != null) deliveryCallback.accept(msg);
    }

    public List<Map<String, Object>> getPendingSummaries() {
        return pending.stream().map(tc -> Map.<String, Object>of(
            "channel",          tc.getMessage().getChannel() != null ? tc.getMessage().getChannel() : "general",
            "preview",          tc.getMessage().getContent() != null
                                    ? tc.getMessage().getContent().substring(0, Math.min(40, tc.getMessage().getContent().length())) + "…"
                                    : "",
            "deliverAt",        tc.getDeliverAt()          != null ? tc.getDeliverAt()          : 0,
            "deliverAtUsers",   tc.getDeliverAtUserCount() != null ? tc.getDeliverAtUserCount() : 0
        )).toList();
    }
}
