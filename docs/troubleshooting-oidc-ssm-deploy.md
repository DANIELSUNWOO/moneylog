# 트러블슈팅: GitHub Actions OIDC + AWS SSM으로 SSH 없는 배포 전환

## 배경: 왜 이걸 했나

머니로그의 초기 CD 파이프라인은 `appleboy/ssh-action`으로 GitHub Actions에서 EC2에 직접 SSH 접속해 컨테이너를 재배포하는 방식이었다. 이 방식은 두 가지 구조적인 약점을 가진다.

첫째, EC2 보안 그룹에서 22번 포트를 인터넷 전체(`0.0.0.0/0`)에 열어둬야 GitHub Actions의 러너(매 실행마다 IP가 바뀐다)가 접속할 수 있다. 즉 배포 편의를 위해 서버의 공격 표면을 상시로 넓혀두는 셈이다.

둘째, SSH 개인키를 GitHub Secrets에 장기간 저장해야 한다. 이 키가 유출되면 만료 시점이 없는 채로 서버 전체에 대한 접근 권한이 넘어간다.

이를 해결하기 위해 다음 구조로 전환했다.

```
[변경 전] GitHub Actions --SSH(포트22, 고정 개인키)--> EC2

[변경 후] GitHub Actions --OIDC로 임시 자격 증명 발급--> AWS IAM Role
                                                      --> AWS SSM SendCommand --> EC2 (SSM Agent, 아웃바운드 전용)
```

OIDC(OpenID Connect) 페더레이션을 쓰면 GitHub Actions가 실행될 때마다 AWS가 그 워크플로 실행 하나에 대해서만 유효한 단기 자격 증명을 발급해준다. 저장해두는 비밀키 자체가 없어진다. 그리고 SSM(Systems Manager) Session Manager / Run Command는 EC2에 미리 설치된 SSM 에이전트가 AWS 쪽으로 아웃바운드 연결만 하는 방식이라, 인바운드 포트를 하나도 열지 않고도 서버에 명령을 보낼 수 있다. 이 전환이 끝나면 22번 포트를 완전히 닫을 수 있다.

여기서는 이 전환 과정에서 만난 두 개의 큰 이슈와, 그걸 어떻게 근본 원인까지 추적했는지를 기록한다.

## 이슈 1: `AssumeRoleWithWebIdentity`가 계속 거부됨

### 증상

IAM에 OIDC Identity Provider(`token.actions.githubusercontent.com`, audience `sts.amazonaws.com`)를 등록하고, GitHub Actions가 맡을 IAM Role(`github-actions-moneylog-deploy`)의 신뢰 정책(trust policy)을 아래처럼 설정했다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::847263217053:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:DANIELSUNWOO/moneylog:*"
        }
      }
    }
  ]
}
```

이건 AWS 공식 문서와 대부분의 블로그 포스트가 안내하는 "표준" 형식이다. 그런데 워크플로를 실행하면 `aws-actions/configure-aws-credentials` 스텝에서 매번 아래 에러가 났다.

```
Error: Could not assume role with OIDC: Not authorized to perform sts:AssumeRoleWithWebIdentity
```

### 시도했지만 원인이 아니었던 것들

문제를 좁혀나가면서 다음을 하나씩 배제했다.

- **`AWS_ROLE_ARN` 시크릿 오타 의심** — 시크릿을 다시 확인하고 재입력했지만 동일하게 실패. 나중에 워크플로에 ARN을 하드코딩해서 시크릿 자체를 경로에서 제거해도 똑같이 실패해, 시크릿 문제가 아님을 확정했다.
- **`sub` 조건이 너무 좁은가** — 브랜치 단위(`repo:OWNER/REPO:ref:refs/heads/BRANCH`)에서 저장소 단위 와일드카드(`repo:OWNER/REPO:*`)로 넓혀봤지만 동일하게 실패.
- **`sub` 조건을 완전히 열어서 격리 테스트** — `"*"`로 설정해 조건 자체를 없애려 했더니, AWS 콘솔이 저장 자체를 거부했다. 외부 OIDC 공급자에 대해 `sub`(또는 `job_workflow_ref`)를 전혀 제한하지 않는 신뢰 정책은 AWS가 안전장치로 막아둔 것이다. 이 지점에서 문제는 "조건의 범위"가 아니라 "조건이 비교하는 대상 값 자체가 처음부터 틀렸다"는 쪽으로 무게가 옮겨갔다.
- **OIDC Provider의 audience 등록 오류 의심** — IAM 콘솔에서 Provider 상세를 확인해 audience가 정확히 `sts.amazonaws.com`으로 등록돼 있음을 재확인. 문제 없음.

### 근본 원인: GitHub OIDC 토큰의 "불변 ID(immutable ID)" 형식

`aws-actions/configure-aws-credentials`는 내부적으로 발급받은 OIDC 토큰(JWT)을 그대로 AWS에 넘기기 때문에, GitHub Actions의 디버그 로그(`ACTIONS_STEP_DEBUG=true`)를 켜도 토큰 값 자체는 `::add-mask::***`로 마스킹되어 나오지 않는다. 그래서 신뢰 정책이 비교하는 실제 `sub` 값이 무엇인지 알아낼 방법이 없었다.

이를 우회하기 위해 워크플로에 진단 전용 스텝을 추가했다.

```yaml
- name: OIDC 토큰 디버그 출력
  uses: actions/github-script@v7
  with:
    script: |
      const token = await core.getIDToken('sts.amazonaws.com');
      const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString());
      console.log('sub:', payload.sub);
      console.log('aud:', payload.aud);
      console.log('iss:', payload.iss);
      console.log('repository:', payload.repository);
      console.log('ref:', payload.ref);
