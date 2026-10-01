# FuschenAddons 0.0.45

Client-side fishing helper by fuschen. Built for **Minecraft 26.1.2, Java 25,
Fabric Loader >= 0.19.5 and Fabric API**. Cascade `2026.09.7+26.1` is bundled.
**Fabric Language Kotlin >= 1.13.13+kotlin.2.4.10 is installed separately.**
Feesh is not a runtime dependency. The inspected Feesh build targets 26.2;
this project retains its existing Minecraft 26.1.2 target.

## Configuration and HUD

Open configuration with **P**, `/fa` or `/fuschen`. Action Weapon, Flare and
Fishing Pet now use dropdowns. Click a row to select; Escape or an outside click
closes the popup. Arrow keys, Home/End and Enter also work. The popup scrolls
independently of the page and opens above/below its control to fit the screen.
Values save automatically in `config/fuschenaddons.json`; old settings are retained.
The fishing keybind is now labelled **Start/Stop FishHelper**. Its binding and the
mod's name are unchanged.

**General -> Sea Creature healthbar** controls the overlay. **Edit HUD**,
`/fa gui` and `/fuschen gui` open its drag editor. Drag with the left mouse button;
release or Escape saves the position. The editor shows explicitly labelled sample
values when no creature is available, including when the overlay is disabled.
Positions use normalized available travel distance, so resizing retains a reachable bar.

The HUD initially selects the nearest recognized living creature within 30 blocks
(UUID breaks ties), then keeps that creature until it dies, leaves this range, or
its identity expires. A two-second entity gap retains the selection and displays
unknown health. This prevents rapid switching between several nearby creatures.
HP comes only from actual nametag values. Missing maximum HP is labelled unknown
and does not produce a made-up percentage or a vanilla-health fallback.

## Shared sea-creature recognition

Recognition is adapted from the inspected **Feesh 1.14.0+26.2** JAR and matching
upstream source. See [third-party notices](THIRD_PARTY_NOTICES.md) for source
classes, exact commit, SHA-256, adaptation details and Apache-2.0 license.

Level/name/heart armor stands are parsed every five client ticks. Names must match
the catalog. Feesh's entity-ID offsets associate the display with the actual mob,
including its special composite-mob offsets. Armor stands and ordinary real-player
UUIDs cannot become mob targets; Thunder specifically requires an Elder Guardian.
Records are deduplicated by the actual entity UUID. A live, loaded mob retains its
recognized identity during name gaps; health becomes unknown after 40 ticks
without an observation. An absent entity gets a 40-tick grace period. A confirmed
entity death or reported zero HP removes it immediately. Changing worlds clears
all recognition state. Server changes to the name/ID scheme need in-game verification.

## Thunder response

Enable **Thunder -> Thunder response** (off by default). Your exact Thunder spawn
message interrupts fishing/pet/flare/recovery actions and waits up to three seconds
for a recognized Thunder. Recognized Thunder within 32 blocks join the encounter;
once joined, a loaded living Thunder remains relevant even if it moves farther away.

1. Aim at the actual Thunder nearest the cast position and use Ice Spray Wand once,
   if present in the hotbar.
2. Aim again and use Ink Wand once, if available. Missing wands are skipped independently.
3. Select Hyperion and look down. Right-click once every four client ticks
   (5 CPS at 20 TPS) while at least one encounter Thunder is within five blocks in 3D.
   Outside this attack radius clicks pause; they resume on reentry.
4. Finish only when all encounter Thunder are dead or have expired from detection.
   Restore the original view and slot and resume fishing. A short recognition gap
   or the death of only one of several Thunder cannot finish the encounter.

The fishing toggle cancels immediately. Opening a menu, death, disabling this
option or missing Hyperion ends this action while keeping fishing enabled. Fishing
continues automatically once prerequisites are available. Only a server/world
change automatically switches fishing off; activate it manually in the new world.
Old-world camera/slot state is never applied to a new player or world. With the
option off, other rare-creature messages briefly select the configured action weapon
and then allow fishing to continue.

## Lord Jawbus fishing pause

