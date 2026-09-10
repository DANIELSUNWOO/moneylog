# Terraform 도입: 손으로 만든 AWS 인프라를 코드로 가져오기

## 배경

머니로그의 AWS 인프라(EC2, 보안그룹, IAM 역할, S3, CloudWatch, GitHub OIDC)는 지금까지 전부 AWS 콘솔 클릭과 CLI 명령으로 하나씩 만들어왔다. 동작은 하지만 세 가지 문제가 있다.

1. **재현 불가능**: 지금 이 EC2 인스턴스가 통째로 사라지면, "어떤 설정으로 다시 만들어야 하는지"가 내 기억과 스크린샷에만 남아있다.
2. **드리프트 추적 불가**: 누군가 콘솔에서 보안그룹 규칙을 하나 슬쩍 바꿔도 아무 기록이 남지 않는다. 지금 상태가 "의도한 상태"인지 확인할 방법이 없다.
3. **문서와 실제의 불일치 위험**: `docs/` 아래 아무리 잘 정리해도, 실제 AWS 상태와 문서가 자연스럽게 벌어진다(사람이 손으로 갱신해야 하니까).

Terraform은 이 세 가지를 "인프라를 코드로 선언하고, 코드와 실제 상태의 차이를 기계가 계산해서 보여주는" 방식으로 해결하는 도구다(Infrastructure as Code, IaC). AWS·GCP·Azure를 가리지 않고 가장 널리 쓰이는 도구이기도 하다.

## 설계 결정

### D-1. destroy 후 재생성이 아니라 `terraform import`

Terraform을 처음부터 쓴다면 `terraform apply`로 인프라를 새로 만들면 그만이다. 하지만 지금 EC2는 실제로 서비스 중이고, RDS 없이 EC2 안에 MySQL 데이터가 살아있다. 지우고 새로 만드는 방식은 다운타임과 데이터 유실 위험을 감수해야 한다.

`terraform import`는 이미 존재하는 AWS 리소스의 ID를 Terraform 상태 파일에 등록만 하는 명령이다. AWS 쪽에는 아무 변화도 주지 않는다 — 즉 무중단으로 "관리 주체만" 콘솔에서 코드로 옮기는 방법이다. 대신 대가가 있다: import는 상태만 가져올 뿐 `.tf` 코드는 자동으로 만들어주지 않는다. 리소스의 실제 속성값을 하나하나 확인해서 코드를 직접 작성해야 한다.

### D-2. 사람 계정과 별도의 `terraform-cli` IAM 사용자

지난 세션에 GitHub Actions용 OIDC 역할을 사람 로그인 계정(`sunwoo-admin`)과 분리했던 것과 같은 이유다. Terraform이 쓰는 자격증명이 콘솔 로그인 비밀번호와 섞이면, 나중에 자동화(예: CI에서 `terraform plan`을 돌리는 것)로 넘어갈 때 사람 계정 자격증명을 CI에 심어야 하는 상황이 생긴다. 처음부터 `terraform-cli`라는 별도 IAM 사용자(AdministratorAccess, 콘솔 로그인 없음, CLI 전용 액세스 키)를 만들어 `aws configure`로 로컬에 등록했다.

### D-3. 리소스를 6개 그룹으로 나눠 순차적으로 import + 검증

15개 리소스를 한 번에 다 import하고 마지막에 한 번만 `terraform plan`을 보는 대신, 논리적 단위(EC2, 보안그룹, EC2 IAM 역할, S3, CloudWatch 로그그룹, GitHub OIDC)로 나눠서 "import → plan으로 diff 확인 → 필요하면 코드 수정 → 다시 plan → No changes 확인"을 그룹마다 반복했다. 한 번에 다 하면 diff가 나왔을 때 15개 리소스 중 어느 것 때문인지 찾는 데 시간이 더 걸린다.

### D-4. plan에서 diff가 나오면 무조건 코드를 실제 상태에 맞춘다 (반대 방향 금지)

가장 중요한 원칙. `terraform plan`이 diff를 보여줄 때 두 가지 선택지가 있다: (a) 코드를 실제 상태에 맞게 고치거나, (b) `terraform apply`로 실제 상태를 코드에 맞게 바꾸거나. 이번 작업에서는 항상 (a)만 선택했다. 이유는 지금 이 AWS 리소스들이 실제로 서비스 중인 인프라이기 때문이다 — import 직후의 `apply`는 "지금 막 상태를 등록한 리소스를 재해석해서 지우고 새로 만드는" 위험한 명령이 될 수 있다. import 단계의 목표는 **코드가 현실을 정확히 베끼는 것**이지, 현실을 코드에 맞춰 바꾸는 것이 아니다.

