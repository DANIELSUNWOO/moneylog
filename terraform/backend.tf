# Terraform state 를 로컬 디스크가 아니라 S3 에 둔다.
#
# 왜 옮기는가:
# state 는 "코드가 관리하는 실제 리소스가 무엇인지"를 담은 단 하나의 정본이다.
# 로컬에만 있으면 이 PC 가 고장나는 순간 코드와 실제 AWS 리소스의 연결이 끊기고,
# Terraform 은 멀쩡히 돌아가는 리소스들을 "새로 만들어야 할 것"으로 인식한다.
#
# 이 버킷은 의도적으로 Terraform 으로 관리하지 않는다(부트스트랩 리소스).
# state 를 담는 버킷을 그 state 로 관리하면 destroy 시 자기 자신을 지우는 순환이 생긴다.
terraform {
  backend "s3" {
    bucket = "moneylog-tfstate-847263217053"
    key    = "moneylog/terraform.tfstate"
    region = "ap-northeast-2"

    # 서버 측 암호화. S3 가 기본으로 암호화하지만 명시해 의도를 남긴다.
    encrypt = true

    # 상태 잠금. apply 중에 다른 실행이 끼어들어 state 가 깨지는 것을 막는다.
    # Terraform 1.11+ 는 S3 객체만으로 잠금이 되어 DynamoDB 테이블이 필요 없다.
    use_lockfile = true
  }
}
