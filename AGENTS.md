# AGENTS.md — Antarctic Agent Guidelines

Base operational guidelines and engineering standards for AI coding agents working on **Antarctic** (a high-performance Paper `1.12.2` overhaul fork built on **Java 21**).

---

## 1. Core Engineering Mandates

Every change, optimization, and patch backport in `Antarctic` **must** satisfy all four core gates before completion:

### 1.1 1.12.2 Relevance: Auto-Reject Features vs. Evaluate Engine Viability
- **Auto-reject post-`1.12.2` game features and content.** Immediately reject patches targeting gameplay content, entities, blocks, items, or mechanics introduced after `1.12.2` (e.g., dolphins/aquatic swimming AI, phantoms, raids, 1.14+ villager brain/POI schedules, sculk/vibrations, copper oxidation, trial spawners).
- **Evaluate `1.13+` underlying engine tweaks (lighting, chunks, physics, networking) for `1.12.2` rebuild viability.** Do **not** auto-reject engine or algorithmic optimizations from modern Paper, Leaf, Pufferfish, SpottedLeaf/Moonrise, Dionysus, or FlamePaper solely because upstream targets post-1.13 internals (`BlockState` palettes, 1.14+ light engine/Starlight, modern `ChunkSystem`, DataFixerUpper). Instead, determine whether the underlying logic can be **cleanly rebuilt on `1.12.2` NMS structures** (`Chunk`, `ChunkSection`, `NibbleArray`, `PlayerChunkMap`, `World` light/tick queues, `RegionFileCache`):
    - **Client & protocol acceptability:** Rebuilt logic must emit valid `1.12.2` protocol packets (chunk bitmasks, skylight/blocklight nibble arrays, block/entity sync, movement/collision expectations) with zero client-side desync, visual lighting glitches, or chunk holes for connecting `1.12.2` clients.
    - **Storage & API compatibility:** Must preserve `1.12.2` Anvil NBT save formats and CraftBukkit/Paper `1.12.2` plugin contracts (never copy-paste mismatched post-1.13 types blindly).
- **Map obfuscated symbols accurately.** Always cross-check Spigot-mapped `1.12.2` NMS classes (`Antarctic-Server/mc-dev/src/net/minecraft/server/`) against the canonical deobfuscated **MCP 9.40** reference (`https://github.com/WangTingZheng/mcp940`) so method/field overloads (`a`, `b`, `c`, `f`, `k`, etc.) are never misidentified.

### 1.2 Vanilla 1.12.2 Gameplay Parity
- **Preserve base vanilla gameplay.** All optimizations and refactors must produce game state transitions and observable mechanics identical to vanilla Minecraft `1.12.2`, verified against **MCP 9.40** (`https://github.com/WangTingZheng/mcp940`) and local `mc-dev` sources.
- **High-sensitivity mechanics require exact parity:**
    - Entity movement, velocity normalization, knockback, and trigonometry (respect vanilla `MathHelper` vs platform math boundaries where drift alters physics or terrain).
    - Block collision sweeps (`AxisAlignedBB` / `World.getCubes`), boundary inclusivity (`<` vs `<=`), WorldBorder checks, and fluid/suffocation checks.
    - Redstone signal propagation, update ordering, tile-entity tick timing, hopper/inventory transfer order, and item stack merging.
    - World generation RNG seeding/consumption and mob spawning rules.
- **Configurable deviations.** If a patch intentionally alters vanilla behavior (e.g., exploit mitigation, entity push rules, chunk border limits, interval throttling), expose it via `AntarcticTomlConfig` (`com.destroystokyo.paper.config.toml.modules.*`) with safe defaults and clear documentation.

### 1.3 Thorough Backport Auditing & Testing
- **Never apply blind diffs.** Upstream patches must be audited line-by-line against `Antarctic`'s existing patch stack (`Spigot-*-Patches/`, `Antarctic-API/*-patches/`, `Antarctic-Server/*-patches/`) to prevent duplicate hooks, broken CraftBukkit event contracts, or regressions in earlier patches.
- **Thread-safety & main-thread invariants.** `1.12.2` NMS structures (`World`, `Chunk`, `PlayerChunkMap`, `EntityTracker`, `NBTTagCompound`) are overwhelmingly main-thread confined. Any off-thread or Java 21 virtual-thread work must enforce explicit synchronization or read-after-write barriers and never mutate live NMS world state asynchronously.
- **Round-trip patch verification.** Backports are incomplete until exported via `./gradlew rebuildPatches`, cleanly re-applied from `antarctic-upstream` via `./gradlew applyPatches`, compiled under JDK 21, and verified by automated tests.

### 1.4 Mandatory Dual-Gate Verification: Base Logic & Performance
Every implementation **must** pass both **base logic tests** and **performance tests**:

