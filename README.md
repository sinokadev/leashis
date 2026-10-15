# Leashis

> **Notice: This is a very, very AI slop project. ChatGPT implemented this project from start to finish. The human only provided instructions.**

This is a casual, prompt-and-click AI slop project I had ChatGPT build for me and my friends.
Issues and pull requests are welcome. Please keep feedback constructive and focused on specific bugs, improvements, or licensing concerns.
Please do not insult the project or its contributors, or complain solely because it is AI slop.

A **server-only** plugin and mod that lets you leash other players.
Bukkit (CraftBukkit), Spigot, Paper, Fabric, and NeoForge share the same connection rules and pulling engine.
No client mods or resource packs are required. An invisible vanilla baby rabbit provides the leash rendering;
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

- Bukkit, Spigot, or Paper: put `leashis-bukkit-2.0.5.jar` in `plugins/`.
- Fabric: put `leashis-fabric-<MC>-2.0.5.jar` and Fabric API matching your server version in `mods/`.
- NeoForge: put `leashis-neoforge-<MC>-2.0.5.jar` matching your server version in `mods/`. The Minecraft 1.21.1 build supports NeoForge 21.1.257 or newer.

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

Arrows and other projectiles cannot destroy a fence knot while it holds a player, including while that player is offline or waiting to respawn. Empty knots keep their normal projectile behavior.
Players tied to a fence cannot break that fence block, including in creative mode. Occupied fence anchors cannot be moved by pistons, sticky pistons, or slime/honey structures, including while a target is waiting to respawn or their disconnected leash is retained. Once all player connections to that fence are released, it can move again. This block protection applies even when `allowLeashedRemoveFenceKnot` is enabled; unrelated fences and other players are unaffected.
Leashed players are blocked from right-clicking or directly attacking fence knots and from changing dimensions through portals or commands.
By default, a leashed player reconnecting to the same running server keeps their connection, including fence attachments. The proxy is recreated on login without consuming or refunding another lead. Player-held connections are cleaned up on death. Other cleanup reasons include holder logout, riding, spectator mode, disabling the feature, or a holder changing dimensions. Server restarts clear connections.
With `keepOnDeath=true` (default), when a player tied to a fence dies, the connection is retained without refunding or consuming another lead. Respawning returns them near that same fence and restores the leash, even if their saved spawn is in another dimension. This death behavior applies independently of `keepOnLogout`. The player's bed, respawn anchor, and saved spawn are not changed: after release, death uses the normal saved spawn again. If the fence anchor is removed before respawn, its connection is released and normal spawning applies.
A normal server shutdown returns consumed leads; connections do not survive a restart.
Proxy entities are not saved to the world. Bukkit also removes stale marked proxies from loaded chunks.

## Pulling players closer

Use `/l` or `/leashis pull` to reel in all players whose leashes you are holding. It pulls along the current rope direction toward you, regardless of where you are looking, and eases to a stop about one block away. It uses velocity rather than teleportation, so blocks still obstruct movement. Each pull lasts up to three seconds, with a one-second cooldown shared by both commands. Releasing or transferring a leash to a fence cancels its reel, and offline or dead targets are excluded.

Pulling is available to ordinary players. Bukkit servers can control it with `leashis.pull` (default: everyone); configuration commands still require `leashis.admin`. On mods, configuration commands still require OP level 2. If another mod already owns `/l`, use `/leashis pull`.

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
| `keepOnLogout` | `true` | Keep a leashed target's connection across reconnects to the same running server |
| `keepOnDeath` | `true` | Keep fence leashes on death and respawn near the fence |

Administrators (OP level 2 on mods, `leashis.admin` on Bukkit) and the server console can use these commands on every platform:

- `/leashis reconnect`: show the current reconnect policy.
- `/leashis reconnect true`: keep the target's leash across reconnects (default).
- `/leashis reconnect false`: release the target on logout and refund a consumed lead.
- `/leashis death`: show the current fence death policy.
- `/leashis death true`: keep fence leashes on death and respawn near the fence (default).
- `/leashis death false`: release fence leashes on death, refund a consumed lead, and use the normal saved spawn.

Changes apply immediately and are saved to the platform's configuration file. Turning reconnect retention off also releases targets who are already offline. Turning death retention off releases connections already waiting for respawn; active connections remain until death or another release condition. Holder logout still releases their targets in either mode. Server restarts clear connections in either mode.

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

Integration tests cover lead consumption, duplicate clicks, leash display packets, refunds, creative mode, pulling, distance-based release, fence attachment, knot protection, dimension travel blocking, target reconnects (player and fence holders), and holder logout cleanup.
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

ChatGPT implemented this project from start to finish. The human only provided instructions.

## License

Except for third-party material, this project is released under [The Unlicense](LICENSE).
It was inspired by the MIT-licensed [pog5/mc-leashplayers](https://github.com/pog5/mc-leashplayers) project and uses the NeoForge MDK template.
The exact boundaries between original implementation and any code copied or adapted from these sources have not been fully audited.
Do not assume that every file or line is covered exclusively by the Unlicense.

Any third-party MIT material remains subject to its applicable copyright and permission notices; the Unlicense does not remove those requirements.
The original notices are preserved in `licenses/mc-leashplayers-MIT.txt` and `TEMPLATE_LICENSE.txt`.
If you reuse, modify, or redistribute this project's source code, review the relevant origins and licenses, preserve applicable notices, and take care to meet your legal obligations.
This notice is not a guarantee that every licensing issue has been identified or resolved.
