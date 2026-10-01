# Third-party notices

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
