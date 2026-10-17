#!/usr/bin/env bash

(
set -e
basedir="$(cd "$1" && pwd -P)"
workdir="$basedir/work"
mcver=$(cat "$workdir/BuildData/info.json" | grep minecraftVersion | cut -d '"' -f 4)
paperjar="$basedir/Antarctic-Server/build/libs/Antarctic-Server-$mcver-R0.1-SNAPSHOT.jar"
if [ ! -f "$paperjar" ]; then
    paperjar="$basedir/Antarctic-Server/build/libs/Antarctic-Server.jar"
fi
if [ ! -f "$paperjar" ]; then
    for f in "$basedir/Antarctic-Server/build/libs/"*.jar; do
        if [ -f "$f" ] && [[ "$f" != *-sources.jar ]]; then
            paperjar="$f"
            break
        fi
    done
fi
if [ ! -f "$paperjar" ]; then
    paperjar="$basedir/Paper-Server/target/paper-$mcver.jar"
fi
vanillajar="$workdir/Minecraft/$mcver/$mcver.jar"

(
    cd "$workdir/Paperclip"
    mvn clean package "-Dmcver=$mcver" "-Dpaperjar=$paperjar" "-Dvanillajar=$vanillajar"
)
cp "$workdir/Paperclip/assembly/target/paperclip-${mcver}.jar" "$basedir/paperclip.jar"

echo ""
echo ""
echo ""
echo "Build success!"
echo "Copied final jar to $(cd "$basedir" && pwd -P)/paperclip.jar"
) || exit 1
