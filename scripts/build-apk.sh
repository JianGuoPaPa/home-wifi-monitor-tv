#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android-tv"
BUILD_TYPE="${1:-release}"

case "$BUILD_TYPE" in
  debug|Debug)
    TASK="assembleDebug"
    OUTPUT="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
    ;;
  release|Release)
    TASK="assembleRelease"
    OUTPUT="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
    ;;
  *)
    echo "Usage: scripts/build-apk.sh [debug|release]" >&2
    exit 2
    ;;
esac

if [[ -x "$ANDROID_DIR/gradlew" ]]; then
  GRADLE_CMD=("$ANDROID_DIR/gradlew")
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=(gradle)
else
  echo "Gradle is not installed. Install Android Studio, or install Gradle and Android SDK command-line tools." >&2
  exit 1
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f "$ANDROID_DIR/local.properties" ]]; then
  echo "Android SDK not found." >&2
  echo "Set ANDROID_HOME/ANDROID_SDK_ROOT or create android-tv/local.properties with sdk.dir=/path/to/Android/sdk" >&2
  exit 1
fi

(cd "$ANDROID_DIR" && "${GRADLE_CMD[@]}" "$TASK")

mkdir -p "$ROOT_DIR/dist"
cp "$OUTPUT" "$ROOT_DIR/dist/home-wifi-monitor-tv-$BUILD_TYPE.apk"
echo "APK written to dist/home-wifi-monitor-tv-$BUILD_TYPE.apk"
