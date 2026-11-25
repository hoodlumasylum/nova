#!/usr/bin/env bash
# Builds android/dist/Nova.apk with only the Android SDK command-line tools (no Gradle).
# Needs: JDK 17+, ANDROID_HOME with build-tools 34 and platform 34.
#   ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/home/box/jdk}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-/home/box/android}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
B=build
rm -rf "$B"
mkdir -p "$B/classes" "$B/dex" dist
cp app/src/main/AndroidManifest.xml "$B/AndroidManifest.xml"
"$BT/aapt2" compile --dir app/src/main/res -o "$B/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest "$B/AndroidManifest.xml" -A app/src/main/assets -o "$B/base.apk" \
  --java "$B/gen" "$B/res.zip" --min-sdk-version 24 --target-sdk-version 30 --version-code 3 --version-name 1.0.2
javac --release 11 -classpath "$JAR" -d "$B/classes" $(find app/src/main/java "$B/gen" -name '*.java')
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/unsigned.apk"
python3 tools/pack_apk.py "$B/unsigned.apk" "$B/dex/classes.dex" native/jniLibs
"$BT/zipalign" -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"
KS="${NOVA_KEYSTORE:-$HOME/.android/nova-release.keystore}"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass "${NOVA_KS_PASS:-android}" -keypass "${NOVA_KS_PASS:-android}" \
    -alias "${NOVA_KS_ALIAS:-nova}" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Nova, OU=CodeAque, O=Hoodlum Asylum, C=GB" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:${NOVA_KS_PASS:-android}" --ks-key-alias "${NOVA_KS_ALIAS:-nova}" \
  --out dist/Nova.apk "$B/aligned.apk"
"$BT/apksigner" verify dist/Nova.apk && ls -la dist/Nova.apk
