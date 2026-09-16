#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")" && pwd)"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT

mapfile -d '' sources < <(
  find "$root_dir/audio-runtime/src/main/java" "$root_dir/audio-runtime/src/test/java" \
    -type f -name '*.java' -print0
)

if command -v javac >/dev/null 2>&1; then
  javac -encoding UTF-8 --release 11 -d "$build_dir" "${sources[@]}"
else
  java -m jdk.compiler/com.sun.tools.javac.Main \
    -encoding UTF-8 --release 11 -d "$build_dir" "${sources[@]}"
fi

java -cp "$build_dir" com.jimmeali.ringsofpower.audio.AudioRuntimeTest
