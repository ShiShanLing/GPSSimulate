#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  echo "用法: $0 <版本号> [--push]"
  echo ""
  echo "示例:"
  echo "  $0 1.0.0          # 本地提交并打标签"
  echo "  $0 1.0.1 --push   # 提交、打标签并推送到 GitHub（自动触发 Release）"
  exit 1
}

VERSION="${1:-}"
PUSH=false
if [[ "${2:-}" == "--push" ]]; then
  PUSH=true
elif [[ -n "${2:-}" ]]; then
  usage
fi

[[ -z "$VERSION" ]] && usage

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
  echo "版本号格式应为 x.y.z，例如 1.0.1" >&2
  exit 1
fi

TAG="v${VERSION}"
BUILD_FILE="app/build.gradle.kts"

CURRENT_NAME=$(grep 'versionName' "$BUILD_FILE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
CURRENT_CODE=$(grep 'versionCode' "$BUILD_FILE" | head -1 | grep -oE '[0-9]+')

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "标签 ${TAG} 已存在。" >&2
  exit 1
fi

if [[ "$VERSION" != "$CURRENT_NAME" ]]; then
  NEW_CODE=$((CURRENT_CODE + 1))
  sed -i.bak "s/versionCode = ${CURRENT_CODE}/versionCode = ${NEW_CODE}/" "$BUILD_FILE"
  sed -i.bak "s/versionName = \"${CURRENT_NAME}\"/versionName = \"${VERSION}\"/" "$BUILD_FILE"
  rm -f "${BUILD_FILE}.bak"
  echo "已更新版本: versionCode=${NEW_CODE}, versionName=${VERSION}"
else
  echo "版本号未变 (${VERSION})，保持 versionCode=${CURRENT_CODE}"
fi

echo "正在本地打包..."
./scripts/build-release.sh

if [[ -n "$(git status --porcelain)" ]]; then
  git add "$BUILD_FILE"
  git commit -m "release: ${TAG}"
fi

git tag -a "$TAG" -m "Release ${VERSION}"

echo ""
echo "已创建标签 ${TAG}"

if [[ "$PUSH" == true ]]; then
  echo "正在推送到 GitHub..."
  git push origin HEAD
  git push origin "$TAG"
  echo ""
  echo "已推送。GitHub Actions 将自动构建 APK 并发布到 Releases："
  echo "https://github.com/ShiShanLing/GPSSimulate/releases"
else
  echo ""
  echo "下一步，推送到 GitHub 以触发自动发布："
  echo "  git push && git push origin ${TAG}"
  echo ""
  echo "或直接："
  echo "  $0 ${VERSION} --push"
fi
