# 트러블슈팅: README에 적힌 로컬 실행이 실제로는 되지 않았다

## 배경: 왜 확인하게 됐나

코드 리뷰에서 한 가지가 걸렸다. README는 저장소를 클론한 사람에게 `docker compose up -d --build` 한 줄로 서비스가 뜬다고 안내하는데, **그 명령을 실제로 돌려본 적이 한 번도 없었다.**

돌려볼 이유가 없었기 때문이다. CI(GitHub Actions)는 러너에서 이미지를 빌드하고, EC2는 GHCR에서 **완성된 이미지를 받아쓰기만** 한다. 즉 "로컬에서 이미지를 빌드해 세 컨테이너를 띄우는" 경로는 아무도 밟은 적이 없는 길이었다.

저장소를 처음 여는 사람이 가장 먼저 실행할 명령이 실패한다면 그것만으로 신뢰를 잃는다. 문서를 믿지 않고 직접 돌려보기로 했다.

결과적으로 **서로 다른 원인 세 개**가 나왔고, 셋 다 CI에서는 구조적으로 잡힐 수 없는 종류였다.

---

## 이슈 1: Gradle 배포판을 내려받지 못해 백엔드 이미지 빌드가 실패

### 증상

```
Downloading https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
Exception in thread "main" java.io.IOException: ... failed: timeout (10000ms)
Caused by: java.net.SocketTimeoutException: Connect timed out
```

### 진단

**왜 지금까지 안 드러났는지부터 설명이 됐다.** 호스트에서 `./gradlew test`는 멀쩡히 돌아간다 — 호스트에는 Gradle 배포판이 이미 캐시돼 있어 받을 필요가 없기 때문이다. 컨테이너는 캐시가 비어 있으니 매번 새로 받아야 하고, 그 경로가 처음 밟히면서 문제가 드러났다.

다음으로 네트워크가 정말 막혔는지 확인했다.

```bash
curl.exe -I https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
# HTTP/1.1 307 Temporary Redirect
# Location: https://github.com/gradle/gradle-distributions/releases/download/v8.14.3/...
```

`services.gradle.org`는 파일을 직접 주지 않고 GitHub 릴리스로 넘긴다. 그리고 스택 트레이스를 다시 보면 실패 지점이 `followRedirect` **이후**였다. 즉 막힌 건 첫 주소가 아니라 리다이렉트 대상이었다.

리다이렉트까지 따라가며 실제로 받아봤다.

```bash
docker run --rm curlimages/curl:latest -s -o /dev/null -L -r 0-1023 \
  -w "code=%{http_code} connect=%{time_connect}s\n" \
  https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
# code=206 connect=0.469120s
```

**컨테이너에서도 성공했고 심지어 빠르다.** 그런데 빌드 안에서만 실패한다. 여기서 `docker run`과 `docker build`의 차이가 용의선상에 올랐다.

결정적 단서는 재시도했을 때 에러가 바뀐 것이었다. `Connect timed out`(응답 없음)에서 **`Connection refused`(능동적 거부)**로 변했다. 대역폭 부족이나 혼잡이 원인이라면 거부는 나오지 않는다. 누군가 연결을 끊고 있다는 뜻이었다.

### 근본 원인

Docker Desktop의 **Containers proxy가 "Same as host proxy"**로 설정돼 있었다. 실행 컨테이너와 빌드(BuildKit) 컨테이너는 Docker Desktop 안에서 네트워크 처리 경로가 다르다. 그래서 `docker run`으로 띄운 컨테이너는 직접 나가 성공했고, 빌드만 프록시를 타다 거부당했다.

### 해결

Containers proxy를 "No proxy"로 바꿨다. 이미지 pull과 컨테이너 curl이 모두 정상이었으므로 직접 연결이 가능한 네트워크임은 이미 확인된 상태였다.

더불어 `gradle-wrapper.properties`의 `networkTimeout`을 기본값 10초에서 60초로 올렸다. 이건 이 PC의 프록시 문제와는 별개다. **콜드 빌드에서는 JDK 이미지(158MB) 등을 병렬로 받는 중에 배포판 연결을 시도하게 되는데, 그 상황에서 10초는 짧다.** 그리고 그 조건에 정확히 놓이는 사람이 바로 저장소를 처음 클론해 빌드하는 사람이다.

---

## 이슈 2: nginx가 인증서를 찾지 못해 프론트엔드 컨테이너가 무한 재시작

### 증상

빌드는 성공했는데 컨테이너 하나가 살아나지 못했다.

