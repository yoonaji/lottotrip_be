# --- GitHub Actions OIDC 배포 ---
# GitHub Actions가 AWS 액세스 키를 GitHub Secrets에 저장하지 않고도 AWS를 호출할 수 있게
# OIDC로 신뢰 관계를 맺는다. 매 push마다 단명 토큰을 발급받아 쓰고, 저장된 장기 키가 없으니
# 리포가 털려도 새어나갈 AWS 자격증명 자체가 없다.
data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]
}

locals {
  github_owner     = split("/", var.github_repo)[0]
  github_repo_name = split("/", var.github_repo)[1]
}

# 이 역할은 SSM으로 EC2에 배포 커맨드를 보내는 것 말고는 아무 권한도 없다.
# 특정 리포의 특정 브랜치(main)에서 온 토큰만 assume 가능하도록 sub 클레임으로 제한한다.
data "aws_iam_policy_document" "github_actions_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # GitHub이 sub 클레임에 소유자/리포 "이름" 바로 뒤에 불변 숫자 ID를 붙이는 새 포맷을 쓴다
    # (예: "repo:yoonaji@168801970/lottotrip_be@1306545378:ref:refs/heads/main" — @168801970은
    # yoonaji 뒤에, @1306545378은 lottotrip_be 뒤에 붙는다). 예전처럼
    # "repo:${var.github_repo}:ref:..."로 이름만 매칭하면 실제 토큰과 안 맞아서
    # AssumeRoleWithWebIdentity가 "Not authorized"로 거부된다 — 실제로 겪은 문제.
    # ID는 계정/리포가 유지되는 한 안 바뀌므로(이름 변경·이관에도 안전) 이름 각각 뒤에
    # 와일드카드를 둬서 "이름(@ID)?" 둘 다 받아들이게 한다.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${local.github_owner}*/${local.github_repo_name}*:ref:refs/heads/${var.github_deploy_branch}"]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name               = "${var.project}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_trust.json
}

data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid       = "SendDeployCommand"
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = [aws_instance.app.arn]
  }

  statement {
    sid       = "SendDeployCommandDocument"
    effect    = "Allow"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript"]
  }

  statement {
    sid    = "ReadDeployResult"
    effect = "Allow"
    # GetCommandInvocation은 리소스 레벨 권한(특정 command-id 단위 제한)을 지원하지 않는다.
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "github_actions_deploy" {
  name   = "${var.project}-github-deploy-policy"
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}

resource "aws_iam_role_policy_attachment" "github_actions_deploy" {
  role       = aws_iam_role.github_actions_deploy.name
  policy_arn = aws_iam_policy.github_actions_deploy.arn
}

# --- EC2가 SSM 커맨드를 받게 하는 쪽 ---
# EC2 인스턴스 자체 역할(iam.tf의 app_ec2)에 AWS 관리형 SSM 정책을 붙인다.
# AL2023은 SSM 에이전트가 기본 설치돼 있어서 이 정책만 붙이면 별도 부트스트랩이 필요 없다.
resource "aws_iam_role_policy_attachment" "app_ec2_ssm_core" {
  role       = aws_iam_role.app_ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
