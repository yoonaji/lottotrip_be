data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "dev" {
  bucket = "${var.project}-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "dev" {
  bucket = aws_s3_bucket.dev.id

  # ACL은 아예 안 쓰므로(전부 버킷 정책으로 제어) 이 둘은 계속 막아 둔다.
  block_public_acls  = true
  ignore_public_acls = true

  # renders/*(완성된 숏폼)만 공개 읽기를 허용하려면 "퍼블릭 버킷 정책" 자체를 막으면 안 된다.
  # clips/*(업로드 원본)는 정책에서 대상으로 넣지 않았으니 계속 비공개다.
  block_public_policy     = false
  restrict_public_buckets = false
}

# 완성된 숏폼(renders/*)만 공개 읽기 허용. 업로드 원본(clips/*)은 그대로 비공개.
# UUID 기반 키라 URL을 모르면 목록도 못 보고(ListBucket은 안 줌) 못 연다.
data "aws_iam_policy_document" "public_read_renders" {
  statement {
    sid       = "PublicReadRenders"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.dev.arn}/renders/*"]

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "public_read_renders" {
  bucket     = aws_s3_bucket.dev.id
  policy     = data.aws_iam_policy_document.public_read_renders.json
  depends_on = [aws_s3_bucket_public_access_block.dev]
}

resource "aws_s3_bucket_cors_configuration" "dev" {
  bucket = aws_s3_bucket.dev.id

  cors_rule {
    # 브라우저에서 presigned URL로 PUT 업로드 및 필요 시 GET/HEAD 허용
    allowed_methods = ["PUT", "POST", "GET", "HEAD"]

    # 요청받은 프론트엔드 URL과 로컬 개발용 URL 추가
    allowed_origins = ["http://localhost:3000", "https://lottotrip-web.vercel.app"]

    # 업로드 시 브라우저가 전송하는 모든 헤더 허용
    allowed_headers = ["*"]

    # 업로드 완료 후 프론트엔드 스크립트에서 ETag 헤더를 읽을 수 있도록 노출
    expose_headers  = ["ETag"]

    # 브라우저가 CORS preflight(OPTIONS) 결과를 캐싱할 시간(초)
    max_age_seconds = 3000
  }
}
