resource "aws_s3_bucket" "moneylog_db_backup" {
  bucket = "moneylog-db-backup-847263217053"
}

resource "aws_s3_bucket_public_access_block" "moneylog_db_backup" {
  bucket = aws_s3_bucket.moneylog_db_backup.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "moneylog_db_backup" {
  bucket = aws_s3_bucket.moneylog_db_backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}
