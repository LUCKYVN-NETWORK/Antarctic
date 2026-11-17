#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd -P)"
commit=e39995d213616cc7d60c38e6e86e562f30637ef7
shopt -s nullglob
fail() { echo "$*" >&2; exit 1; }
PYTHON=""
for candidate in python3 python; do
    if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c "import sys; assert sys.version_info[0] >= 3" >/dev/null 2>&1; then
        PYTHON="$(command -v "$candidate")"
        break
    fi
done
[[ -n "$PYTHON" ]] || fail 'Python 3 required for patch management'

check_clean_existing_src() {
    local src="$1"
    if [[ -e "$src" ]]; then
        if git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
            && [[ -z "$(git -C "$root" status --porcelain --untracked-files=all --ignored=matching -- "$src")" ]]; then
            return 0
        fi
        fail "Refusing to overwrite existing sources: $src (commit/stash local edits, or remove it manually if left by an interrupted setup)"
    fi
}

case "${1:-}" in
setup)
    upstream="${2:-$root/.upstream/Paper}"
    if command -v cygpath >/dev/null 2>&1; then upstream="$(cygpath -u "$upstream")"; fi
    if [[ ! -d "$upstream/.git" ]]; then
        mkdir -p "$(dirname "$upstream")"
        git clone --no-checkout https://github.com/PaperMC/Paper-archive.git "$upstream"
        git -C "$upstream" checkout --detach "$commit"
    fi
    upstream="$(cd "$upstream" && pwd -P)"
    [[ "$(git -C "$upstream" rev-parse HEAD)" == "$commit" ]] || fail 'Upstream does not match pinned Paper commit.'
    if [[ ! -d "$upstream/Paper-API/src/main/java" || ! -d "$upstream/Paper-Server/src/main/java" ]]; then
        : "${JAVA8_HOME:?Set JAVA8_HOME to JDK 8 for legacy generation}"
        [[ -x "$JAVA8_HOME/bin/java" || -f "$JAVA8_HOME/bin/java.exe" ]] || fail 'Invalid JAVA8_HOME'
        git -C "$upstream" submodule update --init
        mc="$upstream/work/Minecraft/1.12.2"
        mkdir -p "$mc"
        url="$(sed -n 's/.*"serverUrl": *"\([^" ]*\)".*/\1/p' "$upstream/work/BuildData/info.json")"
        [[ "$url" == https://* ]] || fail 'Invalid vanilla server URL'
        [[ -f "$mc/1.12.2.jar" ]] || curl -fL "$url" -o "$mc/1.12.2.jar"
        (cd "$upstream"; export JAVA_HOME="$JAVA8_HOME"; export PATH="$JAVA_HOME/bin:$PATH"; bash ./paper patch)
    fi
    for module in API Server; do
        src="$root/Antarctic-$module/src"
        base="$(git -C "$upstream/Paper-$module" rev-parse HEAD)"
        gitdir="$root/Antarctic-$module/.git-sources"
        if [[ -d "$src/.git" || -d "$gitdir" ]]; then
            continue
        fi
        if [[ -e "$src" ]]; then
            check_clean_existing_src "$src"
            rm -rf "$src"
        fi
        cp -R "$upstream/Paper-$module/src" "$src"
        "$PYTHON" -c "import sys, pathlib
for p in pathlib.Path(sys.argv[1]).rglob('*'):
    if p.is_file() and p.suffix.lower() not in {'.png', '.jpg', '.jpeg', '.gif', '.jar', '.zip', '.dat', '.nbt', '.schematic'}:
        raw = p.read_bytes()
        if b'\x00' not in raw and b'\r\n' in raw:
            p.write_bytes(raw.replace(b'\r\n', b'\n'))" "$src"
        git --git-dir="$gitdir" --work-tree="$src" init
        git --git-dir="$gitdir" --work-tree="$src" config core.autocrlf false
        git --git-dir="$gitdir" --work-tree="$src" add .
        git --git-dir="$gitdir" --work-tree="$src" -c user.name='Antarctic upstream' -c user.email=upstream@antarctic.invalid -c commit.gpgsign=false commit -qm 'Pinned Paper source baseline'
        git --git-dir="$gitdir" --work-tree="$src" tag antarctic-upstream
        printf '%s\n' "$base" > "$gitdir/antarctic-base"
    done
    mc="$upstream/work/Minecraft/1.12.2"
    reference="$root/Antarctic-Server/mc-dev/src/net/minecraft/server"
    mkdir -p "$reference" "$root/Antarctic-Server/libs"
    cp "$mc/net/minecraft/server/"*.java "$reference/"
    cp "$mc/1.12.2-mapped.jar" "$root/Antarctic-Server/libs/minecraft-server-1.12.2-mapped.jar"
    "$PYTHON" "$root/scripts/patch_manager.py" apply
    if git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        git -C "$root" update-index -q --refresh || true
    fi
    files=("$reference/"*.java)
    [[ ${#files[@]} -eq 1413 ]] || fail 'Incomplete Minecraft reference sources'
    echo 'Local sources ready; Minecraft reference files: 1413'
    ;;
check-src)
    [[ -n "${2:-}" ]] || fail 'Usage: patches.sh check-src <source-directory>'
    check_clean_existing_src "$2"
    ;;
apply)
    "$PYTHON" "$root/scripts/patch_manager.py" apply
    ;;
rebuild)
    "$PYTHON" "$root/scripts/patch_manager.py" rebuild
    ;;
*) fail 'Usage: patches.sh setup [upstream-directory] | apply | rebuild | check-src <source-directory>' ;;
esac
