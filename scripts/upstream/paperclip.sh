#!/usr/bin/env bash

(
set -e
basedir="$(cd "$1" && pwd -P)"
workdir="$basedir/work"
mcver=$(cat "$workdir/BuildData/info.json" | grep minecraftVersion | cut -d '"' -f 4)
paperjar=""
# Globs expand in order, so the last match is the newest Antarctic-b<year>.<month>.jar.
for f in "$basedir/Antarctic-Server/build/libs/"Antarctic-b*.jar; do
    if [ -f "$f" ]; then
        paperjar="$f"
    fi
done
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
