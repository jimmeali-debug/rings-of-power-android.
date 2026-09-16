#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")" && pwd)"
output_apk="${1:-$root_dir/game-app/build/Rings-of-Power-Game.apk}"
android_api="${ANDROID_API_LEVEL:-35}"
build_tools_version="${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"

test -n "${ANDROID_HOME:-}"
android_jar="$ANDROID_HOME/platforms/android-$android_api/android.jar"
build_tools="$ANDROID_HOME/build-tools/$build_tools_version"
for required in "$android_jar" "$build_tools/aapt2" "$build_tools/d8" \
    "$build_tools/zipalign" "$build_tools/apksigner"; do
  test -e "$required"
done

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
classes_dir="$work_dir/classes"
dex_dir="$work_dir/dex"
mkdir -p "$classes_dir" "$dex_dir" "$(dirname "$output_apk")"

mapfile -d '' sources < <(
  find \
    "$root_dir/rom-runtime/src/main/java" \
    "$root_dir/game-app/src/main/java" \
    -type f -name '*.java' -print0
)

javac -encoding UTF-8 --release 11 -cp "$android_jar" -d "$classes_dir" "${sources[@]}"
jar --create --file "$work_dir/classes.jar" -C "$classes_dir" .
"$build_tools/d8" \
  --lib "$android_jar" \
  --min-api 23 \
  --output "$dex_dir" \
  "$work_dir/classes.jar"

"$build_tools/aapt2" link \
  -I "$android_jar" \
  --manifest "$root_dir/game-app/AndroidManifest.xml" \
  --min-sdk-version 23 \
  --target-sdk-version "$android_api" \
  --version-code 24 \
  --version-name 0.24.0 \
  -o "$work_dir/base.apk"

(cd "$dex_dir" && zip -q -j "$work_dir/base.apk" classes.dex)
"$build_tools/zipalign" -f -p 4 "$work_dir/base.apk" "$work_dir/aligned.apk"

keytool -genkeypair \
  -keystore "$work_dir/game.keystore" \
  -storepass android \
  -keypass android \
  -alias game \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -dname "CN=Rings of Power Game,O=Clean Room,C=US" \
  >/dev/null 2>&1

"$build_tools/apksigner" sign \
  --ks "$work_dir/game.keystore" \
  --ks-key-alias game \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$output_apk" \
  "$work_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose "$output_apk"
"$build_tools/aapt2" dump badging "$output_apk" \
  | grep -q "package: name='com.jimmeali.ringsofpower.game'"
unzip -t "$output_apk" >/dev/null
unzip -l "$output_apk" | grep -q 'classes.dex'
echo "Built game APK: $output_apk"
