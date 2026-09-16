#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "$0")" && pwd)"
build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT

javac -encoding UTF-8 --release 11 -d "$build_dir" \
  "$root_dir/game-app/src/main/java/com/jimmeali/ringsofpower/game/GameProgress.java" \
  "$root_dir/game-app/src/test/java/com/jimmeali/ringsofpower/game/GameProgressTest.java"
java -cp "$build_dir" com.jimmeali.ringsofpower.game.GameProgressTest
