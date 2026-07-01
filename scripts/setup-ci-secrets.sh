#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

REPO="${1:-ShiShanLing/GPSSimulate}"
KEYSTORE_FILE="signing/release-keystore.jks"
PROPS_FILE="keystore.properties"

if [[ ! -f "$KEYSTORE_FILE" ]]; then
  echo "未找到 ${KEYSTORE_FILE}，请先运行 ./scripts/build-release.sh 生成本地签名文件。" >&2
  exit 1
fi

if [[ ! -f "$PROPS_FILE" ]]; then
  echo "未找到 ${PROPS_FILE}。" >&2
  exit 1
fi

store_password=$(grep '^storePassword=' "$PROPS_FILE" | cut -d= -f2-)
key_alias=$(grep '^keyAlias=' "$PROPS_FILE" | cut -d= -f2-)
key_password=$(grep '^keyPassword=' "$PROPS_FILE" | cut -d= -f2-)

if [[ -z "$store_password" || -z "$key_alias" || -z "$key_password" ]]; then
  echo "keystore.properties 格式不正确。" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "需要安装 GitHub CLI (gh)。" >&2
  exit 1
fi

echo "正在为 ${REPO} 配置 GitHub Actions 签名密钥..."

base64 < "$KEYSTORE_FILE" | gh secret set KEYSTORE_BASE64 --repo "$REPO"
gh secret set KEYSTORE_PASSWORD --body "$store_password" --repo "$REPO"
gh secret set KEY_ALIAS --body "$key_alias" --repo "$REPO"
gh secret set KEY_PASSWORD --body "$key_password" --repo "$REPO"

echo ""
echo "已配置以下 Secrets："
echo "  - KEYSTORE_BASE64"
echo "  - KEYSTORE_PASSWORD"
echo "  - KEY_ALIAS"
echo "  - KEY_PASSWORD"
echo ""
echo "推送 v* 标签后，GitHub Actions 将自动构建并发布 Release。"
