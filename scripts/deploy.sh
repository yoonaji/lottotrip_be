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

echo "==> 이미지 빌드 및 재기동"
docker-compose -f docker-compose.prod.yml build app
docker-compose -f docker-compose.prod.yml up -d app

echo "==> 이전 이미지 정리"
docker image prune -f

echo "==> 헬스체크"
for i in $(seq 1 20); do
  if curl -sf http://localhost/actuator/health/liveness > /dev/null 2>&1 || \
     curl -sf http://localhost/actuator/health > /dev/null 2>&1; then
    echo "OK: 앱이 응답합니다"
    exit 0
  fi
  sleep 5
done

echo "FAIL: 헬스체크 타임아웃 — 로그 확인 필요 (docker-compose -f docker-compose.prod.yml logs app)"
exit 1
