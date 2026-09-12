#!/bin/bash
# EC2 인스턴스 안에서 SSM RunCommand로 실행된다 (GitHub Actions → SSM SendCommand).
# 로컬에서 직접 돌릴 일은 없다.
set -euo pipefail

APP_DIR="/opt/lottotrip"
SSM_PATH="/lottotrip-dev"
AWS_REGION="ap-northeast-2"

# SSM RunCommand 셸에는 $HOME이 아예 안 잡혀 있어서 git config --global이 어디에
# 쓸지 못 찾고 죽는다. root로 실행되니 root의 홈으로 명시해 준다.
export HOME=/root

cd "$APP_DIR"

# SSM RunCommand는 root로 실행되는데 이 디렉터리는 ec2-user 소유라, git이
# "dubious ownership"으로 막는다. root의 git 설정에 예외로 등록해 둔다(멱등).
git config --global --get-all safe.directory 2>/dev/null | grep -qx "$APP_DIR" || \
  git config --global --add safe.directory "$APP_DIR"

echo "==> 최신 코드로 갱신"
git fetch origin main
git reset --hard origin/main

echo "==> SSM에서 시크릿 꺼내서 .env 생성"
aws ssm get-parameters-by-path \
  --path "$SSM_PATH" \
  --with-decryption \
  --region "$AWS_REGION" \
  --query "Parameters[*].[Name,Value]" \
  --output text \
  | while IFS=$'\t' read -r name value; do
      key="${name##*/}"
      printf '%s=%s\n' "$key" "$value"
    done > .env
chmod 600 .env

# Caddy가 인증서를 받을 호스트명. 없으면 Caddy가 빈 사이트 주소로 뜨다 죽고, 앱은 떴는데
# 밖에서 아무것도 안 붙는 상태가 된다. 헬스체크(8080 직결)는 그걸 잡아내지 못하므로 여기서 먼저 막는다.
if ! grep -q '^APP_DOMAIN=.\+' .env; then
  echo "FAIL: SSM에 ${SSM_PATH}/APP_DOMAIN 이 없습니다. infra/terraform/ssm.tf 참고."
  exit 1
fi

echo "==> 이미지 빌드 및 재기동"
docker-compose -f docker-compose.prod.yml build app
# app과 caddy를 함께 올린다. caddy는 이미지를 그대로 쓰므로 빌드가 없다.
docker-compose -f docker-compose.prod.yml up -d

echo "==> 이전 이미지 정리"
docker image prune -f

echo "==> 헬스체크"
# 앱에 직접 묻는다(127.0.0.1:8080). 80으로 물으면 Caddy가 443으로 리다이렉트(308)해서 -f에 걸린다.
# TLS까지 실제로 열렸는지는 배포 뒤 밖에서 `curl -I https://$APP_DOMAIN/actuator/health`로 본다 —
# 첫 배포는 인증서 발급에 수십 초가 걸려 여기서 기다리지 않는다.
for i in $(seq 1 20); do
  if curl -sf http://127.0.0.1:8080/actuator/health/liveness > /dev/null 2>&1 || \
     curl -sf http://127.0.0.1:8080/actuator/health > /dev/null 2>&1; then
    echo "OK: 앱이 응답합니다"
    exit 0
  fi
  sleep 5
done

echo "FAIL: 헬스체크 타임아웃 — 로그 확인 필요 (docker-compose -f docker-compose.prod.yml logs app)"
exit 1