```

`core.getIDToken()`으로 토큰을 직접 요청한 뒤, JWT의 payload 부분(`.` 기준 두 번째 조각)을 base64 디코딩해서 각 클레임을 개별적으로 `console.log`로 출력했다. 개별 클레임 값은 GitHub의 자동 마스킹 대상이 아니라서 로그에 그대로 찍혔다. 결과는 다음과 같았다.

```
sub: repo:DANIELSUNWOO@126894604/moneylog@1354740098:ref:refs/heads/feature/F-16-oidc-ssm-deploy
aud: sts.amazonaws.com
iss: https://token.actions.githubusercontent.com
repository: DANIELSUNWOO/moneylog
ref: refs/heads/feature/F-16-oidc-ssm-deploy
```

`sub` 클레임이 문서에 나온 일반형 `repo:OWNER/REPO:ref:...`가 아니라, 계정명과 저장소명 뒤에 각각 숫자로 된 내부 DB ID가 `@`로 붙는 형식(`DANIELSUNWOO@126894604`, `moneylog@1354740098`)이었다. 이 숫자는 계정명이나 저장소명을 바꿔도 변하지 않는 GitHub 내부의 불변 식별자다. 지금까지 시도한 모든 신뢰 정책 조건은 전부 `DANIELSUNWOO/moneylog`라는 "이름" 기준으로 작성되어 있었기 때문에, 문자열이 정확히 일치할 수가 없어 계속 거부됐던 것이다.

### 해결

신뢰 정책의 `sub` 조건을 실제 토큰 값 형식에 맞춰 수정했다.

```json
"StringLike": {
  "token.actions.githubusercontent.com:sub": "repo:DANIELSUNWOO@126894604/moneylog@1354740098:*"
}
```

이후 `AWS 자격 증명 설정 (OIDC)` 스텝이 즉시 성공했고, 로그에는 아래처럼 정상적으로 역할을 위임받은 내역이 찍혔다.

```
Authenticated as assumedRoleId AROA4KRGSWWOUS7ZVDGGR:GitHubActions
Set output authenticated-arn = arn:aws:sts::847263217053:assumed-role/github-actions-moneylog-deploy/GitHubActions
```

## 이슈 2: OIDC는 되는데 SSM 명령 자체가 실패함

### 증상

OIDC 인증은 통과했지만, 그다음 SSM으로 실제 배포 명령(`docker compose pull backend && docker compose up -d backend`)을 보내는 스텝에서 새로운 에러가 났다.

```
aws: [ERROR]: Waiter CommandExecuted failed: Waiter encountered a terminal
failure state: For expression "Status" we matched expected path: "Failed"
```

문제는 워크플로 스크립트가 `aws ssm wait command-executed`가 실패하면 그 자리에서 바로 멈추도록 되어 있어서(GitHub Actions의 `run:` 스텝은 기본적으로 `set -e`가 걸린 셸에서 실행된다), EC2 내부에서 실제로 어떤 에러가 났는지(`get-command-invocation`의 `StandardErrorContent`)를 한 번도 출력해보지 못했다는 점이었다. "실패했다"는 사실만 알 뿐 "왜"를 모르는 상태였다.

이를 해결하기 위해 스크립트를 아래처럼 바꿨다: wait이 실패해도 `|| true`로 다음 줄로 넘어가게 하고, 그다음 무조건 `get-command-invocation`을 호출해 전체 내용을 출력한 뒤, 마지막에 상태를 다시 확인해 실패였다면 그제서야 명시적으로 `exit 1`을 내도록 했다.

```yaml
aws ssm wait command-executed \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID" || true

aws ssm get-command-invocation \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID"

STATUS=$(aws ssm get-command-invocation \
  --command-id "$COMMAND_ID" \
  --instance-id "$INSTANCE_ID" \
  --query "Status" --output text)

