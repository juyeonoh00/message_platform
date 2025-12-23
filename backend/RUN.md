# 백엔드 실행 가이드

## ✅ 수정 완료 사항

### 1. 연관관계 순환 참조 문제 해결
- User, Workspace, Channel, Message 엔티티의 양방향 연관관계 제거
- JSON 직렬화 문제 해결

### 2. 의존성 추가
- WebSocket STOMP 의존성
- Elasticsearch HTTP Client
- MariaDB 드라이버

### 3. 설정 파일 확인
- application.yml: 원격 DB 연결 설정 유지
- Redis, Elasticsearch 로컬 연결

## 🚀 IntelliJ IDEA로 실행 (권장)

### 1단계: 프로젝트 열기
```
File > Open > backend 폴더 선택
```

### 2단계: Gradle Sync
- 자동으로 시작됨
- 우측 하단에서 진행 상태 확인
- 완료까지 1~2분 대기

### 3단계: 애플리케이션 실행
```
src/main/java/com/messenger/MessengerApplication.java
우클릭 > Run 'MessengerApplication'
```

또는 단축키: `Shift + F10`

## ⚠️ 실행 전 체크리스트

### 1. Docker 서비스 실행 확인
```bash
docker ps
```

다음 컨테이너가 실행 중이어야 함:
- messenger-redis (포트 6379)
- messenger-elasticsearch (포트 9200)

### 2. 데이터베이스 연결 확인
- Host: 218.38.54.88
- Port: 3306
- Database: message
- User: root
- Password: wisecan

### 3. 테이블 생성 확인
```sql
USE message;
SHOW TABLES;
```

8개 테이블이 있어야 함:
- users
- workspaces
- workspace_members
- channels
- channel_members
- messages
- mentions
- read_states

## 🐛 자주 발생하는 에러 해결

### 1. Port already in use (8080)
**해결**: application.yml에서 포트 변경
```yaml
server:
  port: 8081
```

### 2. Cannot connect to Redis
**해결**: Docker Redis 재시작
```bash
docker restart messenger-redis
```

### 3. Cannot connect to Elasticsearch
**해결**: Docker Elasticsearch 재시작
```bash
docker restart messenger-elasticsearch
```

### 4. Cannot connect to database
**해결**:
- 원격 DB 서버가 실행 중인지 확인
- 방화벽 설정 확인
- 네트워크 연결 확인

### 5. Gradle build failed
**해결**:
```bash
cd backend
./gradlew clean build --refresh-dependencies
```

### 6. Lombok 관련 에러
**해결**: IntelliJ IDEA 설정
```
File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors
> Enable annotation processing 체크
```

### 7. Bean creation error (Redis/Elasticsearch)
**임시 해결**: 해당 설정 클래스를 일시적으로 비활성화

RedisConfig.java:
```java
// @Configuration  // 주석 처리
public class RedisConfig {
```

ElasticsearchConfig.java:
```java
// @Configuration  // 주석 처리
public class ElasticsearchConfig {
```

## ✅ 성공적인 실행 확인

콘솔에 다음 메시지가 나오면 성공:

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.0)

...
Started MessengerApplication in X.XXX seconds
Tomcat started on port(s): 8080 (http)
```

## 📝 API 테스트

### Postman으로 테스트
```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "password123",
  "name": "Test User"
}
```

성공 시 JWT 토큰이 반환됨

## 💡 추가 도움말

문제가 계속되면:
1. 전체 콘솔 로그 캡처
2. 에러 메시지 전체 복사
3. 스택 트레이스 확인
