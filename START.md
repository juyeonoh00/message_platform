# 🚀 Messenger Platform - 빠른 시작 가이드

Slack 유사 실시간 채팅 플랫폼

## 📋 사전 요구사항

- Docker & Docker Compose
- Java 17+
- Node.js 18+
- Maven

## 🏃 빠른 시작 (3단계)

### 1단계: 인프라 서비스 시작

```bash
# MySQL, Redis, Elasticsearch 시작
docker-compose up -d

# 서비스 확인
docker-compose ps
```

서비스가 healthy 상태가 될 때까지 기다립니다 (약 30초~1분).

### 2단계: 백엔드 시작

```bash
cd backend

# Windows
mvnw.cmd spring-boot:run

# Mac/Linux
./mvnw spring-boot:run
```

백엔드는 http://localhost:8080 에서 실행됩니다.

### 3단계: 프론트엔드 시작

```bash
cd frontend

# 의존성 설치 (최초 1회)
npm install

# 개발 서버 시작
npm run dev
```

프론트엔드는 http://localhost:3000 에서 실행됩니다.

## ✅ 테스트하기

### 1. 회원가입 & 로그인

1. http://localhost:3000 접속
2. Register 클릭
3. 이메일, 비밀번호, 이름 입력
4. 회원가입 완료

### 2. Workspace 생성

1. "Create Workspace" 버튼 클릭
2. Workspace 이름 입력 (예: "My Team")
3. Workspace 카드 클릭

### 3. Channel 자동 생성 및 메시지 전송

1. 왼쪽 사이드바에서 채널 선택
2. 하단 입력창에 메시지 입력
3. Enter 또는 Send 버튼 클릭
4. 실시간으로 메시지가 표시됨

### 4. 실시간 기능 테스트

**두 개의 브라우저 창에서:**
1. 같은 채널에 접속
2. 한쪽에서 메시지 전송
3. 다른 쪽에서 즉시 수신 확인
4. 타이핑 시 "typing..." 표시 확인

## 🎯 주요 기능

### 완료된 기능

✅ JWT 기반 인증 (Access Token + Refresh Token)
✅ Workspace 생성 및 관리
✅ Public/Private Channel 지원
✅ 실시간 메시지 송수신 (WebSocket)
✅ 타이핑 인디케이터
✅ 읽음/안읽음 상태
✅ 메시지 스레드 (Thread)
✅ 멘션 기능 (@user, @channel)
✅ 메시지 검색 (Elasticsearch)
✅ Redis Pub/Sub (분산 환경 지원)

### 아키텍처 플로우

```
메시지 전송:
Client → WebSocket → Backend → DB 저장
                              → Elasticsearch 인덱싱
                              → Redis Pub/Sub
                              → 모든 구독자에게 WebSocket 전송

메시지 검색:
Client → REST API → Elasticsearch → 검색 결과 반환
```

## 📁 프로젝트 구조

```
MessengerPlatform/
├── backend/                # Spring Boot 백엔드
│   ├── src/main/java/com/messenger/
│   │   ├── config/        # Redis, Elasticsearch 설정
│   │   ├── controller/    # REST API 컨트롤러
│   │   ├── dto/           # 요청/응답 DTO
│   │   ├── entity/        # JPA 엔티티
│   │   ├── repository/    # JPA 리포지토리
│   │   ├── security/      # JWT 인증/인가
│   │   ├── service/       # 비즈니스 로직
│   │   ├── websocket/     # WebSocket 핸들러
│   │   └── search/        # Elasticsearch
│   └── pom.xml
│
├── frontend/              # Next.js 프론트엔드
│   ├── src/
│   │   ├── app/          # Next.js 페이지
│   │   ├── components/   # React 컴포넌트
│   │   ├── services/     # API & WebSocket 서비스
│   │   ├── store/        # Redux Toolkit
│   │   └── types/        # TypeScript 타입
│   └── package.json
│
└── docker-compose.yml    # 인프라 서비스
```

## 🔧 환경 변수

### Backend (.env 또는 application.yml)

```yaml
# 이미 설정됨
DB_HOST=localhost
DB_PORT=3306
DB_NAME=messenger_db
REDIS_HOST=localhost
ES_HOST=localhost
JWT_SECRET=your-secret-key
```

### Frontend (.env.local)

```env
# 이미 설정됨
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=http://localhost:8080/ws
```

## 🐛 문제 해결

### Docker 서비스 시작 실패

```bash
# 기존 컨테이너 정리
docker-compose down -v

# 다시 시작
docker-compose up -d
```

### 백엔드 연결 오류

```bash
# MySQL 연결 확인
docker exec -it messenger-mysql mysql -u messenger_user -p messenger_db

# Redis 연결 확인
docker exec -it messenger-redis redis-cli ping

# Elasticsearch 확인
curl http://localhost:9200/_cluster/health
```

### 프론트엔드 빌드 오류

```bash
cd frontend
rm -rf node_modules .next
npm install
npm run dev
```

## 📚 API 엔드포인트

### 인증
- POST `/api/auth/register` - 회원가입
- POST `/api/auth/login` - 로그인
- POST `/api/auth/refresh` - 토큰 갱신

### Workspace
- GET `/api/workspaces` - 내 워크스페이스 목록
- POST `/api/workspaces` - 워크스페이스 생성
- POST `/api/workspaces/{id}/members` - 멤버 추가

### Channel
- GET `/api/channels/workspace/{workspaceId}` - 채널 목록
- POST `/api/channels` - 채널 생성
- POST `/api/channels/{id}/join` - 채널 가입

### Message
- GET `/api/messages/channel/{channelId}` - 메시지 목록
- POST `/api/messages` - 메시지 전송
- GET `/api/messages/thread/{parentId}` - 스레드 조회

### Search
- POST `/api/search` - 메시지 검색

## 🔌 WebSocket 엔드포인트

- **연결**: `/ws` (SockJS)
- **메시지 전송**: `/app/chat.sendMessage`
- **타이핑**: `/app/chat.typing`
- **채널 구독**: `/topic/channel/{channelId}`
- **멘션 수신**: `/user/queue/mentions`

## 🎨 기술 스택

**Backend:**
- Java 17 + Spring Boot 3.2
- Spring WebSocket (STOMP)
- JWT Authentication
- MySQL 8.0
- Redis 7 (Pub/Sub)
- Elasticsearch 8.11
- Maven

**Frontend:**
- Next.js 14 (App Router)
- React 18
- TypeScript
- Redux Toolkit
- SockJS + STOMP
- Axios

**Infrastructure:**
- Docker & Docker Compose

## 📄 라이선스

MIT License

## 🤝 기여

이슈 및 PR 환영합니다!

---

**Made with ❤️ by Senior Full-Stack Engineer**
