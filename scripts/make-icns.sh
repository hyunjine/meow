#!/usr/bin/env bash
# 1024px 앱 아이콘 PNG 로 macOS 패키지용 .icns 를 만든다.
# 사용: scripts/make-icns.sh [원본 PNG] [출력 .icns]
set -euo pipefail

SRC="${1:-composeApp/src/commonMain/composeResources/drawable/app_icon.png}"
OUT="${2:-composeApp/icons/meow.icns}"
WORK="$(mktemp -d)"
ICONSET="$WORK/meow.iconset"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$ICONSET" "$(dirname "$OUT")"

# 16·32·128·256·512 와 각 @2x (32·64·256·512·1024)
for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$SRC" --out "$ICONSET/icon_${size}x${size}.png" >/dev/null
  double=$((size * 2))
  sips -z "$double" "$double" "$SRC" --out "$ICONSET/icon_${size}x${size}@2x.png" >/dev/null
done

iconutil -c icns "$ICONSET" -o "$OUT"
echo "created $OUT"
