output "bucket_name" {
  value = aws_s3_bucket.dev.bucket
}

output "access_key_id" {
  value = aws_iam_access_key.app.id
}

output "secret_access_key" {
  value     = aws_iam_access_key.app.secret
  sensitive = true
}
