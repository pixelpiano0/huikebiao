#!/bin/bash
# 在本机（aarch64 proot）里打包一个最小的 WebView 壳 APK
# 依赖：openjdk-17、qemu-x86_64-static、libc6:amd64 等
set -e

SDK=/root/android
BT=$SDK/android-14
AJAR=$SDK/android-34/android.jar
APP=$SDK/app
OUT=$SDK/out
QEMU="qemu-x86_64-static"
VERSION_NAME=${1:-0.2}
VERSION_CODE=${2:-2}

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/compiled"

echo "[1/7] 编译资源"
$QEMU "$BT/aapt2" compile --dir "$APP/res" -o "$OUT/compiled/res.zip"

echo "[2/7] 链接 APK"
# 版本号以脚本参数为准，避免清单里的旧值覆盖
sed -E "s/android:versionCode=\"[0-9]+\"/android:versionCode=\"$VERSION_CODE\"/; s/android:versionName=\"[^\"]+\"/android:versionName=\"$VERSION_NAME\"/" \
  "$APP/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
$QEMU "$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$AJAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  -A "$APP/assets" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  "$OUT/compiled/res.zip"

echo "[3/7] 编译 Java"
javac -encoding UTF-8 -source 1.8 -target 1.8 -Xlint:-options -nowarn \
  -classpath "$AJAR" -d "$OUT/classes" $(find "$APP/src" -name '*.java')

echo "[4/7] 生成 dex"
java -Xmx512m -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --release --min-api 24 --lib "$AJAR" \
  --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "[5/7] 把 dex 塞进 APK"
python3 - "$OUT/base.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print('   dex added')
PY

echo "[6/7] 对齐"
$QEMU "$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "[7/7] 签名"
if [ ! -f "$SDK/debug.keystore" ]; then
  keytool -genkeypair -keystore "$SDK/debug.keystore" -alias dsh -keyalg RSA \
    -keysize 2048 -validity 10000 -storepass android -keypass android \
    -dname "CN=DSH Kebiao, OU=DSH, O=DSH, L=Changsha, ST=Hunan, C=CN" >/dev/null 2>&1
fi
java -jar "$BT/lib/apksigner.jar" sign \
  --ks "$SDK/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/kebiao-$VERSION_NAME.apk" "$OUT/aligned.apk"

java -jar "$BT/lib/apksigner.jar" verify --print-certs "$OUT/kebiao-$VERSION_NAME.apk" | head -4
ls -lh "$OUT/kebiao-$VERSION_NAME.apk"
