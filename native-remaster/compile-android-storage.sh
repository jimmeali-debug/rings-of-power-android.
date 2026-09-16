#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")" && pwd)"
test -n "$ANDROID_HOME"
android_jar="$ANDROID_HOME/platforms/android-35/android.jar"
test -f "$android_jar"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT

mapfile -d '' sources < <(
  find "$root_dir/audio-runtime/src/main/java" "$root_dir/android-storage/src/main/java" \
    -type f -name '*.java' -print0
)

javac -encoding UTF-8 --release 11 -cp "$android_jar" -d "$build_dir" "${sources[@]}"
echo "Android storage adapter compiled successfully"
