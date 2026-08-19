# Amazon Linux 2023, ARM64 (t4g 계열용). SSM 퍼블릭 파라미터로 최신 AMI ID를 조회해서
# AMI ID를 코드에 하드코딩하지 않는다.
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_key_pair" "app" {
  key_name   = "${var.project}-key"
  public_key = var.ssh_public_key
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.ec2_instance_type
  subnet_id              = sort(data.aws_subnets.default.ids)[0]
  vpc_security_group_ids = [aws_security_group.app.id]
  key_name               = aws_key_pair.app.key_name
  iam_instance_profile   = aws_iam_instance_profile.app_ec2.name

  root_block_device {
    volume_type = "gp3"
    volume_size = 20 # 프리티어 한도(30GB) 안
  }

  # 기본 홉 리밋(1)은 Docker 브릿지 네트워크 안에서 IMDSv2를 못 넘는다.
  # 컨테이너 안의 앱이 인스턴스 role 자격증명(S3/SSM)을 받아오려면 2 이상이어야 한다.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  # 여기서는 Docker/Compose 설치까지만 한다. .env 구성(SSM에서 DB_URL·JWT_SECRET 등 꺼내기)과
  # `docker compose up`은 RDS가 뜬 뒤 별도로 진행한다 — user_data에 시크릿을 평문으로 심지 않기 위함.
  user_data = <<-EOF
    #!/bin/bash
    set -eux
    dnf update -y
    dnf install -y docker git
    systemctl enable --now docker
    usermod -aG docker ec2-user

    curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-aarch64" \
      -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose

    # docker-compose(standalone)가 쓰는 build 백엔드. AL2023에 딸려오는 버전이 너무 낮아서
    # 최신으로 교체해 둔다 (안 하면 "compose build requires buildx 0.17.0 or later"로 실패).
    mkdir -p /home/ec2-user/.docker/cli-plugins
    curl -fSL "https://github.com/docker/buildx/releases/download/v0.36.1/buildx-v0.36.1.linux-arm64" \
      -o /home/ec2-user/.docker/cli-plugins/docker-buildx
    chmod +x /home/ec2-user/.docker/cli-plugins/docker-buildx
    chown -R ec2-user:ec2-user /home/ec2-user/.docker

    # t4g.micro는 RAM 1GB라 Gradle 빌드 중 스왑 없이 OOM으로 인스턴스 전체가 응답 불능이
    # 될 수 있다 (실제로 한 번 겪음 — SSM/SSH 둘 다 무응답, stop/start로만 복구됨).
    if [ ! -f /swapfile ]; then
      fallocate -l 2G /swapfile
      chmod 600 /swapfile
      mkswap /swapfile
      swapon /swapfile
      echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
    fi
  EOF

  tags = {
    Name = "${var.project}-app"
  }
}

# 인스턴스를 재시작해도 IP가 안 바뀌게 고정한다. 켜져 있는 인스턴스에 붙어 있는 동안은
# 무료 — 인스턴스를 내려둔 채로 EIP만 들고 있을 때만 과금된다는 점 주의.
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = {
    Name = "${var.project}-app"
  }
}
