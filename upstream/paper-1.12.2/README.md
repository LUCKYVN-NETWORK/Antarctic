# Vendored upstream: Paper 1.12.2

This directory preserves the **Paper 1.12.2** base that Antarctic forks from,
vendored verbatim from the local `Paper-archive` checkout:

- commit `e39995d213616cc7d60c38e6e86e562f30637ef7`
  (*"Update Log4J (1.12.2) (#7154)"*, 2021-12-19 — the final 1.12.2 maintenance commit)

## Contents

| Path | Purpose |
|------|---------|
| `Spigot-API-Patches/` | Paper's API patches, applied on top of Spigot-API (134 patches) |
| `Spigot-Server-Patches/` | Paper's server patches, applied on top of Spigot-Server (385 patches) |
| `scripts/` | The original bash patch/remap/decompile/build pipeline |
| `paper` | The original `./paper` build tool |
| `pom.xml` | The original Maven parent POM |
| `.gitmodules` | Original submodule layout (inert reference copy) |
| `README.upstream.md` | Original Paper README |
| `CONTRIBUTING.upstream.md` | Original Paper contributing guide |

## How the original build worked

Paper 1.12.2 was a **patch pipeline**, not a plain source tree:

1. `git submodule update --init` — clones Bukkit, CraftBukkit, BuildData,
   Spigot and Paperclip (see `.gitmodules`)
2. `scripts/remap.sh` — remaps the obfuscated vanilla server to Spigot names
3. `scripts/decompile.sh` — decompiles the remapped server
4. `scripts/applyPatches.sh` — applies CraftBukkit → Spigot → Paper patches,
   producing the `Paper-API` / `Paper-Server` source trees
5. `mvn clean install` + `scripts/paperclip.sh` — builds the paperclip jar

The pipeline needs bash, git and network access to the SpigotMC stash; on
Windows it is meant to run under WSL / git-bash.

## Role in Antarctic

Antarctic vendors the *output* of this pipeline directly into `Antarctic-API/`
and `Antarctic-Server/` as plain sources (no more patch-rebase workflow) and
modernises them to Java 21. This directory stays frozen as provenance and
bootstrapping reference.
