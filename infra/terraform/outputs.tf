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

output "app_public_ip" {
  description = "EC2 고정 IP (Route53 A레코드 대상)"
  value       = aws_eip.app.public_ip
}

output "ssh_command" {
  value = "ssh ec2-user@${aws_eip.app.public_ip}"
}

output "db_endpoint" {
  description = "RDS 엔드포인트 (host:port)"
  value       = aws_db_instance.postgres.endpoint
}

output "db_url" {
  description = "application-prod.yml의 DB_URL 값"
  value       = local.db_url
}

output "ssm_config_path" {
  description = "서버에서 시크릿 꺼낼 때 쓸 경로: aws ssm get-parameters-by-path --path <값> --with-decryption"
  value       = local.ssm_prefix
}

output "instance_id" {
  description = "GitHub Actions 워크플로가 SSM SendCommand 대상으로 쓸 인스턴스 ID"
  value       = aws_instance.app.id
}

output "github_actions_role_arn" {
  description = "GitHub Actions 워크플로의 permissions.id-token/role-to-assume에 넣을 역할 ARN"
  value       = aws_iam_role.github_actions_deploy.arn
}
