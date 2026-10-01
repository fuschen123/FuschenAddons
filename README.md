# FuschenAddons 0.0.43

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
option, disconnecting or changing worlds cancels and pauses fishing. Failure to
find Thunder or a usable Hyperion also pauses. Resume with the fishing toggle.
Old-world camera/slot state is never applied to a new player or world. With the
option off, the previous rare-creature stop behavior remains.

## Mob attached to your hook

Only the actual `getHookedIn()` entity on a hook owned by your player can trigger
recovery. Nearby creatures alone do not. The unified sequence replaces the old
Water Snake and Magma Cube right-click sequences:

1. Verify the original rod/hand and a hotbar Hyperion, select Hyperion and click once.
2. Restore the original slot and rod hand (including an offhand rod).
3. Reel once if the original hook still exists; wait for it to disappear before
   casting once. If switching weapons already removed it, only cast. Leave a new,
   different hook alone.

Each hook and mob UUID is claimed once per world, including failed/cancelled
attempts. Rehooking the same mob cannot loop Hyperion/recast. A hook that survives
reeling times out after 40 ticks and pauses. Missing/moved items and menus also
abort safely and pause. After a failure, manually clear the stuck hook before
resuming. Thunder preempts recovery; fishing, pet, radar, flare and Grinch inputs
cannot overlap an active recovery. Grinch's configured left clicks can still run
on a consumed, still-attached encounter; normal right-click timers stay suppressed.
Water Snake armor-stand models are verified by their head texture, not a nearby label.

## Development and validation

Run `./gradlew clean build` with Java 25. The unit suite covers Feesh's actual
formatted nametag examples, partial/unknown health, identity deduplication and gaps,
multiple Thunder, radius exit/reentry, 5-CPS timing, missing items, single-shot hook
recovery, timeouts, encounter deduplication, dropdown boundaries and HUD coordinates.

Local dev-client smoke checks exercise actual mouse/keyboard screen handlers,
dropdown scrolling and closing, all four registered command paths, config reload,
HUD drag/save/resize, and Cascade rendering at GUI scales 1 and 2. This is not a
live Hypixel test. Before relying on the automation in game, verify:

- Live Thunder nametag/entity pairing, aiming, multiple nearby Thunder, packet gaps,
  radius exit/reentry, wand cooldowns and normal camera/slot restoration.
- Main/offhand rods, Water Snake/Magma Cube hook linkage, disappearing/stubborn hooks,
  missing items and Thunder interrupting recovery.
- Cancellation on menus, death, option disable, toggle and world changes; existing
  pet, flare, Hoppity, Grinch and movement behavior under real server timing.
