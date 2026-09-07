# CI/CD 파이프라인 동작 조건과 설정값 저장 위치

머니로그의 GitHub Actions 워크플로가 "언제" 도는지, 그리고 배포에 필요한 값들이 "어디에" 저장되어 있는지를 정리한 참고 문서다.

## 1. 워크플로는 언제 실행되는가

`push`를 한다고 모든 워크플로가 도는 게 아니다. 각 워크플로는 아래 세 조건의 조합으로 실행 여부가 결정된다.

- **이벤트 종류**: `push`(커밋이 올라감), `pull_request`(PR이 열리거나 갱신됨), `workflow_dispatch`(Actions 탭에서 사람이 수동으로 실행)
- **브랜치 조건**: CD 두 워크플로는 `branches: [main]`이라 main에 push될 때만 반응한다. feature 브랜치에 push해도 자동으로는 안 돈다.
- **경로(paths) 조건**: 브랜치 조건을 만족해도, **바뀐 파일이 지정된 경로와 일치할 때만** 실행된다.

| 워크플로 | 트리거 | 경로 조건 |
|---|---|---|
| `cd-backend.yml` | push(main), workflow_dispatch | `backend/**` |
| `cd-frontend.yml` | push(main), workflow_dispatch | `frontend/**` |
| `ci-backend.yml` | pull_request(main), push(main) | `backend/**` (PR일 땐 워크플로 파일 자체 변경도 포함) |
| `ci-frontend.yml` | pull_request(main), push(main) | `frontend/**` (PR일 땐 워크플로 파일 자체 변경도 포함) |

`workflow_dispatch`는 브랜치/경로 조건을 아예 무시하고 지금 그 브랜치 상태 그대로 강제 실행하는 수동 트리거다. OIDC+SSM 작업처럼 `.github/workflows/*.yml`만 바꾸고 `backend/**`는 안 건드린 경우, `paths` 조건에 걸리지 않아 자동으로는 절대 안 돌기 때문에 `workflow_dispatch`로 계속 수동 실행하며 테스트했다.

## 2. 배포 흐름

`cd-backend.yml`(frontend도 동일 구조) 안에는 두 개의 job이 순서대로 실행된다.

1. **`build-and-push`**: 소스 코드로 Docker 이미지를 처음부터 새로 빌드하고, GHCR(GitHub Container Registry)에 `:latest`와 `:{커밋 sha}` 두 태그로 push한다.
2. **`deploy`**: OIDC로 AWS 자격 증명을 얻고, SSM으로 EC2에 "새 이미지를 pull해서 컨테이너를 다시 띄워라" 명령을 보낸다.

여기서 중요한 점: **파일을 patch하는 게 아니라 컨테이너를 통째로 교체한다.** EC2에 떠 있던 기존 컨테이너를 내리고, 새로 만든 이미지로 컨테이너를 처음부터 다시 생성한다. 실행 중인 컨테이너 내부 파일을 직접 고치는 개념 자체가 없다.

## 3. GHCR(GitHub Container Registry)이 하는 역할

GHCR은 Docker Hub와 같은 역할을 하는, GitHub 계정에 딸린 이미지 저장소다. GitHub Actions 러너와 EC2는 이미지를 직접 주고받지 않고, 항상 GHCR을 거친다.

```
GitHub Actions가 이미지 빌드 → ghcr.io/danielsunwoo/moneylog-backend:latest, :{sha} 로 push
                                              ↓
EC2가 docker-compose.yml의 image: 설정을 보고 GHCR에서 pull
```

sha 태그가 계속 쌓이기 때문에, 롤백 리허설 때처럼 특정 커밋 시점의 이미지로 되돌아갈 수 있다.

## 4. 값은 어디에 저장되어 있는가 — GitHub Secrets vs `.env`

같은 "환경변수"처럼 보여도, **누가 그 값을 실제로 읽는가**에 따라 저장 위치가 완전히 다르다.

| 값 | 저장 위치 | 누가 읽는가 | 용도 |
|---|---|---|---|
| `GITHUB_TOKEN` | 저장 안 함 — GitHub이 워크플로 실행마다 자동 발급하는 임시 토큰 | GHCR 로그인 스텝 | 이미지 push 인증 |
| `AWS_ROLE_ARN` | GitHub 저장소 Settings → Secrets and variables → Actions | AWS 자격 증명 설정(OIDC) 스텝 | SSM으로 EC2에 배포 명령을 보내기 위한 AWS 인증 |
| `EC2_HOST`, `EC2_SSH_KEY` | GitHub Secrets에 값은 남아있지만 **어떤 워크플로도 참조하지 않음**(SSH 배포 방식 때 쓰던 것, OIDC+SSM 전환 후 죽은 시크릿) | 없음 — 삭제 대상 | (과거) SSH 접속용 호스트 주소·개인키 |
| `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET`, `APP_CORS_ALLOWED_ORIGINS`, `IMAGE_TAG` 등 | EC2의 `/home/ec2-user/moneylog/.env` (로컬 PC의 `.env`는 별도 파일, 서로 자동 동기화 안 됨) | `docker compose up`이 읽어서 컨테이너 환경변수로 주입 → Spring Boot 앱이 사용 | DB 접속, JWT 서명, CORS 허용 목록 등 애플리케이션 런타임 설정 |
| `AWS_ROLE_ARN`이 실제로 무엇을 할 수 있는지(신뢰 정책, 권한) | AWS IAM 콘솔의 `github-actions-moneylog-deploy` 역할 | AWS 자체 | GitHub Secrets의 ARN 값은 "주소"일 뿐, 그 주소가 허용하는 권한의 실체는 IAM에 정의됨 |

한 줄로 정리하면: **GitHub 쪽 일(이미지 빌드·push·AWS 인증)에 필요한 값은 GitHub Secrets에, 서버에서 실제로 도는 애플리케이션에 필요한 값은 EC2의 `.env`에** — "누가 그 값을 쓰는가"를 기준으로 저장 위치가 나뉜다.

비유하자면 GitHub Secrets는 "택배 기사가 배송하기 위해 필요한 인증서(신분증, 배송 앱 로그인)"이고, `.env`는 "그 집(EC2) 안에서 실제로 쓰는 살림살이(비밀번호, 설정값)"다. 택배 기사는 물건만 문 앞에 두고 가지, 집 안의 살림살이는 알 필요도 관여할 필요도 없다.

## 5. "이 시크릿이 실제로 쓰이는가" 확인하는 방법

GitHub Settings의 Secrets 목록에 값이 있다고 해서 쓰이고 있다는 뜻은 아니다. 그 목록은 그냥 "등록해둔 값들의 창고"일 뿐이고, 실제 사용 여부는 워크플로 `.yml` 파일 안에 `${{ secrets.이름 }}` 형태로 그 이름을 불러오는 코드가 있는지로만 확인할 수 있다.

```bash
grep -rn "secrets\." .github/workflows/*.yml
```

이 명령으로 지금 실제 참조되는 시크릿(`GITHUB_TOKEN`, `AWS_ROLE_ARN`)과, 창고에만 남아있고 아무 데도 안 쓰이는 시크릿(`EC2_HOST`, `EC2_SSH_KEY`)을 구분할 수 있었다.
