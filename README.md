# Leashis

> **Notice: This is a very, very AI slop project. Most of its functionality was implemented by ChatGPT.**

A **server-only** plugin and mod that lets you leash other players.
Bukkit (CraftBukkit), Spigot, Paper, Fabric, and NeoForge share the same connection rules and pulling engine.
No client mods or resource packs are required. An invisible vanilla baby turtle provides the leash rendering;
the project registers no new items, entity types, or required custom network channels.

## Supported versions

| Distribution | Minecraft versions | Installation directory | Additional dependencies |
| --- | --- | --- | --- |
| Shared Bukkit plugin | 1.20–26.3 | `plugins/` | None |
| Fabric | 1.20–26.3, version-specific JARs | `mods/` | Fabric Loader and the matching Fabric API |
| NeoForge | 1.20.2–26.3, version-specific JARs | `mods/` | The matching NeoForge version |

See [versions.json](versions.json) for the exact patch versions and pinned build dependencies.
NeoForge does not exist for Minecraft 1.20, and the legacy Forge-compatible NeoForge release for 1.20.1 is excluded from these distributions.
Some Minecraft versions only have beta NeoForge releases, so check the loader versions in the version table too.
The [validation record](docs/VALIDATION.md) distinguishes build verification from runtime testing.

## Installation

- Bukkit, Spigot, or Paper: put `leashis-bukkit-2.0.0.jar` in `plugins/`.
- Fabric: put `leashis-fabric-<MC>-2.0.0.jar` and Fabric API matching your server version in `mods/`.
- NeoForge: put `leashis-neoforge-<MC>-2.0.0.jar` matching your server version in `mods/`.

Install only one Leashis JAR for your server's platform.
The server runtime requires Java 17 or newer for 1.20–1.20.4, Java 21 or newer for 1.20.5–1.21.11,
and Java 25 or newer for 26.x. Also follow your server distribution's Java requirements.

## Usage

1. Hold a lead and right-click another player to attach it. Both hands are supported.
2. After at least one second, right-click the same player again to release them. This also works with an empty hand.
3. Hold a lead and right-click a fence to transfer the connection to a fence knot.

By default, pulling toward the holder starts at 4 blocks, and the leash breaks beyond 10 blocks.
In survival mode, a successful connection consumes one lead and returns it once near the target when the connection ends.
Connections started in creative mode neither consume nor return leads, even if the player's game mode changes afterward.
You can leash multiple players at once, but you cannot take over an already leashed player.

Leashed players are blocked from right-clicking or directly attacking fence knots and from changing dimensions through portals or commands.
Connections are cleaned up on death, logout, riding, spectator mode, disabling the feature, or a holder changing dimensions.
A normal server shutdown returns consumed leads; connections do not survive a restart.
Proxy entities are not saved to the world. Bukkit also removes stale marked proxies from loaded chunks.

## Configuration

| Platform | Configuration file | How to apply changes |
| --- | --- | --- |
| Bukkit family | `plugins/Leashis/config.yml` | `/leashis reload` (OP by default), or restart |
| Fabric | `config/leashis-server.properties` | Restart |
| NeoForge | `config/leashis-server.toml` | Loader configuration reload, or restart |

On older NeoForge versions using SERVER configuration, the world's `serverconfig/` settings may take precedence.
The LOCAL configuration used in 26.3 is read from the server's `config/` directory.

Configuration meanings and defaults are shared across platforms.

| Key | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Enable the feature |
| `distanceMin` | `4` | Distance at which pulling starts (0–1024) |
| `distanceMax` | `10` | Distance beyond which the leash breaks (1–1024) |
| `allowLeashedRemoveFenceKnot` | `false` | Allow leashed players to remove fence knots |

If the minimum distance is greater than or equal to the maximum, the effective pulling threshold is capped at maximum distance minus 1.
Fabric's `.properties` file uses `key=value` entries, such as `enabled=true`.

## Building

You need JDK 21 or newer to run Gradle (JDK 25 is recommended for the full version matrix) and Python 3.
Gradle toolchains select Java 17, 21, or 25 for compilation and download them when needed.

```sh
# Default: Bukkit + Fabric 1.21.1 + NeoForge 1.21.1
./gradlew build

# Select a Minecraft version and platform
./gradlew -Pmc=26.3 -Pplatforms=fabric,neoforge build
./gradlew -Pplatforms=bukkit :bukkit:build
./gradlew -Pmc=1.20 -Pplatforms=fabric :fabric:build

# Build every supported version and record verification results
python3 tools/build_matrix.py --loader fabric
python3 tools/build_matrix.py --loader neoforge
python3 tools/package_release.py
```

JARs are generated in `bukkit/build/libs/`, `fabric/build/<MC>/libs/`, and `neoforge/build/<MC>/libs/`.
Distribution bundles are generated in `dist/` and contain platform-specific JARs, checksums, and the version table.
The packaging tool stops if any build failed or a required JAR is missing.

## Validation

```sh
./gradlew -Pplatforms=core :core:check
./gradlew -Pplatforms=neoforge :neoforge:runGameTestServer

# Start isolated development servers (creates a development EULA file and localhost settings)
python3 tools/smoke_server.py --loader fabric --mc 26.3
python3 tools/smoke_server.py --loader neoforge --mc 1.20.2

# Integration tests using two vanilla protocol clients
npm ci --prefix tests/integration
python3 tools/smoke_server.py --loader fabric --mc 1.20.1 \
  --client-script tests/integration/regression.cjs
```

Integration tests cover lead consumption, duplicate clicks, leash display packets, refunds, creative mode, pulling, distance-based release, fence attachment, knot protection, dimension travel blocking, and logout cleanup.
Protocol tests do not check rendered leash appearance, so manual visual checks with two real clients are listed separately in the validation record.
Test code and test worlds are excluded from distribution JARs.

## Project structure

- `core/`: platform-independent state management, item refund ownership, and pulling calculations.
- `bukkit/`: shared plugin written against the Bukkit 1.20 API.
- `mod-common/`: Minecraft adapter and proxy shared by Fabric and NeoForge.
- `fabric/`: Fabric events, server configuration, and dimension travel mixins.
- `neoforge/`: NeoForge events and configuration, with legacy NeoGradle and modern ModDevGradle builds.
- `tools/generate_mod_sources.py`: adapts internal API differences between versions at compile time.
- `versions.json`: single source of supported versions and dependencies, also used by CI.

## Reference

The vanilla proxy approach and multiplatform structure were inspired by [pog5/mc-leashplayers](https://github.com/pog5/mc-leashplayers).
The reference project's MIT notice is preserved in `licenses/mc-leashplayers-MIT.txt`.
The original NeoForge MDK's `TEMPLATE_LICENSE.txt` is also preserved.

## Development acknowledgment

Most of the functionality in this project was implemented by ChatGPT.
