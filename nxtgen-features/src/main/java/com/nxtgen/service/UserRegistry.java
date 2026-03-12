package com.nxtgen.service;

import com.nxtgen.model.UserSession;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserRegistry {

    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession register(WebSocketSession ws) {
        UserSession user = new UserSession(ws);
        sessions.put(user.getId(), user);
        return user;
    }

    public Optional<UserSession> get(String id)       { return Optional.ofNullable(sessions.get(id)); }
    public void remove(String id)                     { sessions.remove(id); }
    public Collection<UserSession> getAll()           { return Collections.unmodifiableCollection(sessions.values()); }
    public int count()                                { return sessions.size(); }
}
