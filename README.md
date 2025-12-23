# Messenger Platform - Slack Clone

Real-time workspace-based chat service similar to Slack.

## Tech Stack

### Frontend
- React + TypeScript + Next.js
- Redux Toolkit (RTK)
- WebSocket for real-time communication

### Backend
- Java + Spring Boot
- REST API + WebSocket Gateway
- JWT Authentication
- MySQL Database
- Redis (Pub/Sub)
- Elasticsearch (Message Search)

## Project Structure

```
MessengerPlatform/
├── backend/           # Spring Boot application
├── frontend/          # Next.js application
├── docker-compose.yml # Infrastructure services
└── README.md
```

## Quick Start

### 1. Start Infrastructure Services

```bash
docker-compose up -d
```

This will start:
- MySQL (port 3306)
- Redis (port 6379)
- Elasticsearch (port 9200)

### 2. Start Backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend will run on http://localhost:8080

### 3. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend will run on http://localhost:3000

## Environment Variables

### Backend (.env or application.yml)
```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=messenger_db
DB_USER=messenger_user
DB_PASSWORD=messenger_password

REDIS_HOST=localhost
REDIS_PORT=6379

ES_HOST=localhost
ES_PORT=9200

JWT_SECRET=your-secret-key-change-in-production
JWT_ACCESS_TOKEN_EXPIRATION=3600000
JWT_REFRESH_TOKEN_EXPIRATION=604800000
```

### Frontend (.env.local)
```
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
```

## Features

- ✅ Workspace-based organization
- ✅ Public/Private Channels
- ✅ Real-time messaging
- ✅ Message threads
- ✅ Mentions (@user, @channel)
- ✅ Read/Unread states
- ✅ Typing indicators
- ✅ Message search (Elasticsearch)
- ✅ Direct messages (1-on-1 and group)

## Architecture

### Message Flow
1. Client → WebSocket → Backend
2. Backend → DB (persist message)
3. Backend → Elasticsearch (index message)
4. Backend → Redis Pub/Sub (broadcast)
5. Redis → All backend instances → WebSocket → Clients

### Authentication
- JWT-based authentication
- Access Token (1 hour)
- Refresh Token (7 days)
