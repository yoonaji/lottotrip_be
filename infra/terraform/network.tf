# 기본 VPC를 그대로 쓴다. EC2·RDS 둘 다 (RDS는 publicly_accessible = false로) 같은
# 퍼블릭 서브넷에 두고 보안그룹으로만 접근을 제어하는 구조라 NAT Gateway가 필요 없고,
# 그러면 별도 VPC/서브넷/라우팅을 새로 구성할 이유도 없다. (NAT는 이 규모에서 제일 잘 새는 비용)
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "aws_security_group" "app" {
  # 앱 서버(EC2) - SSH/HTTP/HTTPS만 허용
  # AWS 보안그룹 description은 영문/숫자/일부 특수문자만 허용해서 한글은 여기 주석으로 뺀다.
  name        = "${var.project}-app"
  description = "App server (EC2): SSH/HTTP/HTTPS only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr]
  }

  # HTTP: Let's Encrypt 인증서 발급용, 나머지 트래픽은 앱(Caddy)이 443으로 리다이렉트
  ingress {
    description = "HTTP (ACME challenge, redirected to 443)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project}-app"
  }
}

resource "aws_security_group" "db" {
  # RDS - 앱 서버 보안그룹에서 오는 5432만 허용
  name        = "${var.project}-db"
  description = "RDS: 5432 from app security group only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Postgres from app server"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project}-db"
  }
}