FishHelper pauses its automatic actions immediately on your exact Jawbus spawn
message, or when the shared tracker recognizes a Lord Jawbus within 32 blocks of
you or your fishing hook. This is a temporary action pause: FishHelper stays enabled.
Every queued catch, pet, flare, radar, recast, mob recovery and Thunder action is
cancelled. Neither the five-second watchdog nor an attached Water Snake/Magma Cube
can issue clicks during this pause. New chat-triggered actions are blocked too.

Own spawn messages reserve up to 200 client ticks (10 seconds at 20 TPS) for the
entity/nametag to arrive; multiple pending spawns are tracked separately. Once a
Jawbus is acquired, its living UUID stays relevant even outside the acquisition or
attack radius and during nametag gaps. Additional nearby Jawbus join the encounter.
Confirmed death or a zero-HP nametag removes that mob; an unloaded/absent mob must
remain absent for 100 consecutive client ticks (five seconds). A returning UUID,
including one with a new entity ID, cancels that absence countdown. A live loaded
Jawbus is never released solely because a timer elapsed or its range changed.

The pause ends only when all tracked Jawbus and pending spawn waits have cleared.
Fishing action state and the watchdog are reset before normal processing resumes;
other menu/Hoppity waits still apply. There is at most one start and one end message
per pause. Manual OFF or server/world change clears the encounter and takes priority;
ending a Jawbus pause never enables FishHelper. No new toggle is required.

## Thunder Muter

**Thunder -> Thunder Muter** is a separate, saved toggle, off by default. It works
even while FishHelper is off. Its sound filter is adapted directly from FishyAddons'
GitHub source; FishyAddons is not a runtime dependency. Attribution, pinned source
links and the GPL-3.0 license are in [third-party notices](THIRD_PARTY_NOTICES.md).

Filtering requires a Hypixel server address and a SkyBlock sidebar title. Paths
containing `lightning_bolt` or `guardian` are muted while the shared tracker has an
actual living Thunder. Each matching sound during that encounter refreshes a
65-second tail. Sounds during the tail do not extend it. World/server changes reset
the tail. Other sounds, creature recognition and combat actions are unaffected.

## Five-second idle watchdog and automatic continuation

While enabled, 100 actionable idle client ticks trigger the same controlled
reel/release/cast sequence used by normal fishing. Normal bite waiting in water or
lava, a valid nearby hook countdown and the configured Slugfish timing are exempt.
Menus, Hoppity, Thunder, catch/weapon/pet/flare actions and active recovery have
exclusive control; the watchdog cannot add clicks or slot changes during them.

Normal action/radar phases have a 100-tick deadline that repeated timer updates
cannot extend. Stuck phases restore the rod and enter controlled recovery. Hoppity
prompt/window arrival waits expire after 200 ticks; an open menu still blocks
fishing until closed. Thunder's search and recognition-gap handling remain bounded;
waiting for a live encounter Thunder to return within attack range is intentional.

Missing rods or prerequisites leave the helper enabled, without click spam. It
rechecks availability and resumes automatically. Failed recovery attempts clear
their action state and impose a 100-tick retry delay. Repeated attempts alone do
not count as progress; confirmed hook changes and completed recasts do. A confirmed
recast starts a fresh five-second idle window. Messages are deduplicated per problem
state. Hotspot disappearance and rare-creature handling no longer require toggling.
Manual off and world/server-change off cannot be undone by the watchdog.

## Mob attached to your hook

Only the actual `getHookedIn()` entity on a hook owned by your player can trigger
recovery. Nearby creatures alone do not. The unified sequence replaces the old
Water Snake and Magma Cube right-click sequences:

1. Find the rod and a hotbar Hyperion, select Hyperion and click once.
2. Re-resolve the original rod, including a moved hotbar slot or offhand rod.
3. Reel once if the original hook still exists; wait for it to disappear before
   casting once. If switching weapons already removed it, only cast. Leave a new,
   different hook alone.

