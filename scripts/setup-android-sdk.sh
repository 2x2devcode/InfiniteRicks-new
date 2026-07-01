#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="$ROOT_DIR/local.properties"

resolve_sdk() {
  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    echo "${ANDROID_HOME}"
    return 0
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
    echo "${ANDROID_SDK_ROOT}"
    return 0
  fi
  if [[ -d "${HOME}/Android/Sdk" ]]; then
    echo "${HOME}/Android/Sdk"
    return 0
  fi
  return 1
}

if SDK="$(resolve_sdk)"; then
  printf 'sdk.dir=%s\n' "$SDK" > "$PROPS"
  echo "Criado $PROPS com sdk.dir=$SDK"
  echo "Agora execute: ./gradlew :rick-android:assembleRelease"
  exit 0
fi

cat <<'EOF'
Android SDK nao encontrado.

Opcao 1 — variavel de ambiente:
  export ANDROID_HOME=$HOME/Android/Sdk
  bash scripts/setup-android-sdk.sh

Opcao 2 — arquivo manual:
  cp local.properties.example local.properties
  # edite sdk.dir com o caminho do SDK

Instale o SDK (API 35) via Android Studio ou command-line tools:
  https://developer.android.com/studio#command-tools
EOF
exit 1
