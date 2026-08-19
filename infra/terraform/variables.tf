variable "aws_region" {
  description = "리소스를 생성할 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "로컬 ~/.aws/credentials 프로파일 이름"
  type        = string
  default     = "lottotrip"
}

variable "project" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "lottotrip-dev"
}

variable "ssh_public_key" {
  description = "EC2 SSH 접속용 공개키 (ssh-keygen으로 만든 .pub 파일 내용). terraform.tfvars에서 지정."
  type        = string
}

variable "admin_cidr" {
  description = "SSH(22번 포트) 접속을 허용할 IP 대역. 본인 IP/32로 좁혀서 쓸 것 (예: \"123.45.67.89/32\")."
  type        = string
}

variable "ec2_instance_type" {
  description = "앱 서버(API+웹소켓+렌더워커 전부) 인스턴스 타입"
  type        = string
  default     = "t4g.micro"
}

variable "rds_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "RDS 데이터베이스 이름"
  type        = string
  default     = "lottotrip"
}

variable "db_username" {
  description = "RDS 마스터 유저명"
  type        = string
  default     = "lottotrip"
}

variable "github_repo" {
  description = "GitHub Actions OIDC로 배포를 허용할 저장소 (org/repo 형태)"
  type        = string
  default     = "yoonaji/lottotrip_be"
}

variable "github_deploy_branch" {
  description = "이 브랜치로의 push만 배포를 트리거하도록 OIDC 조건에 건다"
  type        = string
  default     = "main"
}
