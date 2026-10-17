<div align="center">
  <img src="public/images/antarctic.png" alt="Antarctic Logo" width="180" />
  <h1>Antarctic</h1>
  <p><strong>A modern, refined overhaul fork of Paper 1.12.2 built on Java 21</strong></p>

  [![CI](https://img.shields.io/github/actions/workflow/status/LUCKYVN-NETWORK/Antarctic/ci.yml?branch=dev&style=flat-square&label=CI)](https://github.com/LUCKYVN-NETWORK/Antarctic/actions)
  [![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2-0284c7?style=flat-square)](https://www.minecraft.net/)
  [![Java](https://img.shields.io/badge/Java-21-e11d48?style=flat-square)](https://adoptium.net/)
  [![Gradle](https://img.shields.io/badge/Gradle-8.14.3-02303a?style=flat-square)](https://gradle.org/)
  [![License](https://img.shields.io/badge/License-GPL--3.0%20%7C%20MIT-16a34a?style=flat-square)](LICENSE.md)
</div>

---

**Antarctic** is a high-performance Paper 1.12.2 server fork engineered for modern Java 21 runtimes. It combines modernized JVM optimizations, specialized primitive data structures, and curated backports to deliver low tick latency, hardened exploit defenses, and robust server configurability without sacrificing compatibility with the 1.12.2 ecosystem.

> [!NOTE]
> **Active Development:** Development is tracked on the [`dev`](https://github.com/LUCKYVN-NETWORK/Antarctic/tree/dev) branch. Java 21 compilation, unit test suites, and patch round-trips are verified in CI.

---

## 🌿 Branches & Release Model

Antarctic uses a dual-branch development lifecycle:

| Branch | Status | Description |
| :--- | :--- | :--- |
| **[`dev`](https://github.com/LUCKYVN-NETWORK/Antarctic/tree/dev)** | **Default** | **Live development & active changes.** All feature implementations, backports, and refactors land here first. |
| **[`main`](https://github.com/LUCKYVN-NETWORK/Antarctic/tree/main)** | Protected | **Stable, production-verified snapshots.** Tagged releases and release-ready builds are published from this branch. |

Pull requests and patch contributions should target **`dev`**.

---

## ⚡ Highlights & Features

- **Modern Java 21 Runtime:** Built and verified for Java 21 LTS, leveraging modern bytecode capabilities while maintaining full protocol parity for 1.12.2 clients.
- **Optimized Hot Paths & Primitives:** Incorporates Fastutil collections, primitive unboxing in map decorations and coordinates, and reduced object allocation overhead across tick loops.
- **Exploit & Crash Mitigations:** Integrated protections against book crash exploits, oversized chunk serialization overloads, invalid movement/digging packets, and entity desynchronization.
- **Granular Configurability (`antarctic.yml`):** Fine-grained controls for chunk loading past world borders, book mechanics, armour stand ticking, and dupe handling.
- **Curated High-Performance Backports:** Integrates audited improvements and fixes from projects including [Dionysus](https://github.com/nopjmp/Dionysus), [Pufferfish](https://github.com/pufferfish-gg/Pufferfish), [FlamePaper](https://github.com/2LStudios-MC/FlamePaper), and modern Paper.

---

## 📊 Performance & Benchmarks

Antarctic aims to eliminate tick stalls and reduce heap churn during entity tracking, tile-entity ticking, and chunk I/O.

<!-- Performance Benchmark Graph Container -->
<div align="center">
  <table>
    <tr>
      <td align="center">
        <br />
        <p><em>📈 Performance benchmark metrics and comparison graphs against upstream Paper 1.12.2 will be published here upon test suite completion.</em></p>
        <br />
      </td>
    </tr>
  </table>
</div>

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 21** configured on `JAVA_HOME` (compilation & runtime target)
- **JDK 8** configured on `JAVA8_HOME` (upstream decompile/remap requirement)
- **Git** with a configured committer identity
- **Maven** and a **Bash** environment (curl/patch; use Git Bash on Windows)

### Compilation
```bash
# Clone the repository (dev branch)
git clone https://github.com/LUCKYVN-NETWORK/Antarctic.git
cd Antarctic

# Decompile upstream sources and apply patch series
./gradlew setupSources

# Build project jars (skipping tests for quick local builds)
./gradlew build -x test
```

Generated module JARs will be available under `Antarctic-API/build/libs/` and `Antarctic-Server/build/libs/`.

---

## 🔄 Patch Workflow

Antarctic manages modifications as Git patch sets against upstream Paper:

1. **Setup Sources:** Run `./gradlew setupSources` to initialize inner git trees.
2. **Make Changes:** Edit code directly in `Antarctic-API/src` or `Antarctic-Server/src`.
3. **Commit Internally:** Create dedicated commits in the respective submodule repository.
4. **Export Patches:** Run `./gradlew rebuildPatches` from the root directory to generate updated `.patch` files under `patches/`.
5. **Commit to Repository:** Stage and commit the resulting patch files to the outer repository.

---

## 📄 License & Credits

- Antarctic is licensed under **[GPL-3.0](LICENSE.md)**, inheriting licensing from Paper, Spigot, and Bukkit.
- Permissive contributions and backported components are released under the **[MIT License](LICENSE.md#the-mit-license-mit)** where designated.
- A single canonical [**LICENSE.md**](LICENSE.md) is maintained at the root of the project.
- Sincere appreciation to upstream developers and contributors from [PaperMC](https://papermc.io/), [SpigotMC](https://www.spigotmc.org/), [Dionysus](https://github.com/nopjmp/Dionysus), [Pufferfish](https://github.com/pufferfish-gg/Pufferfish), and the wider Minecraft open-source ecosystem.
