#!/usr/bin/env bash

(
set -e
basedir="$(cd "$1" && pwd -P)"
gitcmd="git -c commit.gpgsign=false"

($gitcmd submodule update --init && "$basedir/scripts/upstream/remap.sh" "$basedir" && "$basedir/scripts/upstream/decompile.sh" "$basedir" && "$basedir/scripts/upstream/init.sh" "$basedir" && "$basedir/scripts/upstream/applyPatches.sh" "$basedir") || (
    echo "Failed to build Paper"
    exit 1
) || exit 1
if [ "$2" == "--jar" ]; then
    "$basedir/scripts/upstream/paperclip.sh" "$basedir"
fi
) || exit 1
