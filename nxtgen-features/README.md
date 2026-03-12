# NxtGen — Real-time Team Communication Platform

A Slack-clone built with Spring Boot WebSockets. Features real-time messaging, channels, typing indicators, emoji reactions, and a beautiful warm-toned UI.

## Quick Start

```bash
mvn spring-boot:run
```

Then open http://localhost:8080

## Features
- Real-time WebSocket messaging
- Multiple channels (general, engineering, design, product, random, help)
- Create new channels
- Typing indicators
- Emoji reactions
- Message grouping (Slack-style)
- User presence tracking
- Message history (last 100 per channel)
- Optimistic UI updates
- Auto-reconnect on disconnect

## REST API
- GET  /api/health   — health check
- GET  /api/stats    — server stats
- GET  /api/channels — list channels
- POST /api/channels — create channel
- POST /api/broadcast — send a message
- GET  /api/users    — list users

## WebSocket
Connect to: ws://localhost:8080/ws/nxtgen

Message types (client → server):
- JOIN          { type, senderName }
- MESSAGE       { type, channel, content }
- REACT         { type, channel, emoji, data: messageId }
- TYPING        { type, channel }
- STOP_TYPING   { type, channel }
- CHANNEL_LIST  { type, channel }  ← triggers join
- PING          { type }
