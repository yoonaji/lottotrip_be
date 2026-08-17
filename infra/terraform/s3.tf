data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "dev" {
  bucket = "${var.project}-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "dev" {
  bucket = aws_s3_bucket.dev.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}
