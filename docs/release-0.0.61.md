# FuschenAddons 0.0.61 — Minecraft version matrix

## Source baseline

Fetched and integrated `origin/main` at
`62c9ca85fcdbf7726bbf48b285607ef3ad215f99` before editing. No existing local changes
were discarded. All three variants use the same current feature sources, not old JARs.

## Changes

- Separate builds for Minecraft 26.1.2, 26.2 and 26.3 with exact target metadata,
  matching Fabric API/Cascade dependencies and isolated build/dev-client directories.
- Small compile-time bridges for the GUI split in 26.2, moved slime classes,
  SDL keyboard/mouse input in 26.3 and the 26.3 swing animation API.
- GUI navigation, HUD dragging, keyboard and mouse binds use Minecraft input constants.
  Existing named bindings/settings are retained; legacy GLFW toggle codes are translated.
  SDL cannot represent GLFW F25/raw scancode bindings: they stay saved and inactive on
  26.3 until explicitly rebound, rather than silently selecting a different key.
- Shared FishHelper/recast/hook recovery, Thunder (no rotation, inclusive six-block
  range, existing 5 CPS and 6M Chimera switch), Jawbus pause/Shuriken/cocoon,
  pet swap/favorites, flares, slots, HUDs, English Cascade GUI and Thunder Muter remain.
- Three-target GitHub Actions build/test matrix and permanent AGENTS.md rules.

## Required dependencies

Use Java 25 and Fabric Loader 0.19.5 or newer. Install Fabric API and
Fabric Language Kotlin 1.13.13+kotlin.2.4.10 or newer separately.
Kotlin compiler/runtime baseline: 2.4.10. Cascade is bundled; Feesh is not required.
Install only the FuschenAddons JAR for the exact Minecraft version.

| Minecraft | Fabric API tested / minimum declared | Bundled Cascade |
| --- | --- | --- |
| 26.1.2 | 0.155.3+26.1.2 | 2026.09.7+26.1 |
| 26.2 | 0.161.0+26.2 | 2026.09.7+26.2 |
| 26.3 | 0.162.0+26.3 | 2026.09.7+26.3 |

Dependency availability was checked against Mojang's release manifest, Fabric's
Loader/API/Kotlin metadata and Cascade's Maven metadata/POMs, then validated by
compilation and actual local client launches. Minecraft 26.x is unobfuscated;
no external Yarn mapping layer is used. Gradle Loom is pinned to 1.18.4.

## Reproduce

With Java 25, run each command for `26.1.2`, `26.2`, and `26.3`:

```sh
./gradlew '-Pminecraft_version=26.1.2' clean build
./gradlew '-Pminecraft_version=26.1.2' -I smoke.gradle runClient --no-configuration-cache
./gradlew '-Pminecraft_version=26.1.2' clean build
```

The final clean build removes the optional smoke entrypoint/classes from distribution.
JARs are under `build/<minecraft>/libs`. Local smoke results/screenshots are under
`build/<minecraft>/smoke`; dev worlds/configs remain isolated in `run/<minecraft>`.
The smoke harness does not connect to Hypixel or modify a launcher installation.

## Tests and remaining limits

Executed on Windows with Java 25 and an NVIDIA RTX 5070 Ti on 2026-10-11:

| Check | 26.1.2 | 26.2 | 26.3 |
| --- | --- | --- | --- |
| Separate clean release build | PASS | PASS | PASS |
| JUnit | 96 passed | 96 passed | 96 passed |
| Actual client initialization + complete local smoke suite | PASS | PASS | PASS |
| Cascade GUI/HUD screenshots inspected | PASS | PASS | PASS |
| Keyboard/mouse, saved config and legacy bindings | PASS | PASS | PASS |
| Real SoundEngine mixin injection + non-SkyBlock exclusion | PASS | PASS | PASS |
| Release metadata/nested Cascade/no smoke classes | PASS | PASS | PASS |

No JUnit failures, errors or skipped tests (288 total executions).
The client smoke suite exercised real screen input/rendering and a local creative
world with synthetic mobs: pet catalog/favorites, key capture/reload, HUD movement,
rod slots/offhand, recast/failsafe, hook recovery, flares, movement, Thunder
range/reentry/no rotation/weapon selection, cocoon recognition, Jawbus pause and
Shuriken unknown/missing/marked states, multiple entities, gaps, death and resets.
Sound filtering positive cases and the 65-second tail passed the unit suite;
audible filtering in a live Hypixel session was not tested. The sound injection
was independently verified on the actual SoundEngine class of every version.
The final clean builds excluded all smoke classes/entrypoints. Matching Cascade
nested JARs and exact Minecraft metadata were inspected in all three binaries.

Live Hypixel tests are
still required for real network timing, nametag association/cocoon release, wand
cooldowns, manual aiming and enchantment damage, real pet menus/profile data,
Shuriken status updates and audible Thunder suppression on the server. Synthetic
local entities and accelerated ticks cannot certify server behavior or compatibility
with every third-party modpack. No multiplayer actions were sent during testing.