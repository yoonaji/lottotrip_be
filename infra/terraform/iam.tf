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
