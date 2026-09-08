resource "aws_iam_role" "moneylog_ec2_ssm_role" {
  name        = "moneylog-ec2-ssm-role"
  description = "Allows EC2 instances to call AWS services on your behalf."

  max_session_duration = 3600

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "moneylog_ec2_ssm_role" {
  name = "moneylog-ec2-ssm-role"
  role = aws_iam_role.moneylog_ec2_ssm_role.name
}

# AWS 관리형 정책 — Session Manager로 EC2에 접속하기 위한 필수 권한.
resource "aws_iam_role_policy_attachment" "ssm_managed_instance_core" {
  role       = aws_iam_role.moneylog_ec2_ssm_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# 인라인 정책 1 — DB 백업 스크립트가 S3 버킷에 쓰기 위한 권한 (버킷 하나로 한정).
resource "aws_iam_role_policy" "backup_s3" {
  name = "moneylog-backup-s3-policy"
  role = aws_iam_role.moneylog_ec2_ssm_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "BackupBucketAccess"
        Effect = "Allow"
        Action = ["s3:PutObject", "s3:GetObject", "s3:ListBucket"]
        Resource = [
          "arn:aws:s3:::moneylog-db-backup-847263217053",
          "arn:aws:s3:::moneylog-db-backup-847263217053/*",
        ]
      }
    ]
  })
}

# 인라인 정책 2 — 컨테이너 로그를 CloudWatch Logs로 보내기 위한 권한 (로그 그룹 하나로 한정).
resource "aws_iam_role_policy" "container_logs" {
  name = "moneylog-container-logs-policy"
  role = aws_iam_role.moneylog_ec2_ssm_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ContainerLogsAccess"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:ap-northeast-2:847263217053:log-group:/moneylog/containers:*"
      }
    ]
  })
}

# 인라인 정책 3 — CloudWatch 에이전트가 메모리·디스크 지표를 보내기 위한 권한
# (리소스 ARN이 아니라 네임스페이스 조건으로 범위를 제한).
resource "aws_iam_role_policy" "cwagent_metrics" {
  name = "moneylog-cwagent-metrics-policy"
  role = aws_iam_role.moneylog_ec2_ssm_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "CWAgentMetrics"
        Effect   = "Allow"
        Action   = "cloudwatch:PutMetricData"
        Resource = "*"
        Condition = {
          StringEquals = {
            "cloudwatch:namespace" = "CWAgent"
          }
        }
      }
    ]
  })
}
