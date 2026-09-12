locals {
  ssm_prefix = "/${var.project}"
  db_url     = "jdbc:postgresql://${aws_db_instance.postgres.address}:${aws_db_instance.postgres.port}/${var.db_name}"
}

resource "random_password" "jwt_secret" {
  length  = 48
  special = false # JWT_SECRET은 그냥 긴 랜덤 문자열이면 충분 (jjwt가 base64 등으로 다시 다루는 값)
}

# application-prod.yml이 그대로 읽는 이름(DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET)에 맞춰 저장.
# EC2는 iam.tf의 app_config_read 정책으로 이 경로만 읽을 수 있다.
# 인스턴스 안에서: aws ssm get-parameters-by-path --path "/${var.project}" --with-decryption
resource "aws_ssm_parameter" "db_url" {
  name  = "${local.ssm_prefix}/DB_URL"
  type  = "String"
  value = local.db_url
}

resource "aws_ssm_parameter" "db_username" {
  name  = "${local.ssm_prefix}/DB_USERNAME"
  type  = "String"
  value = var.db_username
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${local.ssm_prefix}/DB_PASSWORD"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "${local.ssm_prefix}/JWT_SECRET"
  type  = "SecureString"
  value = random_password.jwt_secret.result
}

resource "aws_ssm_parameter" "s3_bucket" {
  name  = "${local.ssm_prefix}/AWS_S3_BUCKET"
  type  = "String"
  value = aws_s3_bucket.dev.bucket
}

# Caddy가 TLS 인증서를 받을 호스트명. 도메인이 없어서 sslip.io를 쓴다 — `3-37-104-92.sslip.io`처럼
# IP를 하이픈으로 이어 붙인 이름을 sslip.io의 DNS가 그 IP로 풀어 준다. 등록·비용 없음.
# Let's Encrypt가 순수 IP에는 인증서를 안 내주기 때문에 이런 이름이 필요하다.
# EIP에서 계산하므로 인스턴스를 다시 만들어도 값이 따라간다.
# 실제 도메인을 사면 이 값을 그 도메인으로 바꾸고(DNS A 레코드는 aws_eip.app.public_ip로) 재배포한다.
resource "aws_ssm_parameter" "app_domain" {
  name  = "${local.ssm_prefix}/APP_DOMAIN"
  type  = "String"
  value = "${replace(aws_eip.app.public_ip, ".", "-")}.sslip.io"
}

# 카카오/네이버/구글 키 — 지금은 아직 실제 값을 못 받아서 .env.example과 같은 더미값으로
# 자리만 만들어 둔다. 실제 값이 오면 아래 명령으로 덮어쓰면 되고, 다음 배포(scripts/deploy.sh)가
# 자동으로 최신 값을 .env에 반영한다.
#   aws ssm put-parameter --name "/${var.project}/KAKAO_CLIENT_ID" --value "실제값" \
#     --type SecureString --overwrite --region ${var.aws_region}
# lifecycle.ignore_changes로 이렇게 CLI로 직접 갱신한 값을 terraform apply가 다시
# 더미값으로 덮어쓰지 않게 막는다.
locals {
  app_secret_placeholders = {
    KAKAO_CLIENT_ID      = "dummy-kakao-client-id"
    KAKAO_CLIENT_SECRET  = "dummy-kakao-client-secret"
    NAVER_CLIENT_ID      = "dummy-naver-client-id"
    NAVER_CLIENT_SECRET  = "dummy-naver-client-secret"
    GOOGLE_CLIENT_ID     = "dummy-google-client-id"
    GOOGLE_CLIENT_SECRET = "dummy-google-client-secret"
  }
}

resource "aws_ssm_parameter" "app_secret" {
  for_each = local.app_secret_placeholders

  name  = "${local.ssm_prefix}/${each.key}"
  type  = "SecureString"
  value = each.value

  lifecycle {
    ignore_changes = [value]
  }
}

# GOOGLE_AUDIENCES/APPLE_AUDIENCES/TOUR_API_SERVICE_KEY/ANTHROPIC_API_KEY는 SSM이 빈 문자열을
# 허용하지 않아서 여기 안 만든다. 앱도 이 값들이 env var 자체가 없을 때 빈 문자열 기본값으로
# 동작하니(application.yml의 ${VAR:} 기본값), 굳이 빈 파라미터를 만들 필요가 없다.
# 실제 값이 생기면 그때 새로 만든다:
#   aws ssm put-parameter --name "/${var.project}/ANTHROPIC_API_KEY" --value "실제값" \
#     --type SecureString --region ${var.aws_region}
