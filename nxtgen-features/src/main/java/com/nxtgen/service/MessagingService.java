package com.nxtgen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtgen.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    @Autowired private UserRegistry          userRegistry;
    @Autowired private ChannelRegistry       channelRegistry;
    @Autowired private ObjectMapper          objectMapper;
    @Autowired private KnowledgeService      knowledgeService;
    @Autowired private SentimentService      sentimentService;
    @Autowired private TranslationService    translationService;
    @Autowired private TimeCapsuleService    timeCapsuleService;
    @Autowired private AttentionRoutingService attentionRoutingService;

    @PostConstruct
    public void init() {
        // Wire time-capsule delivery back into this service
        timeCapsuleService.setDeliveryCallback(msg -> sendToChannel(msg.getChannel(), msg));
    }

    // ── Low-level send ───────────────────────────────────────────────────────

    public void send(UserSession user, ChatMessage msg) {
        if (!user.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(msg);
            synchronized (user.getWsSession()) {
                user.getWsSession().sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Send failed to {}: {}", user.getName(), e.getMessage());
        }
    }

    public void sendToAll(ChatMessage msg) {
        userRegistry.getAll().parallelStream()
            .filter(UserSession::isOpen)
            .forEach(u -> send(u, msg));
    }

    /**
     * Sends a message to all channel members, applying per-user translation.
     */
    public void sendToChannel(String channelName, ChatMessage msg) {
        channelRegistry.get(channelName).ifPresent(ch -> {
            ch.recordMessage(msg);
            ch.getMemberIds().stream()
                .map(id -> userRegistry.get(id).orElse(null))
                .filter(u -> u != null && u.isOpen())
                .forEach(u -> sendTranslated(u, msg));
        });
    }

    /**
     * Feature 4 – Translate outgoing message to user's preferred language.
     */
    private void sendTranslated(UserSession user, ChatMessage msg) {
        String targetLang = user.getPreferredLanguage();
        if (targetLang == null || targetLang.equals("en")
                || msg.getContent() == null || msg.getContent().isBlank()) {
            send(user, msg);
            return;
        }
        // Clone the message with translated content
        ChatMessage translated = cloneMsg(msg);
        String srcLang = msg.getOriginalLang() != null ? msg.getOriginalLang() : "en";
        if (!srcLang.equalsIgnoreCase(targetLang)) {
            String tx = translationService.translate(msg.getContent(), srcLang, targetLang);
            if (!tx.equals(msg.getContent())) {
                translated.setOriginalContent(msg.getContent());
                translated.setContent(tx);
            }
        }
        send(user, translated);
    }

    private ChatMessage cloneMsg(ChatMessage src) {
        ChatMessage c = new ChatMessage();
        c.setId(src.getId());
        c.setType(src.getType());
        c.setSenderId(src.getSenderId());
        c.setSenderName(src.getSenderName());
        c.setSenderAvatar(src.getSenderAvatar());
        c.setChannel(src.getChannel());
        c.setContent(src.getContent());
        c.setData(src.getData());
        c.setEmoji(src.getEmoji());
        c.setTimestamp(src.getTimestamp());
        c.setPriority(src.getPriority());
        c.setOriginalContent(src.getOriginalContent());
        c.setOriginalLang(src.getOriginalLang());
        return c;
    }

    public void sendToOthers(String senderId, ChatMessage msg) {
        userRegistry.getAll().parallelStream()
            .filter(u -> u.isOpen() && !u.getId().equals(senderId))
            .forEach(u -> send(u, msg));
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    public void handleJoin(UserSession user, ChatMessage incoming) {
        if (incoming.getSenderName() != null && !incoming.getSenderName().isBlank()) {
            user.setName(incoming.getSenderName().trim());
        }
        // Feature: set preferred language from JOIN message
        if (incoming.getData() instanceof Map<?,?> d && d.containsKey("lang")) {
            user.setPreferredLanguage(d.get("lang").toString());
        }

        channelRegistry.get("general").ifPresent(ch -> {
            ch.addMember(user.getId());
            user.joinChannel("general");
        });

        ChatMessage welcome = new ChatMessage();
        welcome.setType(ChatMessage.Type.WELCOME);
        welcome.setContent("Welcome to NxtGen, " + user.getName() + "!");
        welcome.setData(Map.of(
            "userId",       user.getId(),
            "userName",     user.getName(),
            "avatarColour", user.getAvatarColour(),
            "channels",     channelSummaries(),
            "users",        userSummaries(),
            "generalHistory", historyOf("general"),
            "knowledgeCards", knowledgeService.getByChannel("general"),
            "sentimentStats", sentimentService.getAllStats(),
            "pendingCapsules",timeCapsuleService.getPendingSummaries()
        ));
        send(user, welcome);

        ChatMessage joined = new ChatMessage();
        joined.setType(ChatMessage.Type.USER_JOINED);
        joined.setSenderId(user.getId());
        joined.setSenderName(user.getName());
        joined.setSenderAvatar(user.getAvatarColour());
        joined.setChannel("general");
        joined.setContent(user.getName() + " joined the workspace");
        joined.setData(Map.of("users", userSummaries()));
        sendToOthers(user.getId(), joined);

        // Feature 6 – check user-count capsules
        timeCapsuleService.checkUserCount(userRegistry.count());
    }

    public void handleMessage(UserSession user, ChatMessage incoming) {
        String channelName = incoming.getChannel();
        if (channelName == null || incoming.getContent() == null || incoming.getContent().isBlank()) return;
        channelName = channelName.toLowerCase();

        Channel channel = channelRegistry.get(channelName).orElse(null);
        if (channel == null) { send(user, ChatMessage.error("Channel #" + channelName + " does not exist.")); return; }
        if (!channel.hasMember(user.getId())) { send(user, ChatMessage.error("You have not joined #" + channelName + ".")); return; }

        String content = incoming.getContent().trim();

        // ── Feature 4: detect source language ────────────────────────────────
        String srcLang = translationService.detectLang(content);

        // ── Feature 2: Priority Broadcast ────────────────────────────────────
        ChatMessage.Priority priority = incoming.getPriority() != null
            ? incoming.getPriority() : ChatMessage.Priority.NORMAL;

        // Build outbound message
        ChatMessage out = new ChatMessage();
        out.setType(ChatMessage.Type.CHANNEL_MESSAGE);
        out.setSenderId(user.getId());
        out.setSenderName(user.getName());
        out.setSenderAvatar(user.getAvatarColour());
        out.setChannel(channelName);
        out.setContent(content);
        out.setPriority(priority);
        out.setOriginalLang(srcLang);

        sendToChannel(channelName, out);
        user.incrementSent();
        user.touch();

        // ── Feature 5: Sentiment ─────────────────────────────────────────────
        sentimentService.record(channelName, content);
        broadcastSentiment(channelName);

        // ── Feature 1: Knowledge Extraction ──────────────────────────────────
        KnowledgeCard card = knowledgeService.analyse(channelName, content, user.getName());
        if (card != null) {
            ChatMessage kc = new ChatMessage();
            kc.setType(ChatMessage.Type.KNOWLEDGE_CARD);
            kc.setChannel(channelName);
            kc.setContent("📚 Knowledge card extracted!");
            kc.setData(Map.of(
                "id",          card.getId(),
                "problem",     card.getProblem(),
                "solution",    card.getSolution(),
                "extractedBy", card.getExtractedBy(),
                "channel",     card.getChannel(),
                "timestamp",   card.getTimestamp()
            ));
            sendToChannel(channelName, kc);
        }

        // ── Feature 7: Attention Routing ─────────────────────────────────────
        AttentionRoutingService.RoutingSuggestion route = attentionRoutingService.route(content);
        if (route != null) {
            ChatMessage ar = new ChatMessage();
            ar.setType(ChatMessage.Type.ATTENTION_ROUTE);
            ar.setChannel(channelName);
            ar.setContent("🎯 Question detected — routing to " + route.suggestedExpert());
            ar.setData(Map.of(
                "suggestedExpert", route.suggestedExpert(),
                "matchedKeyword",  route.matchedKeyword(),
                "originalMessage", content,
                "askedBy",         user.getName()
            ));
            sendToChannel(channelName, ar);
        }

        // Record answers for dynamic routing
        attentionRoutingService.recordAnswer(user.getName(), content);

        // ── Feature 2: CRITICAL messages override everyone ───────────────────
        if (priority == ChatMessage.Priority.CRITICAL) {
            ChatMessage alert = new ChatMessage();
            alert.setType(ChatMessage.Type.PRIORITY_MESSAGE);
            alert.setSenderId(user.getId());
            alert.setSenderName(user.getName());
            alert.setChannel(channelName);
            alert.setContent("🚨 CRITICAL: " + content);
            alert.setPriority(ChatMessage.Priority.CRITICAL);
            sendToAll(alert);
        }
    }

    public void handleReact(UserSession user, ChatMessage incoming) {
        String channelName = incoming.getChannel();
        if (channelName == null || incoming.getEmoji() == null) return;

        ChatMessage out = new ChatMessage();
        out.setType(ChatMessage.Type.REACTION);
        out.setSenderId(user.getId());
        out.setSenderName(user.getName());
        out.setChannel(channelName);
        out.setEmoji(incoming.getEmoji());
        out.setData(Map.of("targetMessageId", incoming.getData() != null ? incoming.getData() : ""));
        sendToChannel(channelName, out);
    }

    public void handleChannelJoin(UserSession user, ChatMessage incoming) {
        String channelName = incoming.getChannel();
        if (channelName == null) return;
        channelName = channelName.toLowerCase();

        Channel channel = channelRegistry.create(channelName,
            incoming.getContent() != null ? incoming.getContent() : "Team channel", null, false);
        channel.addMember(user.getId());
        user.joinChannel(channelName);

        ChatMessage ack = new ChatMessage();
        ack.setType(ChatMessage.Type.SYSTEM);
        ack.setChannel(channelName);
        ack.setContent("Joined #" + channelName);
        ack.setData(Map.of(
            "channel",       channelSummary(channel),
            "history",       historyOf(channelName),
            "knowledgeCards",knowledgeService.getByChannel(channelName),
            "sentimentStats",sentimentService.getStats(channelName)
        ));
        send(user, ack);

        ChatMessage announce = new ChatMessage();
        announce.setType(ChatMessage.Type.USER_JOINED);
        announce.setSenderId(user.getId());
        announce.setSenderName(user.getName());
        announce.setSenderAvatar(user.getAvatarColour());
        announce.setChannel(channelName);
        announce.setContent(user.getName() + " joined #" + channelName);
        announce.setData(Map.of("users", userSummaries()));
        sendToChannel(channelName, announce);
        broadcastChannelList();
    }

    public void handleTyping(UserSession user, ChatMessage incoming, boolean isTyping) {
        user.setTyping(isTyping);
        String channelName = incoming.getChannel();
        if (channelName == null) return;
        ChatMessage out = new ChatMessage();
        out.setType(ChatMessage.Type.TYPING_INDICATOR);
        out.setSenderId(user.getId());
        out.setSenderName(user.getName());
        out.setChannel(channelName);
        out.setData(Map.of("typing", isTyping));
        channelRegistry.get(channelName).ifPresent(ch ->
            ch.getMemberIds().stream()
                .filter(id -> !id.equals(user.getId()))
                .map(id -> userRegistry.get(id).orElse(null))
                .filter(u -> u != null && u.isOpen())
                .forEach(u -> send(u, out))
        );
    }

    /**
     * Feature 6: Handle a TIME_CAPSULE scheduling request.
     */
    public void handleTimeCapsule(UserSession user, ChatMessage incoming) {
        String channelName = incoming.getChannel() != null ? incoming.getChannel().toLowerCase() : "general";
        if (incoming.getContent() == null) { send(user, ChatMessage.error("Time capsule needs content")); return; }

        ChatMessage msg = new ChatMessage();
        msg.setType(ChatMessage.Type.CHANNEL_MESSAGE);
        msg.setSenderId(user.getId());
        msg.setSenderName(user.getName());
        msg.setSenderAvatar(user.getAvatarColour());
        msg.setChannel(channelName);
        msg.setContent("⏳ [Time Capsule] " + incoming.getContent());

        timeCapsuleService.schedule(msg, incoming.getDeliverAt(), incoming.getDeliverAtUserCount());

        String desc = incoming.getDeliverAt() != null
            ? "at " + new java.util.Date(incoming.getDeliverAt())
            : "when " + incoming.getDeliverAtUserCount() + " users join";
        send(user, ChatMessage.system("⏳ Time capsule scheduled to deliver " + desc));
    }

    /**
     * Feature: SET_LANG — update user's preferred translation language.
     */
    public void handleSetLang(UserSession user, ChatMessage incoming) {
        if (incoming.getContent() != null) {
            user.setPreferredLanguage(incoming.getContent().trim().toLowerCase());
            send(user, ChatMessage.system("Language set to: " + user.getPreferredLanguage()));
        }
    }

    public void handleDisconnect(UserSession user) {
        channelRegistry.removeMemberFromAll(user.getId());
        userRegistry.remove(user.getId());

        ChatMessage left = new ChatMessage();
        left.setType(ChatMessage.Type.USER_LEFT);
        left.setSenderId(user.getId());
        left.setSenderName(user.getName());
        left.setContent(user.getName() + " left the workspace");
        left.setData(Map.of("users", userSummaries()));
        sendToAll(left);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void broadcastChannelList() {
        ChatMessage msg = new ChatMessage();
        msg.setType(ChatMessage.Type.CHANNEL_LIST);
        msg.setData(Map.of("channels", channelSummaries()));
        sendToAll(msg);
    }

    private void broadcastSentiment(String channel) {
        ChatMessage sm = new ChatMessage();
        sm.setType(ChatMessage.Type.SENTIMENT_UPDATE);
        sm.setChannel(channel);
        sm.setData(sentimentService.getStats(channel));
        channelRegistry.get(channel).ifPresent(ch ->
            ch.getMemberIds().stream()
                .map(id -> userRegistry.get(id).orElse(null))
                .filter(u -> u != null && u.isOpen())
                .forEach(u -> send(u, sm))
        );
    }

    public List<Map<String, Object>> channelSummaries() {
        return channelRegistry.getAll().stream()
            .map(this::channelSummary)
            .collect(Collectors.toList());
    }

    public Map<String, Object> channelSummary(Channel ch) {
        return Map.of(
            "name",          ch.getName(),
            "description",   ch.getDescription(),
            "emoji",         ch.getEmoji(),
            "members",       ch.getMemberCount(),
            "totalMessages", ch.getTotalMessages()
        );
    }

    public List<Map<String, Object>> userSummaries() {
        return userRegistry.getAll().stream()
            .filter(UserSession::isOpen)
            .map(u -> Map.<String, Object>of(
                "id",           u.getId(),
                "name",         u.getName(),
                "avatarColour", u.getAvatarColour(),
                "channels",     u.getJoinedChannels(),
                "messagesSent", u.getMessagesSent(),
                "lang",         u.getPreferredLanguage()
            ))
            .collect(Collectors.toList());
    }

    private List<ChatMessage> historyOf(String channelName) {
        return channelRegistry.get(channelName)
            .map(Channel::getHistory)
            .orElse(List.of());
    }

    public Map<String, Object> stats() {
        return Map.of(
            "totalUsers",    userRegistry.count(),
            "totalChannels", channelRegistry.count(),
            "users",         userSummaries(),
            "channels",      channelSummaries(),
            "sentiment",     sentimentService.getAllStats(),
            "knowledgeCards",knowledgeService.getAll()
        );
    }
}
