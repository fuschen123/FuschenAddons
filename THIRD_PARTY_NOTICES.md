# Third-party notices

## FishyAddons Thunder sound filter (GNU GPL version 3)

The Thunder Muter is adapted from **FishyAddons**, by **valkeea and contributors**,
under the GNU General Public License version 3. The GitHub source was inspected
directly on 2026-10-01 at commit
[`408916b56f236e180a50d5e38f706c5a09057b5c`](https://github.com/valkeea/FishyAddons/tree/408916b56f236e180a50d5e38f706c5a09057b5c).
No installed FishyAddons JAR was used.

Inspected source:

- [`SkyblockCleaner.java`](https://github.com/valkeea/FishyAddons/blob/408916b56f236e180a50d5e38f706c5a09057b5c/common/src/client/java/me/valkeea/fishyaddons/feature/skyblock/SkyblockCleaner.java):
  `thunderSound()`, `muteThunder()` and the relevant `shouldClean()` branch.
- [`MixinSoundSystem.java`](https://github.com/valkeea/FishyAddons/blob/408916b56f236e180a50d5e38f706c5a09057b5c/common/src/client/java/me/valkeea/fishyaddons/mixin/MixinSoundSystem.java):
  cancellable `SoundEngine.play(SoundInstance)` interception returning `NOT_STARTED`.
- [`GameMode.java`](https://github.com/valkeea/FishyAddons/blob/408916b56f236e180a50d5e38f706c5a09057b5c/common/src/client/java/me/valkeea/fishyaddons/api/skyblock/GameMode.java):
  inspected Hypixel address and SkyBlock sidebar gating.

Adapted files: `ThunderSoundFilter.java`, `ThunderMuter.java` and
`mixin/ThunderSoundMixin.java`. Changes: isolated and testable sound/timestamp state;
the central FuschenAddons tracker supplies actual living Thunder; a dedicated
persisted option; Minecraft 26.1.2 sound bindings; world/server reset and explicit
first-match state; host suffix validation for the SkyBlock gate. The original
`lightning_bolt`/`guardian` substring matching and 65-second tail are retained.
No other cleaner features or FishyAddons runtime dependencies are included.

The combined distribution is GPL-3.0-only. The full upstream GPL version 3 text is
in the root `LICENSE`, included in the binary and sources JAR. Complete buildable
source is provided by this repository and the source ZIP accompanying the binary.
The original FuschenAddons CC0 dedication is retained in
`src/main/resources/licenses/FuschenAddons-original-CC0.txt`; this notice does not
withdraw those original permissions or the other third-party licenses below.

## Cascade UI (BSD 3-Clause)

Cascade `2026.09.7+26.1` is bundled as a nested JAR. Copyright (c) 2025, Starred.
Source: https://github.com/skies-starred/cascade
Its full BSD 3-Clause license is also included at
`src/main/resources/licenses/Cascade-BSD-3-Clause.txt` (under `licenses/` in the binary).

## Feesh detection adaptation (Apache License 2.0)

Parts of the sea-creature recognition in FuschenAddons are adapted from
**Feesh 1.14.0**, by **MoonTheSadFisher and contributors**:
https://github.com/Sleepy-Panda/Feesh/tree/130d48915cad7f720cec6b57d2499253bdf1dcf8

The installed `Feesh-1.14.0+26.2-fabric.jar` was inspected and its relevant
classes decompiled with Vineflower 1.12.0. Its SHA-256 is
`0AD640996978E07F1CA4A48FB90370D8F81099325CF6CDF97FFF070A88C8AFC0`.
The matching upstream tag `1.14.0` was also read to clarify decompiled Kotlin.

Relevant original files under `src/main/kotlin/com/github/sleepypanda/feesh/`:

- `utils/EntityUtils.kt`: level/name/heart nametag parsing and hook ownership.
- `utils/CommonUtils.kt`: compact K/M/B number parsing.
- `utils/ChatUtils.kt`: inspected formatting handling; its styled-string visitor
  was not copied. FuschenAddons instead discards obfuscated component spans.
- `constants/SeaCreatures.kt` / compiled `SeaCreatureNames`: creature catalog.
- `settings/models/HpTrackableSeaCreatureTypes.kt`: extra recognized display names.
- `features/overlays/SeaCreatureHpTracker.kt` and its `MobDisplayInfo`: armor-stand
  HP observations and association to real mobs.
- `features/rendering/RareMobHighlight.kt`: entity-ID offsets, composite creature
  heads, Jawbus Follower/Fire Eel handling and exclusion of real player UUIDs.
- `utils/FishingHookUtils.kt` / `ActiveFishingHookInfo`: inspected hook state;
  the existing FuschenAddons owner check and `getHookedIn()` supply actual linkage.

Adapted files: `SeaCreatureNametag.java`, `SeaCreatureTracker.java`,
`feesh-sea-creatures.txt`, and the original Feesh nametag fixtures in
`SeaCreatureRecognitionTest.java`.

Changes: Java port targeting Minecraft 26.1.2; exact catalog filtering; validated
real-entity association; deduplication by UUID; world-local memory and a 40-tick
gap allowance; explicit unknown/partial HP instead of zero as an unknown sentinel;
no invented max HP; shared Thunder/HUD consumers. The single-target HUD selection
and hook recovery state machine are FuschenAddons additions. Feesh's multiple-row
HUD, immunity timers, settings, network functionality and runtime are not bundled.

These adapted portions remain available under Apache-2.0. The complete upstream
license is included at `licenses/Feesh-Apache-2.0.txt` in the binary and at
`src/main/resources/licenses/Feesh-Apache-2.0.txt` in the source distribution.
This notice does not replace the licenses of the remaining project or Cascade.
