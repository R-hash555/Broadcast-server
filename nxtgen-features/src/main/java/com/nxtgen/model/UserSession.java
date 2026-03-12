package com.nxtgen.model;

import org.springframework.web.socket.WebSocketSession;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class UserSession {

    private static final String[] AVATAR_COLOURS = {
        "rose","orange","amber","lime","emerald","teal","sky","indigo","violet","pink"
    };

    private final String id;
    private final WebSocketSession wsSession;
    private final Instant connectedAt;
    private volatile String name;
    private final String avatarColour;
    private final Set<String> joinedChannels;
    private final AtomicLong messagesSent = new AtomicLong();
    private volatile long lastSeen;
    private volatile boolean typing;
    private volatile String preferredLanguage = "en"; // Feature: Translation

    public UserSession(WebSocketSession wsSession) {
        this.id            = wsSession.getId();
        this.wsSession     = wsSession;
        this.connectedAt   = Instant.now();
        this.lastSeen      = System.currentTimeMillis();
        this.joinedChannels= Collections.newSetFromMap(new ConcurrentHashMap<>());
        int idx = Math.abs(wsSession.getId().hashCode()) % AVATAR_COLOURS.length;
        this.avatarColour  = AVATAR_COLOURS[idx];
        this.name = "User-" + wsSession.getId().substring(0, 4).toUpperCase();
    }

    public boolean isOpen()       { return wsSession.isOpen(); }
    public void    touch()        { lastSeen = System.currentTimeMillis(); }
    public void    setTyping(boolean t){ this.typing = t; }
    public boolean isTyping()     { return typing; }
    public void    joinChannel(String ch)  { joinedChannels.add(ch); }
    public void    leaveChannel(String ch) { joinedChannels.remove(ch); }
    public boolean inChannel(String ch)    { return joinedChannels.contains(ch); }
    public void    incrementSent()         { messagesSent.incrementAndGet(); }

    public String             getId()              { return id; }
    public WebSocketSession   getWsSession()       { return wsSession; }
    public Instant            getConnectedAt()     { return connectedAt; }
    public String             getName()            { return name; }
    public void               setName(String n)    { this.name = n; }
    public String             getAvatarColour()    { return avatarColour; }
    public Set<String>        getJoinedChannels()  { return Collections.unmodifiableSet(joinedChannels); }
    public long               getMessagesSent()    { return messagesSent.get(); }
    public long               getLastSeen()        { return lastSeen; }
    public String             getPreferredLanguage()          { return preferredLanguage; }
    public void               setPreferredLanguage(String l)  { this.preferredLanguage = l != null ? l : "en"; }
}
