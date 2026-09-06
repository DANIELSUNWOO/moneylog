# 1차 팀 프로젝트(prep2gether) 참고 노트

머니로그에 **그대로 가져올 것 / 고쳐서 가져올 것 / 가져오지 않을 것**을 정리한 문서.
원본: `~/ProjectTeam_2` (GitHub: likelion-backend-24th/ProjectTeam_2, 커밋 609개, main + feature 브랜치 다수)

---

## 1. 전체 구조 — 그대로 가져온다

```
ProjectTeam_2/
├── .github/workflows/   backend-ci.yml, frontend-ci.yml, cd.yml
├── backend/             Spring Boot (Gradle), Dockerfile
├── frontend/            React + Vite, Dockerfile, nginx.conf
├── docs/                requirement.md, ERD.png, ddl.sql, sql/ (시드)
├── docker-compose.yml   mysql + backend + frontend
└── .gitignore
```

머니로그도 이 모노레포 구조를 따른다. 이미 `docs/`는 같은 방식으로 쓰고 있다
(P2G는 `requirement.md` + `ddl.sql`, 머니로그는 `requirements.md` + `erd.md` + `schema.sql`).

---

## 2. 그대로 가져올 것 (검증된 패턴)

### 2-1. 멀티스테이지 Dockerfile

**backend** — JDK로 빌드하고 JRE로 실행해 이미지 용량을 줄인다.
```dockerfile
FROM eclipse-temurin:21-jdk AS builder
COPY gradlew . ; COPY gradle gradle ; COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true   # 의존성 레이어 캐싱 (소스 변경 시 재다운로드 방지)
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre AS runtime
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```
> `dependencies` 먼저 실행하는 트릭이 핵심. src보다 build.gradle을 먼저 COPY해야 도커 레이어 캐시가 살아서 빌드가 빨라진다.

**frontend** — node로 빌드 → 산출물만 nginx에 얹는다.
```dockerfile
FROM node:20-alpine AS builder → npm ci → npm run build
FROM nginx:alpine AS runtime   → COPY --from=builder /app/dist /usr/share/nginx/html
```

### 2-2. SPA 라우팅용 nginx.conf
```nginx
location / { try_files $uri $uri/ /index.html; }
```
이게 없으면 `/transactions` 같은 경로로 새로고침할 때 404가 난다.

### 2-3. 백엔드 패키지 구조 — 도메인별 수직 분할

```
org/example/backend/
├── post/                     ← 도메인마다 한 세트
│   ├── controller/  service/  repository/
│   ├── entity/  dto/  exception/PostErrorCode.java
└── common/
    ├── config/     SwaggerConfig, S3Config, AsyncConfig
    ├── dto/        ApiResponse, Meta, PageMeta
    ├── exception/  ErrorCode(interface), BusinessException, GlobalExceptionHandler
    └── util/
```
머니로그 대응: `user/`, `category/`, `transaction/`, `stat/`, (도전)`budget/` + `common/`.
계층별(controller 폴더에 모든 컨트롤러) 대신 **도메인별**로 나누는 이 방식을 유지한다.

### 2-4. 일관된 응답 포맷 3종 세트

```java
ApiResponse<T> { boolean success; String message; T data; Meta meta; String errorCode; }
  → static success(message, data) / success(message, data, meta) / error(errorCode, message)

interface ErrorCode { HttpStatus getHttpStatus(); String getMessage(); String name(); }
  → 도메인마다 PostErrorCode, UserErrorCode … enum이 구현

BusinessException(ErrorCode)  +  @RestControllerAdvice GlobalExceptionHandler
```
머니로그 F-07(전역 예외 + 일관된 에러 형식)이 요구하는 게 정확히 이 구조다. **거의 그대로 재사용.**

### 2-5. 프로파일 분리 + 환경변수 주입
- `application.yaml` (로컬) / `application-prod.yml` (운영, 값은 전부 `${ENV}`)
- 컨테이너에서 `SPRING_PROFILES_ACTIVE: prod`
- `me.paulschwarz:spring-dotenv`로 로컬 `.env` 읽기
- `.env`는 backend/frontend 각각 `.gitignore`에 등록 → **커밋 안 됨(확인 완료)**

### 2-6. CI/CD 구성
- `backend-ci.yml` / `frontend-ci.yml`: `paths:` 필터로 **바뀐 쪽만** 돈다
- `cd.yml`: GHCR 로그인 → 이미지 빌드·푸시 → `appleboy/ssh-action`으로 EC2 접속 → `git pull` + `docker compose pull` + `up -d` + `image prune -f`
- 시크릿: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `GITHUB_TOKEN`(GHCR), 프론트 빌드용 `VITE_*`

### 2-7. 컨트롤러 작성 패턴
```java
@RestController @RequestMapping("/api/posts") @RequiredArgsConstructor
@Tag(name="게시판", description="...")
  @Operation(summary="...", description="...")
  public ResponseEntity<ApiResponse<Page<PostResponse>>> getPost(
      @RequestParam(required=false) PostCategory category,
      @PageableDefault(sort="createdAt", direction=DESC) Pageable pageable,
      @AuthenticationPrincipal CustomUserDetails userDetails)
```
- `@AuthenticationPrincipal CustomUserDetails`로 로그인 사용자를 받아 서비스에 넘기는 흐름 → **머니로그 인가(F-06)의 출발점**
- `Pageable` + `@PageableDefault` → F-04 페이징 그대로 적용
- Swagger 어노테이션을 컨트롤러에 같이 붙이는 방식 → F-09

