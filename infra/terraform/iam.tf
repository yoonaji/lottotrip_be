data "aws_iam_policy_document" "app" {
  statement {
    sid       = "S3ClipsAndRenders"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.dev.arn}/*"]
  }

  statement {
    sid       = "PollyNarration"
    effect    = "Allow"
    actions   = ["polly:SynthesizeSpeech"]
    resources = ["*"] # Polly SynthesizeSpeech는 리소스 레벨 권한을 지원하지 않음
  }
}

resource "aws_iam_policy" "app" {
  name   = "${var.project}-policy"
  policy = data.aws_iam_policy_document.app.json
}

resource "aws_iam_user" "app" {
  name = var.project
}

resource "aws_iam_user_policy_attachment" "app" {
  user       = aws_iam_user.app.name
  policy_arn = aws_iam_policy.app.arn
}

# 앱이 로컬/도커에서 쓸 액세스 키. 시크릿은 terraform.tfstate에 평문으로 남으므로
# state 파일은 반드시 .gitignore에 걸어두고 git에 올리지 않는다.
resource "aws_iam_access_key" "app" {
  user = aws_iam_user.app.name
}

# --- EC2 인스턴스 역할 ---
# 실서버는 위 IAM 유저의 장기 액세스 키를 쓰지 않는다. 인스턴스 프로파일이 EC2에
# 임시 자격증명을 자동 공급하므로, 서버 어디에도 키를 저장/유출할 필요가 없다.
resource "aws_iam_role" "app_ec2" {
  name = "${var.project}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "app_ec2" {
  role       = aws_iam_role.app_ec2.name
  policy_arn = aws_iam_policy.app.arn
}

resource "aws_iam_role_policy_attachment" "app_ec2_config_read" {
  role       = aws_iam_role.app_ec2.name
  policy_arn = aws_iam_policy.app_config_read.arn
}

resource "aws_iam_instance_profile" "app_ec2" {
  name = "${var.project}-ec2-profile"
  role = aws_iam_role.app_ec2.name
}

# 서버가 자기 경로(/${var.project}/*)의 SSM 파라미터(DB_URL, DB_PASSWORD, JWT_SECRET 등)만
# 읽을 수 있게 제한한다. 나머지 계정 파라미터는 이 정책으로 못 건드림.
data "aws_iam_policy_document" "app_config_read" {
  statement {
    sid     = "SSMParameterRead"
    effect  = "Allow"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    # GetParametersByPath는 경로 자체(와일드카드 없는 형태)에 대한 권한도 따로 확인한다.
    resources = [
      "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_prefix}",
      "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_prefix}/*",
    ]
  }

  statement {
    sid     = "SSMDecrypt"
    effect  = "Allow"
    actions = ["kms:Decrypt"]
    # SecureString 기본 키(alias/aws/ssm)는 AWS 관리형이라 계정마다 고정 ARN이 없어 "*"로 둔다.
    # 실질적인 범위는 위 SSMParameterRead가 이미 우리 파라미터 경로로 좁혀 놓는다.
    resources = ["*"]
  }
}

resource "aws_iam_policy" "app_config_read" {
  name   = "${var.project}-config-read-policy"
  policy = data.aws_iam_policy_document.app_config_read.json
}
