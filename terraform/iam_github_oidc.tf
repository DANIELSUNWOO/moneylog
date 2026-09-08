# GitHub Actions가 AWS를 정적 액세스 키 없이 인증하기 위한 OIDC 신뢰 관계.
resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["ab9d0263244dd0326eb67015705a667e79cfe998"]
}

resource "aws_iam_role" "github_actions_moneylog_deploy" {
  name = "github-actions-moneylog-deploy"

  max_session_duration = 3600

  # sub 조건이 흔히 알려진 "repo:OWNER/REPO:ref:..." 형태가 아니라
  # "repo:OWNER@org_id/REPO@repo_id:*" 불변 ID 형태인 이유는
  # docs/troubleshooting-oidc-ssm-deploy.md 참고.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github_actions.arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "token.actions.githubusercontent.com:sub" = "repo:DANIELSUNWOO@126894604/moneylog@1354740098:*"
          }
        }
      }
    ]
  })
}

# CD 파이프라인이 SSM으로 EC2에 배포 명령을 보내기 위한 권한.
# SendCommand는 이 EC2 인스턴스 하나 + AWS-RunShellScript 문서로만 한정.
resource "aws_iam_role_policy" "ssm_deploy" {
  name = "moneylog-ssm-deploy-policy"
  role = aws_iam_role.github_actions_moneylog_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SendCommand"
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = [
          "arn:aws:ec2:ap-northeast-2:847263217053:instance/i-0ee7f6a4eade2fdbb",
          "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript",
        ]
      },
      {
        Sid      = "ReadCommandStatus"
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      }
    ]
  })
}