### 2-8. 프론트 API 레이어
```
src/api/client.js        ← fetch 래퍼(토큰 주입, 에러 처리) 한 곳에
src/api/postApi.js …     ← 도메인별 함수
src/context/AuthContext  ← 로그인 상태·토큰
```
머니로그는 화면 3종이라 `api/client.js` + `authApi.js` + `transactionApi.js` + `categoryApi.js` 정도면 충분.

---

## 3. 고쳐서 가져올 것 (P2G의 아쉬운 점)

| # | P2G 현황 | 머니로그에서는 | 이유 |
|---|----------|----------------|------|
| 1 | compose에서 mysql `ports: 3306:3306` **호스트 노출** | 운영 compose에서 mysql `ports` **제거** (backend가 내부 네트워크 `mysql:3306`으로만 접근) | 인터넷에 노출된 DB는 스캐너에 금방 걸린다. 보안그룹은 22/80/443만. |
| 2 | `ddl-auto: update` (운영) | 운영은 `validate`, 스키마 변경은 명시적으로 | `update`는 컬럼 삭제·타입 변경을 조용히 넘기거나 예기치 않게 바꾼다. 운영 데이터가 걸린 순간 위험. |
| 3 | 이미지 태그가 `:latest` 하나뿐 | `:latest` + `:{{ github.sha }}` **둘 다 태깅** | latest만 있으면 **롤백할 대상이 없다**. 무중단 배포(F-16) 갈 때도 필수. |
| 4 | CI가 워크플로 안에서 heredoc으로 `application.yaml` 생성 | `src/test/resources/application-test.yml`을 리포에 두고 `@ActiveProfiles("test")` | 테스트 설정이 워크플로에 하드코딩되면 로컬 테스트와 CI 테스트가 달라진다. |
| 5 | `handleValidation`이 "입력값이 올바르지 않습니다" 한 줄 | 어떤 필드가 왜 틀렸는지 `data`에 담아 반환 | F-07 요구사항이 "금액>0, 날짜 필수" 같은 구체적 검증이라 프론트가 필드별 에러를 표시해야 한다. |
| 6 | CD가 `workflow_dispatch`(수동)만, 자동 배포는 주석 처리 | 기본은 수동 유지, 안정화 후 `workflow_run` 자동 배포 켜기 | 수동이 나쁜 건 아니지만, "CI 통과 → 자동 배포"를 한 번은 보여주는 게 DevOps 어필에 좋다. |
| 7 | `ApiResponse`의 에러 필드명이 `errorCode` | 머니로그 SPEC은 `{success:false, code, message, data:null}` | 필드명을 SPEC에 맞출지 P2G에 맞출지 1-4(API 명세)에서 결정할 것. |
| 8 | `cd.yml` 1개로 backend/frontend 배포를 같이 처리 | `cd-backend.yml` / `cd-frontend.yml`로 분리, 각자 `paths` 필터로 자기 쪽만 빌드·배포 | CI를 이미 두 파일로 나눴으니 CD도 같은 규칙(컴포넌트 하나당 파일 하나)으로 맞춰 일관성을 준다. 프론트만 바뀐 커밋에 백엔드 컨테이너까지 재시작시키는 낭비도 막는다. 단점은 한 커밋에 둘 다 바뀌면 두 배포가 병렬로 돌아 아주 짧게 프론트·백엔드 버전이 어긋나는 순간이 생길 수 있다는 것 — 서비스 하나짜리 규모라 감수 가능하다고 판단. |

---

## 4. 가져오지 않을 것

P2G에만 필요했던 것들 — 머니로그 범위(Out of Scope)에 없다.

- PortOne 결제/구독/빌링키, 이메일 인증(spring-boot-starter-mail)
- S3 파일 업로드 (`S3Config`, `FileStorageService`, `ImageValidator`) — 영수증 첨부는 Out of Scope
- 소셜 로그인 3종(Kakao/Google/Naver) — 도전 과제에서도 "선택"
- 역할 기반 권한(USER/EXPERT/ADMIN) — 머니로그는 **모든 사용자가 동등**하고, 인가는 "내 데이터만"이라는 소유권 기반이다. 이 차이가 중요.
- `AsyncConfig`, `AfterCommitExecutor`, 알림(notification), 신고(report)

---

## 5. 다음에 이 문서를 쓸 시점

- **1-5 프로젝트 셋업**: 폴더 구조, `build.gradle`(P2G에서 결제·메일·S3 의존성만 빼면 거의 그대로), `.gitignore`
- **2일차 CRUD**: 도메인 패키지 구조, `ApiResponse`/`ErrorCode`/`BusinessException`/`GlobalExceptionHandler`
- **3일차 인증·인가**: `CustomUserDetails` + `@AuthenticationPrincipal` 흐름, JWT(jjwt) 설정
- **4일차 배포**: Dockerfile 2종, nginx.conf, compose, 워크플로 3종 — 위 3장의 개선사항 반영해서
