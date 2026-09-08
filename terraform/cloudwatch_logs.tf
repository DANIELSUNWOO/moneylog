resource "aws_cloudwatch_log_group" "moneylog_containers" {
  name              = "/moneylog/containers"
  retention_in_days = 14
}
