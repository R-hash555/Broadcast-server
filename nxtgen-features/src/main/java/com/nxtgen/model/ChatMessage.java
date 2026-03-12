package com.nxtgen.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    public enum Type {
        JOIN, LEAVE, MESSAGE, TYPING, STOP_TYPING, PING, REACT,
        WELCOME, USER_JOINED, USER_LEFT, CHANNEL_MESSAGE, DIRECT_MESSAGE,
        SYSTEM, PONG, ERROR, REACTION,
        USER_LIST, CHANNEL_LIST, TYPING_INDICATOR,
        KNOWLEDGE_CARD,
        PRIORITY_MESSAGE,
        TIME_CAPSULE,
        SENTIMENT_UPDATE,
        ATTENTION_ROUTE
    }

    public enum Priority { NORMAL, IMPORTANT, CRITICAL }

    private String id;
    private Type type;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String channel;
    private String content;
    private Object data;
    private String emoji;
    private long timestamp;
    private Priority priority;
    private Long deliverAt;
    private Long deliverAtUserCount;
    private String originalContent;
    private String originalLang;

    public ChatMessage() {
        this.id        = UUID.randomUUID().toString();
        this.timestamp = Instant.now().toEpochMilli();
        this.priority  = Priority.NORMAL;
    }

    public static ChatMessage of(Type type, String content) {
        ChatMessage m = new ChatMessage();
        m.setType(type);
        m.setContent(content);
        return m;
    }

    public static ChatMessage system(String content) { return of(Type.SYSTEM, content); }
    public static ChatMessage error(String content)  { return of(Type.ERROR,  content); }
    public static ChatMessage pong()                 { return of(Type.PONG,   "pong");  }

    public String getId()             { return id; }
    public void setId(String id)      { this.id = id; }
    public Type getType()             { return type; }
    public void setType(Type type)    { this.type = type; }
    public String getSenderId()       { return senderId; }
    public void setSenderId(String s) { this.senderId = s; }
    public String getSenderName()     { return senderName; }
    public void setSenderName(String s){ this.senderName = s; }
    public String getSenderAvatar()   { return senderAvatar; }
    public void setSenderAvatar(String s){ this.senderAvatar = s; }
    public String getChannel()        { return channel; }
    public void setChannel(String c)  { this.channel = c; }
    public String getContent()        { return content; }
    public void setContent(String c)  { this.content = c; }
    public Object getData()           { return data; }
    public void setData(Object d)     { this.data = d; }
    public String getEmoji()          { return emoji; }
    public void setEmoji(String e)    { this.emoji = e; }
    public long getTimestamp()        { return timestamp; }
    public void setTimestamp(long t)  { this.timestamp = t; }
    public Priority getPriority()           { return priority; }
    public void setPriority(Priority p)     { this.priority = p; }
    public Long getDeliverAt()              { return deliverAt; }
    public void setDeliverAt(Long d)        { this.deliverAt = d; }
    public Long getDeliverAtUserCount()     { return deliverAtUserCount; }
    public void setDeliverAtUserCount(Long d){ this.deliverAtUserCount = d; }
    public String getOriginalContent()      { return originalContent; }
    public void setOriginalContent(String s){ this.originalContent = s; }
    public String getOriginalLang()         { return originalLang; }
    public void setOriginalLang(String s)   { this.originalLang = s; }
}
