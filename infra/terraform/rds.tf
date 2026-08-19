# RDS는 매 apply마다 마이너 버전이 바뀌어 드리프트 나는 걸 피하려고,
# "16"으로 고정하는 대신 현재 시점의 최신 16.x를 조회해서 못박아 둔다.
data "aws_rds_engine_version" "postgres16" {
  engine  = "postgres"
  version = "16"
  latest  = true
}

resource "random_password" "db" {
  length  = 24
  special = true
  # RDS 마스터 비밀번호에 못 쓰는 문자(/, @, ", 공백)를 뺀 안전한 특수문자만 허용
  override_special = "!#$%^&*()-_=+"
}

resource "aws_db_subnet_group" "default" {
  name       = "${var.project}-db-subnets"
  subnet_ids = data.aws_subnets.default.ids

  tags = {
    Name = "${var.project}-db-subnets"
  }
}

resource "aws_db_instance" "postgres" {
  identifier     = "${var.project}-db"
  engine         = "postgres"
  engine_version = data.aws_rds_engine_version.postgres16.version
  instance_class = var.rds_instance_class

  allocated_storage = 20 # 프리티어 한도(20GB) 안
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.default.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 1    # 프리티어는 백업 보관 1일까지만 허용
  skip_final_snapshot     = true # dev 환경 — destroy 시 최종 스냅샷 없이 바로 삭제
  deletion_protection     = false

  tags = {
    Name = "${var.project}-db"
  }
}
