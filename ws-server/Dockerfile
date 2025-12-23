# ===== 1) build stage =====
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app

# 캐시 최적화: 먼저 설정/빌드 파일만 복사
COPY build.gradle* settings.gradle* gradle.properties* ./
COPY gradle ./gradle
RUN gradle --no-daemon dependencies || true

# 소스 복사 후 빌드
COPY . .
RUN gradle --no-daemon clean bootJar -x test

# ===== 2) run stage =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# 빌드 결과 jar 복사 (프로젝트에 따라 *SNAPSHOT 등 이름 다를 수 있어 * 사용)
COPY --from=builder /app/build/libs/*.jar app.jar

# 컨테이너 내부 포트(앱이 실제로 listen 하는 포트)
# compose에서 PORT 환경변수로 바꾸면 됨
ENV PORT=8081
EXPOSE 8081

# 스프링이 PORT 환경변수를 쓰게 하려면 application.yml에서 server.port: ${PORT:8081} 형태 권장
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT}"]