## 실행 결과: 6개 그룹 · 15개 리소스

| 그룹 | 리소스 | import ID 형식 |
|---|---|---|
| EC2 | `aws_instance.moneylog_server` | 인스턴스 ID (`i-0ee7...`) |
| 보안그룹 | `aws_security_group.moneylog_sg` | 보안그룹 ID (`sg-06ea...`) |
| EC2 IAM 역할 | `aws_iam_role`, `aws_iam_instance_profile`, `aws_iam_role_policy_attachment`(관리형 정책 1개), `aws_iam_role_policy`(인라인 정책 3개) | 역할명 / 역할명 / `역할명/정책ARN` / `역할명:정책명` |
| S3 백업 | `aws_s3_bucket`, `aws_s3_bucket_public_access_block`, `aws_s3_bucket_server_side_encryption_configuration` | 버킷명 (3개 리소스 모두 동일) |
| CloudWatch | `aws_cloudwatch_log_group.moneylog_containers` | 로그그룹 이름 |
| GitHub OIDC | `aws_iam_openid_connect_provider`, `aws_iam_role`, `aws_iam_role_policy`(인라인) | 공급자 ARN / 역할명 / `역할명:정책명` |

리소스 종류마다 import ID의 형식이 다르다는 게 이번에 새로 익힌 부분이다. 단일 문자열(대부분), `상위/하위` 형식(정책 연결), `상위:하위` 형식(인라인 정책)이 섞여 있어서 Terraform 공식 문서의 각 리소스 페이지에서 매번 확인해야 했다.

최종적으로 6개 그룹 모두 `terraform plan` → `No changes. Your infrastructure matches the configuration.` 를 확인했다.

## 트러블슈팅

### 1. 보안그룹 `description`이 두 단계에 걸쳐 diff를 만든 사례

가장 배울 게 많았던 케이스라 자세히 남긴다.

**1단계 — 애초에 plan이 에러로 실패함.** 보안그룹 규칙에 한글 설명("모든 아웃바운드 허용")을 넣은 코드를 작성했더니, `terraform plan` 단계에서부터 AWS API가 다음 에러를 뱉었다.

```
"egress.0.description" doesn't comply with restrictions
```

원인은 AWS 보안그룹 규칙의 `description` 필드가 정규식 `^[0-9A-Za-z_ .:/()#,@\[\]+=&;{}!$*-]*$`를 강제한다는 것 — 한글 같은 비ASCII 문자를 아예 받지 않는다. 영문(`"Allow all outbound"`)으로 바꿔서 이 에러 자체는 해결했다.

**2단계 — 에러는 없어졌지만 plan이 "규칙 삭제 후 재생성" diff를 보여줌.** 영문 설명으로도 `terraform plan`을 돌리면 여전히 diff가 있었다. 콘솔에서 손으로 만들었던 실제 규칙에는 애초에 `description`이 아예 없었기 때문이다. 즉 코드에 어떤 description 값을 넣든(영문이라도) "description 없음 → description 있음"이라는 실제 변경이 되어, Terraform은 이걸 규칙을 지우고 새로 만드는 작업으로 계획한다.

이 시점에서 D-4 원칙을 그대로 적용했다: 지금 목표는 규칙에 설명을 붙이는 게 아니라 코드가 현실을 정확히 반영하는 것이므로, description을 아예 만들지 않고(코드에서 통째로 제거) 실제 상태와 일치시켰다. 그 결과 `terraform plan`이 비로소 `No changes`를 보였다.

**배운 점**: "겉보기에 문제없어 보이는 값(영문 설명)"도 실제 인프라와 다르면 diff가 난다. plan 에러가 사라졌다고 끝난 게 아니라, `No changes`가 나올 때까지가 검증의 완료 지점이다.

### 2. `aws configure` 실행 중 자격증명 화면 노출 → 즉시 로테이션

`terraform-cli` 사용자의 액세스 키를 로컬에 등록하는 과정에서, `aws configure` 실행 결과를 캡처해 공유했는데 여기에 Access Key ID와 Secret Access Key 전체가 평문으로 찍혀 있었다. 채팅이나 스크린샷은 안전한 자격증명 저장소가 아니므로, 실제로 유출됐는지와 무관하게 "노출된 키는 무효화한다"는 원칙에 따라 즉시 처리했다: 기존 키 비활성화 → 삭제 → 새 키 발급 → (이번엔 비밀 값을 가리고) 재등록 → `aws sts get-caller-identity`로 새 키 동작 확인.

## 결론

