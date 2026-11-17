#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd -P)"
repo_root="$(cd "$script_dir/.." && pwd -P)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/scripts"
cp "$script_dir/patches.sh" "$tmp/scripts/patches.sh"
cp "$script_dir/patch_manager.py" "$tmp/scripts/patch_manager.py"
for module in API Server; do
    src="$tmp/Antarctic-$module/src"
    gitdir="$tmp/Antarctic-$module/.git-sources"
    mkdir -p "$src"
    git --git-dir="$gitdir" --work-tree="$src" init -q
    git --git-dir="$gitdir" --work-tree="$src" config user.name Test
    git --git-dir="$gitdir" --work-tree="$src" config user.email test@antarctic.invalid
    git --git-dir="$gitdir" --work-tree="$src" config commit.gpgsign false
    git --git-dir="$gitdir" --work-tree="$src" config core.autocrlf false
    printf 'baseline\n' > "$src/Example.java"
    git --git-dir="$gitdir" --work-tree="$src" add .
    git --git-dir="$gitdir" --work-tree="$src" commit -qm Baseline
    git --git-dir="$gitdir" --work-tree="$src" tag antarctic-upstream
    printf 'changed\n' > "$src/Example.java"
    git --git-dir="$gitdir" --work-tree="$src" commit -qam Change
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

# Verify setup pre-existing source guard across tracked, dirty, ignored, and non-git states
guard="$tmp/guard-repo"
mkdir -p "$guard/scripts" "$guard/Antarctic-Server/src/main/java/me/stella/antarctic"
cp "$script_dir/patches.sh" "$guard/scripts/patches.sh"
cp "$script_dir/patch_manager.py" "$guard/scripts/patch_manager.py"
cp "$repo_root/.gitignore" "$guard/.gitignore"
printf 'package me.stella.antarctic;\nclass Tracked {}\n' > "$guard/Antarctic-Server/src/main/java/me/stella/antarctic/Tracked.java"
git -C "$guard" init -q
git -C "$guard" config user.name Test
git -C "$guard" config user.email test@antarctic.invalid
git -C "$guard" config commit.gpgsign false
git -C "$guard" config core.autocrlf false
git -C "$guard" add .gitignore scripts/ Antarctic-Server/src/main/java/me/stella/antarctic/Tracked.java
git -C "$guard" commit -qm "Initial tracked sources"

# 1. Missing directory, clean outer-tracked directory, and git worktree (.git file) must pass
bash "$guard/scripts/patches.sh" check-src "$guard/Antarctic-API/src"
bash "$guard/scripts/patches.sh" check-src "$guard/Antarctic-Server/src"
git -C "$guard" worktree add -q "$tmp/guard-wt" HEAD
bash "$tmp/guard-wt/scripts/patches.sh" check-src "$tmp/guard-wt/Antarctic-Server/src"

# 2. Modified outer-tracked file must be refused
printf '// dirty\n' >> "$guard/Antarctic-Server/src/main/java/me/stella/antarctic/Tracked.java"
if bash "$guard/scripts/patches.sh" check-src "$guard/Antarctic-Server/src" 2>/dev/null; then
    echo 'FAIL: modified tracked source was not protected by setup guard' >&2
    exit 1
fi
git -C "$guard" checkout -- Antarctic-Server/src/main/java/me/stella/antarctic/Tracked.java

# 3. Gitignored files under Antarctic-Server/src or Antarctic-API/src must be refused
mkdir -p "$guard/Antarctic-Server/src/main/java/net/minecraft/server" "$guard/Antarctic-API/src/main/java/org/bukkit"
printf 'class Wip {}\n' > "$guard/Antarctic-Server/src/main/java/net/minecraft/server/Wip.java"
printf 'class ApiWip {}\n' > "$guard/Antarctic-API/src/main/java/org/bukkit/ApiWip.java"
if bash "$guard/scripts/patches.sh" check-src "$guard/Antarctic-Server/src" 2>/dev/null; then
    echo 'FAIL: gitignored server source was not protected by setup guard' >&2
    exit 1
fi
if bash "$guard/scripts/patches.sh" check-src "$guard/Antarctic-API/src" 2>/dev/null; then
    echo 'FAIL: gitignored API source was not protected by setup guard' >&2
    exit 1
fi
rm -rf "$guard/Antarctic-Server/src/main/java/net" "$guard/Antarctic-API/src"
bash "$guard/scripts/patches.sh" check-src "$guard/Antarctic-Server/src"

# 4. Non-git root with existing src must be refused
rm -rf "$guard/.git"
if bash "$guard/scripts/patches.sh" check-src "$guard/Antarctic-Server/src" 2>/dev/null; then
    echo 'FAIL: non-git existing source directory was not protected' >&2
    exit 1
fi

echo 'PASS: Bash patch round-trip and dirty-source protection'
