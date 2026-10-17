<div align="center">
  <img src="public/images/antarctic.png" alt="Antarctic" width="180" />
  <h1>Antarctic</h1>
  <p>A modern, refined overhaul fork of Paper 1.12.2 built on Java 21</p>

[![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2-0284c7?style=flat-square)](https://www.minecraft.net/)
[![Java](https://img.shields.io/badge/Java-21-e11d48?style=flat-square)](https://adoptium.net/)
[![Gradle](https://img.shields.io/badge/Gradle-8.14.3-02303a?style=flat-square)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-GPL--3.0%20%7C%20MIT-16a34a?style=flat-square)](LICENSE.md)
</div>

> **In development.** Java 21 compilation is verified; server runtime and plugin compatibility are not yet validated.

## Building

**Requirements:** JDK 21 (`JAVA_HOME`), JDK 8 (`JAVA8_HOME`) for upstream generation, Git with a configured identity, Maven, and Bash with curl/patch. On Windows, use Git Bash.

```bash
git clone --branch dev https://github.com/LUCKYVN-NETWORK/Antarctic.git
cd Antarctic
./gradlew setupSources
./gradlew build -x test
```

Sources are generated locally and excluded from Git. Module jars are written to `build/libs/` within each module; they are **not standalone server distributions**. The command above skips tests.

For IntelliJ IDEA, open the root Gradle project and select JDK 21 for the project SDK and Gradle JVM. Keep `mc-dev/` as reference material, not a compiled source root.

## Contributing

1. Work on **`dev`**; **`main`** is reserved for stable changes.
2. Edit and commit inside `Antarctic-API/src` or `Antarctic-Server/src`.
3. Run `./gradlew rebuildPatches` from the project root.
4. Commit the resulting `patches/` changes to the outer repository and open a pull request.

Use `./gradlew applyPatches` to reset local sources and reapply saved patches. **Export committed changes first**—unexported commits will leave the active source branch. Uncommitted edits are rejected.

## License

Inherited **GPL-3.0**, with **MIT** contributions where specified. See [LICENSE.md](LICENSE.md) and [licenses/](licenses/).
