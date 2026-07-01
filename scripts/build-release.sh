#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f keystore.properties ]]; then
  echo "未找到 keystore.properties，正在根据示例生成签名配置..."
  if [[ ! -f signing/release-keystore.jks ]]; then
    mkdir -p signing
    keytool -genkey -v \
      -keystore signing/release-keystore.jks \
      -alias gpssimulate \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000 \
      -storepass gpssimulate \
      -keypass gpssimulate \
      -dname "CN=GPSSimulate, OU=Mobile, O=GPSSimulate, L=Suzhou, ST=Jiangsu, C=CN"
  fi
  cat > keystore.properties <<'EOF'
storeFile=signing/release-keystore.jks
storePassword=gpssimulate
keyAlias=gpssimulate
keyPassword=gpssimulate
EOF
  echo "已生成 signing/release-keystore.jks 与 keystore.properties"
fi

./gradlew assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [[ -f "$APK_PATH" ]]; then
  echo ""
  echo "打包完成: $ROOT_DIR/$APK_PATH"
  ls -lh "$APK_PATH"
else
  echo "未找到 release APK，请检查构建日志。" >&2
  exit 1
fi
