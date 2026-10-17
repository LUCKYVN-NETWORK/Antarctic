#!/usr/bin/env bash
set -euo pipefail
script="$(cd "$(dirname "$0")" && pwd -P)/patches.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/scripts"
cp "$script" "$tmp/scripts/patches.sh"
for module in API Server; do
    src="$tmp/Antarctic-$module/src"
    mkdir -p "$src" "$tmp/patches/${module,,}"
    git -C "$src" init -q
    git -C "$src" config user.name Test
    git -C "$src" config user.email test@antarctic.invalid
    git -C "$src" config commit.gpgsign false
    printf 'baseline\n' > "$src/Example.java"
    git -C "$src" add .
    git -C "$src" commit -qm Baseline
    git -C "$src" tag antarctic-upstream
    printf 'changed\n' > "$src/Example.java"
    git -C "$src" commit -qam Change
 done
bash "$tmp/scripts/patches.sh" rebuild
bash "$tmp/scripts/patches.sh" apply
for module in API Server; do
    [[ "$(cat "$tmp/Antarctic-$module/src/Example.java")" == changed ]]
done
printf 'dirty\n' > "$tmp/Antarctic-Server/src/Example.java"
if bash "$tmp/scripts/patches.sh" apply; then
    echo 'FAIL: dirty sources were not protected' >&2
    exit 1
fi
[[ "$(cat "$tmp/Antarctic-Server/src/Example.java")" == dirty ]]
echo 'PASS: Bash patch round-trip and dirty-source protection'
