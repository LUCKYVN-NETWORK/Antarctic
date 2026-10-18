#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd -P)"
commit=e39995d213616cc7d60c38e6e86e562f30637ef7
shopt -s nullglob
fail() { echo "$*" >&2; exit 1; }
PYTHON="$(command -v python3 || command -v python || fail 'Python 3 required for patch management')"

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
        if [[ -d "$src/.git" ]]; then
            continue
        fi
        [[ ! -e "$src" ]] || fail "Refusing to overwrite existing sources: $src"
        cp -R "$upstream/Paper-$module/src" "$src"
        git -C "$src" init
        git -C "$src" add .
        git -C "$src" -c user.name='Antarctic upstream' -c user.email=upstream@antarctic.invalid -c commit.gpgsign=false commit -qm 'Pinned Paper source baseline'
        git -C "$src" tag antarctic-upstream
        printf '%s\n' "$base" > "$src/.git/antarctic-base"
    done
    mc="$upstream/work/Minecraft/1.12.2"
    reference="$root/Antarctic-Server/mc-dev/src/net/minecraft/server"
    mkdir -p "$reference" "$root/Antarctic-Server/libs"
    cp "$mc/net/minecraft/server/"*.java "$reference/"
    cp "$mc/1.12.2-mapped.jar" "$root/Antarctic-Server/libs/minecraft-server-1.12.2-mapped.jar"
    "$PYTHON" "$root/scripts/patch_manager.py" apply
    files=("$reference/"*.java)
    [[ ${#files[@]} -eq 1413 ]] || fail 'Incomplete Minecraft reference sources'
    echo 'Local sources ready; Minecraft reference files: 1413'
    ;;
apply)
    "$PYTHON" "$root/scripts/patch_manager.py" apply
    ;;
rebuild)
    "$PYTHON" "$root/scripts/patch_manager.py" rebuild
    ;;
*) fail 'Usage: patches.sh setup [upstream-directory] | apply | rebuild' ;;
esac