if [ "$STATUS" != "Success" ]; then
  echo "배포 실패: 위 StandardErrorContent를 확인하세요."
  exit 1
fi
```

이렇게 하니 실제 에러 메시지가 드러났다.

```json
"StandardErrorContent": "docker: 'compose' is not a docker command.\nSee 'docker --help'"
```

### 근본 원인: docker compose 플러그인이 특정 사용자에게만 설치되어 있었음

`docker compose`(v2, 공백 방식)는 `docker` CLI에 내장된 기능이 아니라 별도의 실행 파일을 CLI 플러그인으로 찾아서 실행해주는 구조다. 이 플러그인 파일은 보통 두 곳 중 하나에 설치된다: 특정 사용자의 홈 디렉토리(`~/.docker/cli-plugins/docker-compose`) 또는 시스템 전체가 공유하는 위치(`/usr/libexec/docker/cli-plugins/`).

이 EC2 인스턴스에서는 이전에 `docker-compose` 플러그인을 `ec2-user`의 홈 디렉토리에만 설치해뒀다. 기존 SSH 기반 배포는 `ec2-user`로 로그인해서 명령을 실행했기 때문에 문제없이 동작했다.

그런데 AWS SSM의 `AWS-RunShellScript` 문서는 명령을 **root 사용자**로 실행한다. root는 `ec2-user`의 홈 디렉토리를 전혀 알지 못하므로, root 입장에서는 `compose`라는 서브커맨드 자체가 존재하지 않는 것으로 보였던 것이다. SSM 세션에 직접 접속해 확인한 결과는 다음과 같았다.

```
$ ls -la /home/ec2-user/.docker/cli-plugins/
-rwxr-xr-x. 1 ec2-user ec2-user 32333754 ... docker-compose

$ ls -la /usr/libexec/docker/cli-plugins/
-rwxr-xr-x. 1 root root 63487176 ... docker-buildx      # compose는 없음

$ ls -la /root/.docker/cli-plugins/
ls: cannot access '/root/.docker/cli-plugins/': No such file or directory
```

### 해결

`ec2-user` 전용 위치에 있던 실행 파일을 시스템 전체가 공유하는 위치로 복사했다. 이렇게 하면 root든 ec2-user든, 혹은 나중에 추가되는 어떤 사용자든 동일하게 `docker compose`를 찾을 수 있다.

```bash
cp /home/ec2-user/.docker/cli-plugins/docker-compose /usr/libexec/docker/cli-plugins/docker-compose
chmod +x /usr/libexec/docker/cli-plugins/docker-compose
```

적용 후 `docker compose version`이 정상 출력됐고, SSM을 통한 배포도 곧바로 성공했다.

## 최종 결과

- GitHub Actions → (OIDC 단기 자격 증명) → IAM Role → SSM SendCommand → EC2 순서로 배포 파이프라인 전환 완료.
- EC2 보안 그룹에서 22번 포트 인바운드 규칙을 완전히 삭제. 이제 이 서버로 들어가는 경로는 AWS 콘솔의 SSM Session Manager(IAM 권한 필요)뿐이다.
- GitHub Secrets에 장기 SSH 키를 저장할 필요가 없어졌다.

## 배운 점

공식 문서나 널리 퍼진 예제가 안내하는 "표준" 형식이 항상 실제 환경과 일치하지는 않는다는 걸 이번에 직접 겪었다. GitHub의 OIDC `sub` 클레임처럼, 문서화된 일반형과 실제 발급되는 값이 다를 가능성이 있는 부분은 추측이나 문서 재확인으로 시간을 쓰기보다, 애초에 실제 값을 직접 찍어서 확인하는 쪽이 훨씬 빨랐다. 이 문제는 시크릿을 하드코딩해서 배제하고, 신뢰 정책을 여러 형태로 바꿔가며 시도하는 데 상당한 시간을 썼지만, 결국 해결한 것은 "추측을 멈추고 실제 토큰 값을 눈으로 확인하는" 진단 스텝 하나였다.

또한 AWS 서비스가 "누구 권한으로" 명령을 실행하는지는 절대 당연하게 넘겨짚으면 안 된다는 것도 확인했다. SSH는 로그인한 사용자 권한으로, SSM Run Command는 기본적으로 root 권한으로 실행된다는 차이가 있었고, 이 차이가 사용자별로 설치된 소프트웨어(docker compose 플러그인)를 찾지 못하는 문제로 이어졌다. 배포 자동화를 만들 때는 "이 스크립트가 실제로 어떤 사용자, 어떤 환경 변수, 어떤 PATH로 실행되는가"를 항상 구체적으로 확인해야 한다는 걸 배웠다.
