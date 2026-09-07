# 트러블슈팅: HTTPS 도메인 전환 후 회원가입이 막힘 (CORS)

## 증상

OIDC+SSM 배포 전환을 끝내고 `https://13-125-116-52.sslip.io`로 실제 서비스에 접속해 회원가입을 시도했더니, 화면에 "요청을 처리하지 못했습니다"라는 일반적인 오류만 뜨고 가입이 되지 않았다. 필드별 검증 오류(예: "이미 가입된 이메일입니다")가 아니라 이런 뭉뚱그려진 메시지가 떴다는 건, 백엔드가 요청을 정상적으로 처리하고 응답한 게 아니라 그 이전 단계에서 막혔다는 신호였다.

## 진단

먼저 컨테이너 자체가 죽은 건 아닌지 EC2에 SSM으로 접속해 확인했다.

```bash
docker compose ps
docker compose logs backend --tail 50
```

`moneylog-backend`는 `Up (healthy)` 상태였고, 로그에도 기동 과정에서 에러가 전혀 없었다. 컨테이너는 멀쩡하다는 뜻이었다.

다음으로 브라우저 개발자 도구(F12) → Network 탭에서 실패한 `signup` 요청을 직접 열어봤다. **Response 탭에 `Invalid CORS request`라는 문구와 함께 상태 코드 403**이 찍혀 있었다. 이건 Spring Security의 CORS 필터(`DefaultCorsProcessor`)가 요청을 거부할 때 내보내는 고정 문자열이다. 즉 요청이 컨트롤러 코드까지 도달하지도 못하고, CORS 검사 단계에서 이미 막힌 것이었다.

## 원인

백엔드 소스(`SecurityConfig.java`)를 확인해보니, CORS 허용 origin 목록이 이렇게 설정돼 있었다.

```java
@Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost}")
private String allowedOrigins;
```

기본값에 로컬 개발 주소만 들어있고, 이번 주에 새로 만든 운영 도메인 `https://13-125-116-52.sslip.io`는 어디에도 없었다. HTTPS 작업 전에는 이 화면까지 실제로 테스트해본 적이 없어서 발견되지 않고 있던 문제였다.

Spring의 CORS 필터는 "이 요청이 나 자신과 같은 서버로 가는 것인지"를 판단하는 게 아니라, 설정된 화이트리스트 문자열과 요청의 `Origin` 헤더를 그대로 비교한다. nginx가 `/api`를 프록시해서 브라우저 입장에서는 사실상 같은 origin으로 동작하게 설계했더라도, 그 origin 문자열 자체가 화이트리스트에 없으면 그대로 거부된다.

## 해결

다행히 `allowedOrigins`가 하드코딩이 아니라 `@Value`로 외부화되어 있었기 때문에, **코드를 고치거나 이미지를 다시 빌드할 필요 없이 환경변수 하나만 추가**하면 됐다. Spring Boot는 `app.cors.allowed-origins`를 `APP_CORS_ALLOWED_ORIGINS` 환경변수와 자동으로 매핑한다(relaxed binding).

`docker-compose.yml`의 backend 서비스에 추가:

```yaml
      APP_CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-https://13-125-116-52.sslip.io}
```

여기서 한 가지 다시 확인한 부분: CD 파이프라인은 GHCR에서 **이미지만** pull해서 배포하지, `docker-compose.yml` 파일 자체를 EC2로 동기화하지는 않는다. 그래서 이 파일은 로컬 저장소뿐 아니라 EC2의 `/home/ec2-user/moneylog/docker-compose.yml`에도 직접 같은 내용을 추가해야 했다(HTTPS 작업 때 443 포트를 추가할 때도 똑같이 겪었던 부분). 수정 후:

```bash
docker compose up -d backend
```

으로 컨테이너를 재시작했다. 코드가 아니라 환경변수만 바뀐 것이라 새 이미지를 pull할 필요조차 없었다.

## 검증 중 곁가지로 만난 것: MySQL root 비밀번호 불일치

수정이 실제로 반영됐는지 DB에서 직접 확인하려고 `docker exec -it moneylog-mysql mysql -u root -p`로 접속을 시도했는데, `.env`에 적힌 `DB_ROOT_PASSWORD`를 그대로 넣었는데도 `Access denied`가 났다.

원인은 MySQL 공식 이미지가 `MYSQL_ROOT_PASSWORD` 환경변수를 **데이터 볼륨이 처음 만들어질 때 딱 한 번만** 적용한다는 점이었다. 그 이후로 `.env`의 값이 바뀌어도, 이미 초기화된 볼륨 안의 실제 root 비밀번호는 갱신되지 않는다. `moneylog-mysql`은 이미 오래전에 초기화된 볼륨을 계속 재사용해왔기 때문에 지금 `.env`의 값과 어긋나 있었던 것으로 보인다.