Each hook and mob UUID consumes its Hyperion action only after the click is issued;
missing prerequisites do not consume it. Rehooking the same mob cannot loop Hyperion.
Reeling is also recorded per hook UUID, so a timed-out attempt cannot repeatedly
toggle the rod on an unacknowledged hook. Release and cast confirmation each wait
up to 100 ticks, then automatically retry verification after a backoff. A still-live
old hook is never forcibly removed; casting waits until its actual release.
Thunder preempts recovery; fishing, pet, radar, flare and Grinch inputs cannot overlap
an active recovery. Grinch's configured left clicks can still run between retries
on a consumed, still-attached encounter; normal right-click timers stay suppressed.
Water Snake armor-stand models are verified by their head texture, not a nearby label.

The old `missing or hook not released; toggle to resume` path combined stale
slot/hand snapshots, a short hook-release timeout and a persistent pause. Recovery
now distinguishes missing items, release timeout and cast confirmation. The live
owned hook in the world registry is authoritative. A stale `player.fishing` pointer
is repaired from that entity or cleared only after four consecutive ticks with no
live owned hook. A new cast additionally waits for stable absence; a different new
hook is left alone. This repairs the state instead of requiring an off/on toggle.

## Development and validation

Run `./gradlew clean build` with Java 25. The 62-test unit suite covers Feesh's actual
formatted nametag examples, partial/unknown health, identity deduplication and gaps,
multiple Thunder, radius exit/reentry, 5-CPS timing, missing items, single-shot hook
recovery, delayed release, retry limits, watchdog exemptions/preemption, the exact
65-second sound tail, encounter deduplication, dropdown boundaries and HUD coordinates.
Jawbus tests cover delayed/multiple spawn signals, multiple mobs, radius exit,
confirmed death, short/long absence, entity-ID replacement and world/reset cleanup.

Run `./gradlew -I smoke.gradle runClient --no-configuration-cache` for the optional
local dev-client smoke suite. It creates a disposable creative world under ignored
`run/saves`, writes screenshots to `build/smoke-captures/screenshots` and fails the
task unless `build/smoke-result.txt` reports success. The harness is excluded from
normal builds. After a smoke run, use `./gradlew clean build` for distribution.

The smoke checks exercise actual mouse/keyboard screen handlers,
dropdown scrolling and closing, all four registered command paths, config reload,
HUD drag/save/resize, and Cascade rendering at GUI scales 1 and 2. A local world with
synthetic client entities and accelerated ticks checks rod slot/offhand moves,
stale hook reconciliation, missing-rod return, idle/legitimate waits, action timeouts,
menu continuation, queued flare preservation, manual/world off and Thunder mapping.
The Muter toggle persists and rejects non-SkyBlock sound in that client. Pure logic
tests cover its positive filtering and tail. These are not live Hypixel tests.
Additional Jawbus client fixtures exercise immediate cancellation during a pending
recast and normal catch, attached-mob recovery exclusion, nametag/range/entity gaps,
multiple mobs, zero-HP death, Hoppity/menu waits, a fresh watchdog window, and
manual/world OFF.
Still verify on the server:

- Live Thunder nametag/entity pairing, aiming, multiple nearby Thunder, packet gaps,
  radius exit/reentry, wand cooldowns and normal camera/slot restoration.
- Main/offhand rods, Water Snake/Magma Cube hook linkage, disappearing/stubborn hooks,
  missing items and Thunder interrupting recovery.
- Cancellation on menus, death, option disable, toggle and world changes; existing
  pet, flare, Hoppity, Grinch and movement behavior under real server timing.
- Muting with a real SkyBlock sidebar, live Thunder sounds and the 65-second tail.
- Real Jawbus spawn/nametag packet ordering, kills and unloads during fishing/recast,
  multiple simultaneous Jawbus and automatic fishing continuation after the fight.

## License

This combined version is distributed under **GPL-3.0-only** because it incorporates
the FishyAddons sound filter. The complete license is in `LICENSE`. The original
FuschenAddons CC0 dedication is preserved in
`src/main/resources/licenses/FuschenAddons-original-CC0.txt`; the Feesh Apache-2.0
and Cascade BSD-3-Clause notices remain intact. Complete buildable source, including
Gradle scripts and tests, is available from this repository and the source ZIP
delivered alongside the JAR.
