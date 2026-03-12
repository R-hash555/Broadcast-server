package com.nxtgen.service;

import com.nxtgen.model.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChannelRegistry.class);
    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();

    public ChannelRegistry() {
        // Default workspace channels
        seed("general",     "Company-wide announcements and work-based matters", "🏠", false);
        seed("engineering", "Engineering discussions, PRs, and tech debt",        "⚙️",  false);
        seed("design",      "Design systems, Figma links, and creative feedback", "🎨", false);
        seed("product",     "Roadmap, feature requests, and user research",       "🗺️",  false);
        seed("random",      "Non-work banter and fun stuff",                      "🎲", false);
        seed("help",        "Get help from teammates on anything",                "🆘", false);
    }

    private void seed(String name, String desc, String emoji, boolean priv) {
        channels.put(name, new Channel(name, desc, emoji, priv));
        log.info("Channel seeded: #{}", name);
    }

    public Channel create(String name, String description, String emoji, boolean priv) {
        String normalized = name.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        return channels.computeIfAbsent(normalized, k -> {
            log.info("Channel created: #{}", k);
            return new Channel(k, description, emoji != null ? emoji : "💬", priv);
        });
    }

    public Optional<Channel> get(String name) {
        return Optional.ofNullable(channels.get(name.toLowerCase()));
    }

    public Collection<Channel> getAll() {
        return Collections.unmodifiableCollection(channels.values());
    }

    public void removeMemberFromAll(String userId) {
        channels.values().forEach(ch -> ch.removeMember(userId));
    }

    public int count() { return channels.size(); }
}
