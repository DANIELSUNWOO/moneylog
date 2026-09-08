# CloudWatch 구축 기록 — 로그 수집·지표 수집·알람

Step 6("Layer 1 운영 기본기")의 마지막 항목. 지금까지 트러블슈팅할 때마다 SSM으로 EC2에 들어가 `docker compose logs`를 직접 봐야 했고, 메모리가 부족해지는 상황은 아예 알 방법이 없었다. 이 문서는 그 두 가지 문제를 해결한 과정이다.

## 1. 컨테이너 로그를 CloudWatch로 수집

### 설계

로그를 CloudWatch로 보내는 방법은 두 가지다 — 별도 에이전트 프로세스가 로그 파일을 읽어 전송하는 방식, 또는 Docker에 내장된 `awslogs` 로깅 드라이버가 컨테이너의 stdout을 직접 전송하는 방식. 메모리 1GB짜리 인스턴스에서 컨테이너 3개가 이미 돌고 있는 상황이라, 프로세스를 하나 더 띄우지 않는 후자를 선택했다.

로그 그룹은 Docker가 자동으로 만들게 하지 않고 **미리 콘솔에서 생성**했다. 자동 생성을 허용하려면 EC2 역할에 `logs:CreateLogGroup` 권한까지 줘야 하는데, 이건 "아무 이름의 로그 그룹이나 만들 수 있다"는 뜻이라 권한이 넓어진다. 대신 로그 그룹 하나(`/moneylog/containers`)를 미리 만들고, EC2 역할에는 **그 로그 그룹 하나에 한해서만** `logs:CreateLogStream`, `logs:PutLogEvents`를 허용했다(IAM 정책을 특정 리소스 ARN으로 좁히는, 지금까지 계속 써온 원칙과 동일).

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ContainerLogsAccess",
      "Effect": "Allow",
      "Action": ["logs:CreateLogStream", "logs:PutLogEvents"],
      "Resource": "arn:aws:logs:ap-northeast-2:847263217053:log-group:/moneylog/containers:*"
    }
  ]
}
```

로그 그룹 보존 기간은 **14일**로 설정했다. 기본값인 "무기한 보관"으로 두면 시간이 지날수록 저장 용량과 비용이 계속 늘어나는데, 트러블슈팅용 로그는 2주면 충분하다.

### 적용

`docker-compose.yml`의 `mysql`/`backend`/`frontend` 세 서비스 각각에 로깅 드라이버를 추가했다.

```yaml
    logging:
      driver: awslogs
      options:
        awslogs-region: ap-northeast-2
        awslogs-group: /moneylog/containers
        awslogs-stream: backend   # 서비스별로 mysql / backend / frontend
```

CD 파이프라인이 이미지는 자동으로 배포해주지만 `docker-compose.yml` 자체는 EC2로 동기화하지 않는다는 걸 이전 CORS 트러블슈팅에서 이미 겪었기 때문에, 이번에도 로컬 저장소와 EC2의 `/home/ec2-user/moneylog/docker-compose.yml`을 각각 수정했다(EC2 쪽에는 로컬에 없는 443 포트 설정이 이미 있어서, 그 차이를 보존하며 로깅 설정만 추가). `logging` 설정은 컨테이너를 재생성해야 적용되므로 `docker compose up -d`로 세 컨테이너를 모두 재생성했다.

### 검증

CloudWatch Logs 콘솔에서 로그 그룹 `/moneylog/containers` 안에 `backend`/`frontend`/`mysql` 세 개의 로그 스트림이 생성됐고, 각각 실제 로그(Spring Boot 기동 로그, nginx `docker-entrypoint.sh` 로그, MySQL 초기화 로그)가 실시간으로 들어오는 걸 확인했다.

## 2. 메모리·디스크 지표 수집 (CloudWatch 에이전트)

### 설계

EC2는 CPU 사용률·네트워크·디스크 I/O는 기본으로 CloudWatch에 잡히지만, **메모리 사용률과 디스크 사용량(용량)은 기본 제공되지 않는다** — 별도 에이전트를 설치해야 나온다. 메모리 1GB 인스턴스에서 컨테이너 3개를 돌리는 지금 구성에서는 메모리가 특히 중요한 지표다.

에이전트가 지표를 보내려면 `cloudwatch:PutMetricData` 권한이 필요한데, 이 API는 리소스 ARN 단위로 좁힐 수 있는 구조가 아니다. 대신 **네임스페이스 조건**을 걸어 에이전트가 오직 `CWAgent`라는 네임스페이스에만 쓸 수 있도록 제한했다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "CWAgentMetrics",
      "Effect": "Allow",
      "Action": "cloudwatch:PutMetricData",
      "Resource": "*",
      "Condition": {
        "StringEquals": { "cloudwatch:namespace": "CWAgent" }
      }
    }
  ]
}
```