1. **Base Logic Tests (Correctness & Parity Gate):**
    - Add or update deterministic JUnit tests (`Antarctic-Server/src/test/java/` or `Antarctic-API/src/test/java/`) covering normal execution, vanilla parity against `mcp940`/baseline logic, boundary conditions (unloaded chunks, `null`/empty inputs, coordinate extremes, `NaN`/overflow, empty vs full-cube blocks), and config toggle states (`true` vs `false`).
    - Initialize NMS registries cleanly in unit tests when needed (e.g., `DispenserRegistry.c()` in `@BeforeClass`).
    - Run and pass `./gradlew :Antarctic-API:test` and targeted + suite server tests (`./gradlew :Antarctic-Server:test --tests <TestClass>`).

2. **Performance Tests & Optimal Resource Utilization (Efficiency Gate):**
    - **Meaningful, measurable gain required.** Every optimization must demonstrate a concrete improvement in throughput, tick latency (MSPT), or heap allocation rate via repeatable microbenchmarks / JMH-style harnesses or timed stress tests comparing baseline vs optimized paths under realistic `1.12.2` workloads. Reject complexity that yields negligible or noisy gains.
    - **Hardware & CPU sympathy:**
        - Favor cache-friendly primitive arrays and specialized collections (`it.unimi.dsi.fastutil.*`, `me.stella.antarctic.collection.*`, `me.stella.antarctic.spatial.*`) over boxed `java.util` collections and pointer-chasing graphs.
        - Cache invariant lookups across spatial sweeps (e.g., caching `Chunk` references by `x >> 4, z >> 4` instead of repeating `World.isLoaded` / `getType` hash lookups per block).
        - Keep hot-path branches predictable; short-circuit common cases early (e.g., `AIR`/empty voxels, zero-delta movement, unregistered Bukkit events via `getRegisteredListeners().length == 0`).
    - **Java 21 JVM & memory optimization:**
        - Eliminate object churn in tick/network hot loops (no transient `Iterator`s, `Stream`s, lambdas capturing state, autoboxed primitives, unnecessary `BlockPosition` allocations when `PooledBlockPosition` or primitive coords suffice, or defensive `TileEntity` snapshots on read-only paths).
        - Respect HotSpot C2 JIT inlining: keep hot methods small and monomorphic where possible, and extract cold error/fallback logic into separate helper methods.
        - Leverage Java 21 capabilities logically (e.g., virtual threads for blocking I/O such as auth/chat executors, modern concurrency primitives, bitwise/intrinsic operations) without introducing contention, carrier-thread pinning (`synchronized` around blocking I/O), or GC pressure.

---

## 2. Repository Architecture & Layout

| Path | Purpose |
| :--- | :--- |
| `Antarctic-API/` | Patched Bukkit/Paper `1.12.2` API module (`Antarctic-API/paper-patches/`). |
| `Antarctic-Server/` | Patched CraftBukkit/Paper/NMS `1.12.2` server module (`minecraft-patches/` for pure NMS diffs; `paper-patches/` for CraftBukkit/Paper/config/mixed diffs). |
| `Antarctic-Server/src/main/java/me/stella/antarctic/` | Native Antarctic low-level primitives (`collection`, `math`, `spatial`). Tracked in outer Git **and** committed into the inner source patch series. |
| `Antarctic-Server/mc-dev/src/net/minecraft/server/` | Ignored local reference directory containing all 1,413 decompiled Spigot-mapped `1.12.2` NMS classes (populated by `setupSources`). |
| `Spigot-API-Patches/`, `Spigot-Server-Patches/` | Upstream Paper `1.12.2` baseline patches (pinned commit `e39995d213616cc7d60c38e6e86e562f30637ef7`). Prefer adding new work in `Antarctic-*/*-patches/` unless specifically maintaining upstream bootstrap. |
| `scripts/patches.sh`, `scripts/patch_manager.py` | Patch lifecycle tooling (`setup`, `apply`, `rebuild`) managing inner `.git-sources` trees tagged at `antarctic-upstream`. |
| `reports/` | **Gitignored** local agent reports (CI audits, backport audits, benchmark results). See §2.1. |

### 2.1 Agent Reports (`reports/`)
- Audit, investigation, and benchmark reports written by agents live in `reports/` at the repository root. The directory is **gitignored** (`/reports/` in `.gitignore`); never commit or `git add -f` its contents.
- Naming: `reports/YYYY-MM-DD-<kebab-topic>.md` (e.g. `reports/2026-11-17-ci-failed-runs-audit.md`).
- **Before starting work, check `reports/` for recent reports on the same area** (CI, patch tooling, a subsystem you are backporting) and follow up on any open recommended actions. When you resolve one, append a dated "Resolution" note to that report instead of deleting it.
- Each report should state the date, scope, the evidence it used (run/job IDs, commits, log excerpts), what was verified locally versus not verified, and prioritized recommended actions.

### External Vanilla Reference
- **MCP 9.40 (`1.12.2`):** `https://github.com/WangTingZheng/mcp940`
    - Use MCP 9.40 to inspect human-readable vanilla `1.12.2` class, method, field, and parameter names alongside `Antarctic-Server/mc-dev/src/net/minecraft/server/*.java` before modifying or backporting any NMS logic.

