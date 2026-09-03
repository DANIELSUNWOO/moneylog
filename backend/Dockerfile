# syntax=docker/dockerfile:1

# ============================================================
# 1단계: 빌드 스테이지 — jar를 만드는 "공장"
# JDK와 Gradle 캐시가 필요하지만, 이 무거운 것들은 최종 이미지에 남지 않는다.
# ============================================================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 래퍼와 빌드 스크립트를 소스보다 "먼저" 복사한다.
# 도커는 레이어 단위로 캐시하므로, 소스만 바뀌면 아래 의존성 다운로드를 건너뛴다.
# 순서를 바꾸면 코드 한 줄 고칠 때마다 의존성을 통째로 다시 받는다.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src

# 테스트는 CI(GitHub Actions)에서 이미 돌린다. 이미지 빌드는 산출물 생성에만 집중한다.
# 여기서 또 돌리면 배포 때마다 몇 분씩 낭비된다.
RUN ./gradlew bootJar --no-daemon -x test

# ============================================================
# 2단계: 실행 스테이지 — jar만 담는 "배달 상자"
# JRE만 있으면 되므로 이미지가 수백 MB 가벼워지고,
# 소스코드와 빌드 캐시가 최종 이미지에 남지 않아 더 안전하다.
# ============================================================
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 컨테이너 헬스체크와 장애 조사(컨테이너 안에서 curl)를 위해 최소한만 설치한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# root로 실행하지 않는다. 컨테이너가 뚫려도 할 수 있는 일을 최소화한다.
RUN useradd -r -u 1001 -m appuser
USER appuser

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# 고정 -Xmx는 인스턴스 타입을 바꿀 때마다 고쳐야 한다.
# MaxRAMPercentage는 컨테이너에 할당된 메모리를 기준으로 알아서 따라간다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"

# exec를 붙이는 이유: java가 PID 1이 되어 SIGTERM을 직접 받는다.
# 안 붙이면 sh가 신호를 삼켜 컨테이너 종료 시 스프링이 정상 종료(graceful shutdown)를 못 한다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
