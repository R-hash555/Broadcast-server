package com.nxtgen.service;

import com.nxtgen.model.ChatMessage;
import com.nxtgen.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    private static final long STALE_MS = 90_000;

    @Autowired private UserRegistry    userRegistry;
    @Autowired private MessagingService messagingService;

    @Scheduled(fixedDelay = 30_000)
    public void ping() {
        ChatMessage ping = ChatMessage.of(ChatMessage.Type.PING, "ping");
        List<UserSession> all = List.copyOf(userRegistry.getAll());

        for (UserSession user : all) {
            if (!user.isOpen()) {
                messagingService.handleDisconnect(user);
                continue;
            }
            long idle = System.currentTimeMillis() - user.getLastSeen();
            if (idle > STALE_MS) {
                log.warn("Stale user {}, disconnecting", user.getName());
                messagingService.handleDisconnect(user);
            } else {
                messagingService.send(user, ping);
                user.touch();
            }
        }
    }
}
