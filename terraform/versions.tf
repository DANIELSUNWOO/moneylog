terraform {
  # backend "s3" 의 use_lockfile(잠금)이 1.11 부터 지원되므로 하한을 올린다.
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