```
moneylog-frontend   Restarting (1) 29 seconds ago
```

```
nginx: [emerg] cannot load certificate
"/etc/letsencrypt/live/sunwoomoneylog.duckdns.org/fullchain.pem":
No such file or directory
```

### 근본 원인

`frontend/nginx.conf`는 운영 환경을 전제로 쓰였다. HTTP로 들어온 요청을 전부 HTTPS로 돌리고, HTTPS 블록은 Let's Encrypt 인증서를 참조한다. **그 인증서는 EC2에만 있다.** 개발 PC에는 파일이 없으니 nginx가 설정을 읽는 단계에서 죽고, `restart: unless-stopped` 정책 때문에 죽고 다시 뜨기를 반복했다.

이 설정 파일은 이미지 빌드 시점에 이미지 안으로 복사된다. 따라서 로컬에서만 다르게 하려면 이미지를 다시 만들거나, 실행 시점에 덮어써야 한다.

### 해결

`frontend/nginx.local.conf`를 새로 만들고, `docker-compose.override.yml`에서 컨테이너의 설정 파일 위에 마운트했다.

```yaml
frontend:
  volumes:
    - ./frontend/nginx.local.conf:/etc/nginx/conf.d/default.conf:ro
```

**운영 설정 파일은 한 글자도 건드리지 않았다.** 이 저장소의 "base는 운영, override는 로컬" 원칙과 같은 방식이고, 이미지를 다시 빌드할 필요도 없다.

로컬 설정에서는 두 가지를 일부러 뺐다. HTTPS 블록과 HTTP→HTTPS 리다이렉트는 인증서가 없으니 당연히 빼고, 운영에 있는 `/assets/` 1년 캐시도 뺐다. 개발 중에 브라우저가 옛 빌드 산출물을 붙들고 있으면 수정이 반영되지 않아 오히려 방해가 되기 때문이다.

---

## 이슈 3: IDE 실행과 컨테이너가 8080을 두고 충돌

검증 도중 IntelliJ에서 백엔드를 실행하자 이렇게 떴다.

```
Web server failed to start. Port 8080 was already in use.
```

`docker-compose.override.yml`이 로컬에서만 백엔드의 8080을 호스트로 열어두기 때문이다(Swagger 직접 접근용). 즉 **백엔드를 띄우는 방법이 IDE 실행과 컨테이너 두 가지가 됐고, 같은 포트를 쓰므로 동시에는 못 띄운다.**

고칠 문제라기보다 알고 있어야 하는 제약이다. 굳이 둘을 동시에 띄우려면 override에서 호스트 포트를 바꾸면 되지만, 그럴 필요가 있는 상황이 아니라 그대로 두었다.

---

## 결과

세 컨테이너가 모두 정상 기동했고, 브라우저에서 회원가입 → 로그인 → 인증된 화면 진입까지 확인했다. 브라우저 → nginx(80) → `/api` 프록시 → Spring Boot → MySQL 전 구간이 이어진다는 뜻이다.

```
moneylog-backend    Up (healthy)   0.0.0.0:8080->8080/tcp
moneylog-frontend   Up             0.0.0.0:80->80/tcp
moneylog-mysql      Up (healthy)   0.0.0.0:3307->3306/tcp
```

## 배운 점

- **CI가 초록불인 것과 "누구나 실행할 수 있다"는 완전히 다른 명제다.** 이번에 나온 세 원인은 CI에서 잡힐 수가 없었다 — GitHub 러너에는 프록시가 없고, EC2는 완성된 이미지를 받아쓰며 인증서도 거기엔 있다. 각 환경은 자기가 밟는 경로만 검증한다.
- **아무도 밟지 않는 경로는 조용히 썩는다.** 로컬 빌드 경로는 문서에는 있는데 실행되지는 않는 상태로 방치돼 있었다. 문서에 적었다면 최소 한 번은 그대로 따라해봐야 한다.
- **에러 메시지의 변화가 진단의 핵심 단서였다.** `Connect timed out`이 `Connection refused`로 바뀌지 않았다면 대역폭 문제로 오진한 채 타임아웃만 계속 늘렸을 것이다. 같은 실패도 실패하는 방식이 다르면 원인이 다르다.
- **환경 차이는 설정 파일을 고쳐서가 아니라 덮어써서 흡수하는 게 낫다.** 로컬을 위해 `nginx.conf`를 조건부로 만들었다면 운영 설정이 복잡해졌을 것이다. 운영본은 단순하게 두고 로컬에서만 다른 파일을 얹는 쪽이 양쪽 모두 읽기 쉽다.