### 적용

```bash
sudo yum install -y amazon-cloudwatch-agent
```

수집할 지표를 메모리 사용률과 루트 디스크(`/`) 사용률, 딱 두 가지로 최소화한 설정 파일을 작성했다(불필요한 지표를 많이 보내면 그만큼 API 호출·저장 비용도 늘어난다).

```json
{
  "metrics": {
    "namespace": "CWAgent",
    "metrics_collected": {
      "mem": { "measurement": ["mem_used_percent"], "metrics_collection_interval": 60 },
      "disk": { "measurement": ["used_percent"], "metrics_collection_interval": 60, "resources": ["/"] }
    }
  }
}
```

```bash
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/config.json
systemctl enable amazon-cloudwatch-agent
```

`amazon-cloudwatch-agent-ctl -a status`로 `"status": "running"`을 확인했고, CloudWatch 콘솔의 `CWAgent` 네임스페이스에서 `mem_used_percent`, `disk_used_percent` 두 지표가 실제로 수집되는 것도 확인했다.

## 3. 메모리 사용률 알람

### 설계

지표를 눈으로 볼 수 있게 된 것과, 문제가 생겼을 때 사람이 먼저 알아채는 것 사이에는 차이가 있다. SNS 주제(`moneylog-alerts`)를 만들어 이메일을 구독시키고, 메모리 사용률이 임계값을 넘으면 그 주제로 알림이 가도록 CloudWatch 알람을 연결했다.

알람 조건은 **85% 초과 상태가 5분씩 2회(총 10분) 연속될 때**로 잡았다. 컨테이너 재시작 순간처럼 잠깐 튀는 값에도 매번 알림이 오면 알람 자체를 무시하게 되기 때문에, 일부러 지속 조건을 걸어 진짜 문제일 때만 울리게 설계했다.

- SNS 주제: `moneylog-alerts` (이메일 구독, 구독 확인 완료)
- 알람: `moneylog-high-memory-alarm` — `CWAgent > mem_used_percent > 85`, 5분 기간 × 2회 연속, 트리거 시 `moneylog-alerts`로 알림

## 결과

- 트러블슈팅 시 SSM 없이 CloudWatch Logs 콘솔에서 바로 컨테이너 로그를 확인할 수 있게 됨.
- 메모리·디스크 사용률을 실시간으로 확인할 수 있게 됨.
- 메모리 사용률이 85%를 10분 이상 지속해서 넘기면 이메일로 자동 알림.
- 비용 확인 겸 Cost Explorer도 함께 점검: 실제 청구액 $0.00(무료 티어 내), 이미 있던 "Zero-Spend Budget"(실제 비용 $0.01 초과 시 알림)이 계획했던 $5 알람보다 더 촘촘해 그대로 유지하기로 함.

이걸로 Step 6("Layer 1 운영 기본기") 전체가 완료됐다 — HTTPS, OIDC+SSM 배포+포트22 차단, 롤백 리허설, 백업·복구 리허설, CloudWatch(로그+지표+알람)까지 전부.

## 배운 점

로그·지표를 "수집만 하는 것"과 "이상 상황에서 사람에게 먼저 알려주는 것" 사이에는 실질적인 차이가 있다. 대시보드는 누군가 들여다봐야만 값어치가 있지만, 알람은 아무도 보고 있지 않아도 동작한다. 개인 프로젝트라 하더라도 운영 중인 서비스라면 "언제 봐도 확인 가능한 상태"가 아니라 "문제가 생기면 알아서 알려주는 상태"를 목표로 삼는 게 맞다고 느꼈다.

또한 IAM 권한을 설계할 때 리소스 ARN으로 좁힐 수 없는 API(`PutMetricData`)를 만났을 때, 대안으로 **조건(Condition)**을 활용해 네임스페이스 단위로라도 범위를 제한할 수 있다는 걸 이번에 익혔다 — 무조건 `Resource: "*"`로 열어주는 것과, 조건을 걸어 제한하는 것 사이에는 실질적인 보안 차이가 있다.
