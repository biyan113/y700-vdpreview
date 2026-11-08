#!/bin/sh
set -e
ROOT=$(cd "$(dirname "$0")" && pwd)
cd "$ROOT"
if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
  SDK="$ANDROID_HOME"
elif [ -d "$HOME/Library/Android/sdk" ]; then
  SDK="$HOME/Library/Android/sdk"
else
  echo "找不到 Android SDK。设置 ANDROID_HOME，或安装到 \$HOME/Library/Android/sdk" >&2
  exit 1
fi
BT="$SDK/build-tools/36.0.0"
PLATFORM="$SDK/platforms/android-36/android.jar"
if [ ! -x "$BT/aapt2" ] || [ ! -f "$PLATFORM" ]; then
  echo "需要 platforms;android-36 和 build-tools;36.0.0，当前 SDK 是 $SDK" >&2
  exit 1
fi
OUT="$ROOT/build"
rm -rf "$OUT"
mkdir -p "$OUT/flat" "$OUT/gen" "$OUT/classes" "$OUT/dex"
python3 "$ROOT/tools/make_icon.py"
if [ ! -f "$ROOT/debug.keystore" ]; then
  keytool -genkeypair -keystore "$ROOT/debug.keystore" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Y700 Preview, O=Android, C=CN"
fi
"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/flat"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 33 \
  --java "$OUT/gen" \
  "$OUT/flat"/*.flat
find "$ROOT/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 17 -encoding UTF-8 -cp "$PLATFORM" -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --min-api 26 --output "$OUT/dex" @"$OUT/classes.txt"
cp "$OUT/base.apk" "$OUT/raw.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/raw.apk" classes.dex)
"$BT/zipalign" -p -f 4 "$OUT/raw.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign \
  --ks "$ROOT/debug.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$ROOT/vdpreview.apk" \
  "$OUT/aligned.apk"
"$BT/apksigner" verify "$ROOT/vdpreview.apk"
echo "APK $ROOT/vdpreview.apk"
