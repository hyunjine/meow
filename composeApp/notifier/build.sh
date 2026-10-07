#!/usr/bin/env bash
# Meow Notifier.app 을 만든다: swiftc 로 컴파일 → 번들 구성 → ad-hoc 서명.
# 사용법: build.sh <출력 .app 경로> <icns 경로>
set -euo pipefail

OUT="$1"
ICNS="$2"
DIR="$(cd "$(dirname "$0")" && pwd)"

rm -rf "$OUT"
mkdir -p "$OUT/Contents/MacOS" "$OUT/Contents/Resources"

swiftc -O -target "$(uname -m)-apple-macos11.0" -o "$OUT/Contents/MacOS/MeowNotifier" "$DIR/main.swift"
cp "$DIR/Info.plist" "$OUT/Contents/Info.plist"
cp "$ICNS" "$OUT/Contents/Resources/meow.icns"

codesign --force --deep -s - "$OUT"
