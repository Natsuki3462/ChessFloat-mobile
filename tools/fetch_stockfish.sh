#!/usr/bin/env bash
# 下载 Android 版 Stockfish 并放到 jniLibs。
#
#   ./tools/fetch_stockfish.sh              # 默认 sf_17.1
#   ./tools/fetch_stockfish.sh sf_17.1      # 指定版本
#   MIRROR= ./tools/fetch_stockfish.sh      # 直连 GitHub（默认走镜像）
#
# 注意：Android 10+ 禁止执行应用可写目录里的二进制，所以引擎必须放在
# jniLibs 里、以 lib*.so 的形式随 APK 安装到 nativeLibraryDir 才能被 exec。

set -euo pipefail

VERSION="${1:-sf_17.1}"
MIRROR="${MIRROR-https://gh-proxy.com}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSETS="$ROOT/app/src/main/assets"
TMP="$(mktemp -d)"

URL="$MIRROR/https://github.com/official-stockfish/Stockfish/releases/download/$VERSION/stockfish-android-armv8.tar"

echo "下载 $URL"
curl -fL --progress-bar -o "$TMP/sf.tar" "$URL"

mkdir -p "$DEST" "$ASSETS"
tar -xf "$TMP/sf.tar" -C "$TMP"
SRC="$(find "$TMP" -type f -name 'stockfish-android-armv8' | head -1)"
if [ -z "$SRC" ]; then
    echo "解包后没找到 stockfish-android-armv8" >&2
    exit 1
fi

cp "$SRC" "$DEST/libstockfish.so"
for f in Copying.txt AUTHORS; do
    found="$(find "$TMP" -type f -name "$f" | head -1)"
    [ -n "$found" ] && cp "$found" "$ASSETS/$f"
done

# 校验是 64 位 ARM（ELF class=2, machine=183）
python3 - "$DEST/libstockfish.so" <<'PY' 2>/dev/null || true
import sys
d = open(sys.argv[1], "rb").read(20)
assert d[:4] == b"\x7fELF", "不是 ELF"
assert d[4] == 2, "不是 64 位"
assert int.from_bytes(d[18:20], "little") == 183, "不是 arm64"
print("架构校验通过：ELF64 / AArch64")
PY

echo
echo "完成："
ls -lh "$DEST/libstockfish.so" | awk '{print "  " $9, $5}'
rm -rf "$TMP"