콘솔/CLI로 손으로 만들었던 AWS 인프라 6개 그룹·15개 리소스가 전부 `terraform plan` 기준 `No changes`로 검증되어, 이제부터 이 인프라에 대한 모든 변경은 원칙적으로 `.tf` 파일 수정 → `terraform plan`으로 diff 확인 → `terraform apply` 순서를 거치게 된다. 콘솔에서 직접 클릭해서 바꾸는 경로는 앞으로는 "긴급 수정 후 반드시 코드에 반영"해야 하는 예외 상황으로 취급한다.

## 후속: state를 S3 백엔드로 옮기기

import까지 끝내고 나서야 드러난 구멍이 하나 있었다. **state 파일의 사본이 내 노트북에만 있었다는 것이다.**

`.gitignore`로 state를 저장소에서 빼둔 것 자체는 맞다 — state에는 리소스의 상세 설정이 그대로 담기고, 경우에 따라 민감한 값도 들어간다. 그런데 그 결과로 "코드가 관리하는 실제 리소스가 무엇인지"를 아는 유일한 파일이 이 PC 한 대에만 존재하게 됐다. 이 PC가 고장나면 Terraform은 멀쩡히 돌아가는 15개 리소스를 **"아직 만들어지지 않은 것"**으로 인식한다. IaC를 도입한 의미가 절반 사라지는 지점이다.

### 결정

**D-5. state 전용 버킷을 따로 만든다.** 이미 DB 백업용 S3 버킷이 있어서 거기에 경로만 나눠 넣을 수도 있었지만 그러지 않았다. 결정적인 이유는 **버전 관리가 버킷 단위 설정**이기 때문이다. state에는 버전 관리가 필수인데(잘못된 apply로 state가 오염됐을 때 되돌릴 유일한 수단), 백업 버킷에 이를 켜면 매일 쌓이는 백업 파일까지 전부 이전 버전이 영구히 남는다. 성격이 정반대인 두 데이터 — 계속 쌓이고 지워도 되는 백업과, 단 하나뿐이고 잃으면 안 되는 정본 — 를 한 버킷에 두면 어느 한쪽의 요구를 포기해야 한다.

**D-6. state 버킷은 Terraform으로 관리하지 않는다.** state를 담는 버킷을 그 state로 관리하면 순환이 생긴다. `terraform destroy`가 자기 state가 든 버킷을 지우려 드는 식이다. 그래서 이 버킷만은 의도적으로 코드 밖에 두고 AWS CLI로 만들었다(부트스트랩 리소스).

**D-7. 잠금은 DynamoDB 없이 S3로 한다.** 예전에는 상태 잠금에 DynamoDB 테이블이 필요했지만, Terraform 1.11부터 S3 객체만으로 잠금이 된다(`use_lockfile = true`). 리소스를 하나 덜 만들고 비용도 줄어든다. 대신 `required_version`을 `>= 1.11`로 올려, 낮은 버전에서 조용히 잠금 없이 도는 상황을 막았다.

### 검증

`terraform init -migrate-state`로 로컬 state를 S3로 복사한 뒤, **`terraform plan`이 다시 `No changes`를 내는지**로 확인했다. state가 온전히 옮겨졌다면 Terraform이 보는 세상은 이전과 똑같아야 하기 때문이다. 만약 여기서 "15개를 새로 만들겠다"는 계획이 나왔다면 state가 유실된 것이므로 apply해서는 안 된다. 마이그레이션 전에 로컬 state를 따로 복사해둔 것도 같은 이유다.

## 배운 점

- IaC를 처음부터 있는 상태(그린필드)에 적용하는 것과, 이미 돌아가는 인프라에 나중에 적용하는 것(브라운필드)은 완전히 다른 기술이다. 후자가 실무에서는 훨씬 흔하고, `terraform import`가 그 다리 역할을 한다.
- `import`는 상태만 가져오지 코드는 만들어주지 않는다 — 결국 각 리소스의 실제 설정값을 AWS CLI(`describe-*`, `get-*`)로 직접 조회해서 코드로 옮기는 과정이 필요했고, 이 과정 자체가 "지금 이 인프라가 정확히 어떤 설정으로 되어 있는지"를 처음으로 한 줄 한 줄 확인하는 계기가 됐다.
- state를 저장소에서 빼는 것(`.gitignore`)과 안전한 곳에 두는 것은 별개의 문제다. 전자만 하고 후자를 빠뜨리면 "아무 데도 백업이 없는 정본"이 된다. 커밋하지 않기로 한 파일일수록 어디에 둘지를 따로 정해야 한다.
- `terraform plan`은 단순한 "미리보기"가 아니라, 코드와 현실의 불일치를 찾아내는 검증 도구로 쓰일 수 있다. 이번 보안그룹 사례처럼, plan이 있어야만 "description을 넣었는데 왜 규칙이 재생성되지"라는 문제를 배포 전에 발견할 수 있었다.
