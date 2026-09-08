resource "aws_security_group" "moneylog_sg" {
  name        = "moneylog-sg"
  description = "moneylog EC2"
  vpc_id      = "vpc-0a34f5e5a70b528e4"

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # 22번(SSH) 인바운드 규칙 없음 — OIDC+SSM 배포 전환 후 의도적으로 삭제한 상태.
  # 여기에 다시 추가하지 않는 것이 중요하다.

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
