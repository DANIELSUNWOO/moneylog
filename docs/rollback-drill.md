# 롤백 리허설 기록

## 배경

`docker-compose.yml`은 처음부터 `image: ghcr.io/.../moneylog-backend:${IMAGE_TAG:-latest}` 형태로, `IMAGE_TAG` 환경변수로 배포할 이미지 태그를 바꿔 끼울 수 있게 설계해뒀다. CD 파이프라인도 이미지를 `:latest`와 `:{git-sha}` 두 태그로 함께 GHCR에 올린다. 하지만 실제로 CD가 배포에 쓰는 값은 항상 기본값인 `:latest`뿐이었기 때문에, "롤백 가능한 구조"는 있었지만 "실제로 롤백해본 적"은 없는 상태였다. 이 문서는 그 구조가 실제로 동작하는지 검증한 기록이다.

리허설 대상으로는 애플리케이션 코드(`backend/`)가 아니라 `.github/workflows/`만 바뀐 두 커밋 사이의 이미지를 선택했다. 워크플로를 `workflow_dispatch`로 수동 실행하면 `paths` 필터와 무관하게 `build-and-push`가 항상 실행되기 때문에, 그 과정에서 이미 GHCR에 두 개의 실제 이미지가 쌓여 있었다.

- `39cd785b6ca6544e05579928586e535b78c5b5dc` — main에 머지된 현재 버전
- `d48dc4148df38e90beaff5d1fbe00b55f1d5a57d` — 그 이전 테스트 커밋 버전

두 이미지 사이에는 애플리케이션 코드나 DB 스키마 변화가 없기 때문에, "롤백 메커니즘 자체"만 리스크 없이 검증하기에 적합했다.

## 설계: `.env`에 쓰지 않고 명령 단위로만 태그를 넘긴다

롤백을 `.env` 파일에 `IMAGE_TAG=<sha>`로 영구히 써넣는 대신, 명령어 앞에 `IMAGE_TAG=<sha>`를 붙여 **그 한 번의 명령에만 적용되는 값**으로 넘겼다.

```bash
IMAGE_TAG=d48dc4148df38e90beaff5d1fbe00b55f1d5a57d docker compose pull backend
IMAGE_TAG=d48dc4148df38e90beaff5d1fbe00b55f1d5a57d docker compose up -d backend
```

`.env`에 영구히 남기면, 다음번 정상적인 자동 배포(CD)가 실행될 때 이 값을 모르는 채로 넘어가 옛날 sha를 계속 배포하게 되는 사고로 이어질 수 있다. 명령 단위로만 넘기면 롤백 직후 아무 흔적도 남지 않아, 다음 CD 실행은 다시 정상적으로 `:latest`를 배포한다. 또한 `docker compose up -d backend`처럼 대상 서비스를 `backend`로 명시했기 때문에, `IMAGE_TAG`라는 변수 이름이 frontend 서비스 정의에도 등장하지만 frontend 컨테이너는 이 실행에서 전혀 평가되지 않는다.

## 실행 결과

AWS 콘솔의 SSM Session Manager로 EC2에 접속해(포트 22 없이) 아래 순서로 진행했다.

**1) 롤백 실행**

```
$ IMAGE_TAG=d48dc4148df38e90beaff5d1fbe00b55f1d5a57d docker compose pull backend
$ IMAGE_TAG=d48dc4148df38e90beaff5d1fbe00b55f1d5a57d docker compose up -d backend
[+] up 2/2
 ✔ Container moneylog-mysql    Healthy
 ✔ Container moneylog-backend  Started
```

**2) 검증**

```
$ docker inspect --format='롤백 후 이미지: {{.Config.Image}}' moneylog-backend
롤백 후 이미지: ghcr.io/danielsunwoo/moneylog-backend:d48dc4148df38e90beaff5d1fbe00b55f1d5a57d

$ docker compose logs backend --tail 20
...
moneylog-backend | Tomcat started on port 8080 (http) with context path '/'
moneylog-backend | Started BackendApplication in 13.002 seconds (process running for 14.288)
```

`docker inspect`로 지정한 sha 이미지가 정확히 떠 있음을 확인했고, 기동 로그에서도 데이터소스 연결·JPA 초기화·Tomcat 기동까지 정상적으로 완료됐음을 확인했다. (재기동 직후 `curl`로 헬스체크를 확인하려 했을 때는 애플리케이션이 완전히 기동되기 전에 요청이 먼저 도착해 빈 응답을 받았다. 즉시 헬스체크를 확인하려면 `docker compose up -d backend`와 `curl` 사이에 헬스체크가 `healthy`로 바뀔 때까지 기다리는 짧은 재시도 루프를 두는 게 낫다는 걸 확인한 부분이다.)

**3) 롤포워드(원상 복구)**

```
$ docker compose pull backend
[+] pull 1/1
 ✔ Image ghcr.io/danielsunwoo/moneylog-backend:latest  Pulled

$ docker compose up -d backend
[+] up 2/2
 ✔ Container moneylog-mysql    Healthy
 ✔ Container moneylog-backend  Started

$ docker inspect --format='복구 후 이미지: {{.Config.Image}}' moneylog-backend
복구 후 이미지: ghcr.io/danielsunwoo/moneylog-backend:latest
```

`IMAGE_TAG`를 지정하지 않으면 compose 파일의 기본값인 `:latest`로 정확히 복귀됨을 확인했다.

## 결론

- sha 태그를 지정해 이전 버전으로 되돌리는 것과, 다시 최신 버전으로 돌아오는 것 모두 실제 명령과 실제 로그로 검증했다.
- 롤백은 `.env`를 건드리지 않는 일회성 명령이므로, 다음 자동 배포 사이클에 영향을 주지 않는다.
- 다음에 실제 장애 상황에서 롤백이 필요하면, 이 문서의 1)번 명령 두 줄을 그대로 SSM Session Manager 또는 SSM SendCommand로 실행하면 된다. frontend를 롤백할 때는 `backend`를 `frontend`로, 이미지 이름을 `moneylog-frontend`로 바꾸면 동일하게 적용된다.