root로 들어갈 필요는 없었다 — 애플리케이션이 실제로 쓰는 `DB_USER`/`DB_PASSWORD`(root가 아닌 전용 계정)는 방금 백엔드가 정상적으로 접속에 성공한 로그로 이미 검증된 상태였기 때문에, 이 계정으로 조회하면 됐다. 비밀번호를 손으로 옮겨 적다가 오타가 나는 것도 피하기 위해 `.env`를 셸 환경변수로 그대로 불러와서 썼다.

```bash
export $(grep -v '^#' .env | xargs)
docker exec -it moneylog-mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" -e "SELECT id, email, nickname, created_at FROM moneylog.users;"
```

## 결과

재시도한 회원가입이 실제로 DB에 반영된 것을 직접 확인했다.

```
+----+-------------------+----------+----------------------------+
| id | email             | nickname | created_at                 |
+----+-------------------+----------+----------------------------+
|  1 | sunwo04@naver.com | ...      | 2026-09-07 08:35:04.434779 |
+----+-------------------+----------+----------------------------+
```

## 배운 점

로컬 개발 환경에서 잘 되던 게 운영에서 막히는 전형적인 사례였다. 원인은 코드 버그가 아니라 "새 환경(도메인)이 생겼는데 그 환경을 아직 화이트리스트에 등록하지 않았다"는, 환경 설정의 누락이었다. 이런 문제는 로컬 테스트만으로는 절대 잡히지 않고, 실제 배포된 도메인으로 브라우저에서 직접 눌러봐야만 드러난다 — HTTPS 전환을 끝내자마자 실제 화면에서 회원가입까지 끝까지 눌러본 게 이 문제를 발견한 유일한 방법이었다.

또한 `SecurityConfig`에서 CORS 허용 origin을 처음부터 하드코딩이 아니라 `@Value`로 외부화해둔 설계 덕분에, 애플리케이션 코드를 재빌드하지 않고 배포 인프라(`docker-compose.yml`) 쪽 설정만으로 문제를 해결할 수 있었다. 환경별로 달라질 값을 코드가 아니라 환경변수로 주입하도록 설계해두는 것(12-factor 원칙)이 실제로 운영 중 문제를 얼마나 빨리 해결할 수 있는지 보여주는 사례였다.

## 곁가지 회고: DB를 매번 SSM+CLI로 들여다보는 게 맞는가

검증 과정에서 SSM 터미널에 접속해 `mysql` CLI로 직접 쿼리를 날렸는데, 이게 "서비스 운영자가 일상적으로 해야 하는 방식"인지 스스로 질문하게 됐다. 정리한 결론은 이렇다.

지금처럼 **가끔, 목적이 분명한** 확인(배포 후 검증, 버그 재현, 트러블슈팅)에는 SSM+CLI 방식으로 충분하고 실무에서도 흔히 쓰는 방법이다. 하지만 서비스가 커지면 아래 방향으로 자연스럽게 옮겨간다.

- 매번 SSM 터미널을 열고 CLI를 치는 대신, **SSM 포트 포워딩**(`aws ssm start-session --document-name AWS-StartPortForwardingSession`)으로 로컬 PC의 포트를 EC2의 3306으로 터널링해서, DBeaver나 MySQL Workbench 같은 GUI 클라이언트로 마치 로컬 DB처럼 편하게 접속한다. 22번 포트를 열 필요 없이 SSM만으로 가능하다.
- "가입자 수 확인", "특정 유저 삭제 요청 처리"처럼 **반복되는 업무**는 SQL을 직접 짜는 대신 관리자 화면(어드민 대시보드)을 앱에 만들거나, 최소한 자주 쓰는 쿼리를 스크립트로 남겨둔다.
- 실제 사용자 데이터가 있는 운영 DB에 root로 자유롭게 접근할 수 있다는 것 자체가 보안·감사 관점에서는 최소화 대상이다. SSM Session Manager 접속 기록은 CloudTrail에 남기 때문에, 팀 규모가 커지면 "누가 언제 무엇에 접근했는지"를 이 로그로 추적하는 방향으로 이어진다(CloudWatch/CloudTrail 작업 때 이어서 다룰 예정).

### 다음에 시도해볼 것: SSM 포트 포워딩 + GUI DB 클라이언트

위 논의를 실제로 구현해보기로 했다. `aws ssm start-session`의 포트 포워딩 기능으로 로컬 PC에서 EC2의 MySQL(3306)에 터널을 뚫고, DBeaver 같은 GUI 클라이언트로 접속하는 걸 다음 작업으로 남겨둔다. 이것도 22번 포트나 3306을 인터넷에 열지 않고 SSM의 인증·감사 경로 그대로 활용하는 방식이라, OIDC+SSM 전환과 같은 맥락의 "운영 기본기" 작업이다.
