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
