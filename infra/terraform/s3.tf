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
