package com.nxtgen.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtgen.model.ChatMessage;
import com.nxtgen.model.UserSession;
import com.nxtgen.service.MessagingService;
import com.nxtgen.service.UserRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class NxtGenWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NxtGenWebSocketHandler.class);

    @Autowired private UserRegistry     userRegistry;
    @Autowired private MessagingService messagingService;
    @Autowired private ObjectMapper     objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        UserSession user = userRegistry.register(wsSession);
        log.info("Connection established: {} ({})", user.getName(), user.getId());
        messagingService.handleJoin(user, new ChatMessage());
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage raw) {
        UserSession user = userRegistry.get(wsSession.getId()).orElse(null);
        if (user == null) return;
        user.touch();

        ChatMessage msg;
        try {
            msg = objectMapper.readValue(raw.getPayload(), ChatMessage.class);
        } catch (Exception e) {
            messagingService.send(user, ChatMessage.error("Invalid JSON: " + e.getMessage()));
            return;
        }

        if (msg.getType() == null) {
            messagingService.send(user, ChatMessage.error("type field is required"));
            return;
        }

        log.debug("[{}] {} → {}", msg.getType(), user.getName(), msg.getContent());

        switch (msg.getType()) {
            case JOIN          -> messagingService.handleJoin(user, msg);
            case MESSAGE       -> messagingService.handleMessage(user, msg);
            case REACT         -> messagingService.handleReact(user, msg);
            case TYPING        -> messagingService.handleTyping(user, msg, true);
            case STOP_TYPING   -> messagingService.handleTyping(user, msg, false);
            case TIME_CAPSULE  -> messagingService.handleTimeCapsule(user, msg);  // Feature 6
            case CHANNEL_LIST  -> {
                if (msg.getChannel() != null) {
                    messagingService.handleChannelJoin(user, msg);
                } else {
                    ChatMessage out = new ChatMessage();
                    out.setType(ChatMessage.Type.CHANNEL_LIST);
                    out.setData(messagingService.channelSummaries());
                    messagingService.send(user, out);
                }
            }
            case PING          -> messagingService.send(user, ChatMessage.pong());
            case SYSTEM        -> {
                // SET_LANG: client sends {type:"SYSTEM", content:"hi"} for language change
                if (msg.getContent() != null && msg.getContent().startsWith("SET_LANG:")) {
                    msg.setContent(msg.getContent().substring(9).trim());
                    messagingService.handleSetLang(user, msg);
                } else {
                    messagingService.send(user, ChatMessage.error("Unsupported SYSTEM command"));
                }
            }
            default            -> messagingService.send(user, ChatMessage.error("Unsupported type: " + msg.getType()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        userRegistry.get(wsSession.getId()).ifPresent(user -> {
            log.info("Disconnected: {} ({})", user.getName(), status);
            messagingService.handleDisconnect(user);
        });
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable ex) {
        log.error("Transport error {}: {}", wsSession.getId(), ex.getMessage());
        userRegistry.get(wsSession.getId()).ifPresent(messagingService::handleDisconnect);
    }
}