---

## 3. Environment & Patch Workflow

### Prerequisites
- **JDK 21** on `JAVA_HOME` (required to compile and run `Antarctic`; `--release 21`).
- **JDK 8** on `JAVA8_HOME` (required only during initial `./gradlew setupSources` upstream decompile/remap).
- **Python 3**, **Git** (with user identity configured), and **Bash** (Git Bash on Windows).

### Standard Development Lifecycle
1. **Initialize workspace (first run):**
    ```bash
    ./gradlew setupSources
    ```
2. **Import an unpatched NMS class (if not yet in `Antarctic-Server/src`):**
    - Copy the target file from `Antarctic-Server/mc-dev/src/net/minecraft/server/<Class>.java` into `Antarctic-Server/src/main/java/net/minecraft/server/<Class>.java` before or as part of your patch commit so diffs stay minimal and traceable.
3. **Inspect & edit inner source repositories:**
    - `Antarctic-API/src` uses git dir `Antarctic-API/.git-sources`
    - `Antarctic-Server/src` uses git dir `Antarctic-Server/.git-sources`
    ```bash
    git --git-dir=Antarctic-Server/.git-sources --work-tree=Antarctic-Server/src status
    ```
4. **Compile & run logic + performance tests:**
    ```bash
    ./gradlew compileJava compileTestJava
    ./gradlew :Antarctic-API:test
    ./gradlew :Antarctic-Server:cleanTest :Antarctic-Server:test --tests <YourLogicAndPerfTests>
    ./gradlew :Antarctic-Server:test
    ```
    *(Note: `ItemStackSkullTest`, `ItemStackLoreEnchantmentTest`, and `ItemStackBookTest` are inherited upstream exclusions in `Antarctic-Server/build.gradle`; pass `-PincludeInheritedFailures` only when explicitly diagnosing them.)*
5. **Commit inside the inner repository & export patches:**
    ```bash
    git --git-dir=Antarctic-Server/.git-sources --work-tree=Antarctic-Server/src add -A
    git --git-dir=Antarctic-Server/.git-sources --work-tree=Antarctic-Server/src commit -m "<type>: <message>"
    ./gradlew rebuildPatches
    ```
6. **Verify clean patch round-trip & script integrity:**
    ```bash
    ./gradlew applyPatches
    bash scripts/test-patches.sh
    ./gradlew buildServerJar -x test
    ```
7. **Stage outer repository artifacts:**
    - Commit updated `.patch` files under `Antarctic-API/` and/or `Antarctic-Server/` (plus any `me/stella/antarctic/**` files if touched).
    - **Never** commit generated/proprietary files (`mc-dev/`, `libs/`, `.upstream/`, `work/`, `run/`, logs, or unpatched NMS dumps).
    - Target the `dev` branch (`main` is protected for production-verified snapshots).

### 3.1 Commit Hygiene & Message Convention
- **One change per commit:** Each logical change must be its own atomic commit — both in the inner source repositories (one patch per fix, optimization, or feature) and in the outer repository (export via `rebuildPatches` and commit each change individually rather than bundling multiple changes into one commit).
- Every commit, in the inner source repositories (its subject becomes the patch file name) and in the outer repository, uses the form `type: message`, e.g. `feat: add pure light engine`, `fix: keep deferred light replay in queue order`, `test: cover neighbour shapes in voxel parity`, `refactor: ...`, `docs: ...`, `chore: ...`.
- `type` is lowercase and one of `feat`, `fix`, `test`, `refactor`, `perf`, `docs`, `build`, `ci`, `chore`. `message` is lowercase, imperative, has no trailing period, and keeps the subject under about 72 characters.
- Put any detail in the commit body, not the subject. Keep the attribution lines the session requires.
- Reword a commit before `rebuildPatches` if its subject is wrong; the patch name follows the subject. Do not rewrite commits that are already pushed, unless the user asks.

---

## 4. Code & Formatting Conventions

- **Formatting (`.editorconfig`):** UTF-8 encoding, `LF` line endings, final newline required; 4-space indentation for `.java`, `.gradle`, and `.md`; 2-space indentation for `.yml`/`.yaml`/`.json`/`.xml`/`.properties`.
- **Minimal, Surgical NMS Diffs:** Keep edits inside `net.minecraft.server.*` and `org.bukkit.craftbukkit.*` compact and well-delimited so future patch rebases remain conflict-free. Place reusable self-contained data structures or math/spatial helpers under `me.stella.antarctic.*` and unit-test them directly.
- **Configuration Hygiene:** Register new server-wide toggles or tuning knobs as `IConfigModule` implementations in `com.destroystokyo.paper.config.toml.modules.*` wired into `AntarcticTomlConfig`, with `@ConfigInfo` comments stating behavior and default values.
