FishHelper by fuschen

## Configuration

Open the settings with **P**, `/fa`, or `/fuschen`. The Cascade-rendered menu has
Fishing, Thunder, and General tabs. Use the mouse wheel or arrow buttons to reach
additional settings at smaller GUI sizes. Controls support keyboard navigation;
hover a setting to read its full description. Valid edits save automatically.
All existing settings and the `fuschenaddons.json` file are retained.

Cascade `2026.09.7+26.1` and Fabric Language Kotlin are bundled in the mod JAR.
Minecraft 26.1.2, Java 25, and Fabric API are still required.

## Thunder response

Enable **Thunder → Thunder response** (off by default). While fishing is enabled
and unpaused, the exact Thunder spawn message starts this sequence:

1. Cancel the current fishing/pet/flare/recast action and wait up to 3 seconds for
   the Elder Guardian entity to arrive. Prefer a Thunder-named Elder Guardian;
   otherwise use the one closest to the cast position, within 32 blocks of you.
2. Aim at it, select Ice Spray Wand from the hotbar, and right-click once.
3. Aim again, select Ink Wand, and right-click once. Missing wands are skipped
   independently; hotbar items are checked again before use.
4. Select Hyperion, look at the floor, and right-click every 4 client ticks
   (5 times/second at 20 TPS) while any living Elder Guardian is within a true
   5-block radius, including vertical distance. No Hyperion means no attack clicks.
5. Restore the previous view and fishing slot, then resume fishing. Pressing the
   fishing toggle cancels immediately. Opening a screen, dying, disabling the
   option, disconnecting, or changing worlds cancels the encounter; resume with
   the fishing toggle when ready. Old world state is never restored into a new world.

The trigger is your server spawn message, not the presence of an arbitrary
Elder Guardian. Disabling the option keeps the previous rare-creature behavior.

## Development and validation

Run `./gradlew build` with Java 25. Unit tests cover the Thunder sequence's order,
missing/moved items, delayed spawn/timeout, target loss, radius boundary, click
interval, and cancellation. In-game validation is still needed for server timing
and the rendered UI:

- Open each settings tab at different window sizes and GUI scales; scroll, tab
  between controls, edit numbers, rebind the toggle, close/reopen, and check persistence.
- Trigger Thunder with all three items, each wand missing, and Hyperion missing.
- Check camera aim, wand use order, and Hyperion stopping at death or just beyond
  five blocks (including a guardian above/below the player).
- Cancel using the fishing toggle, a menu, option disable, death, disconnect, and
  world change. Confirm no delayed wand/fishing action runs afterward.
- Verify the old fishing, pet, flare, Grinch, Water Snake, Hoppity, and movement
  options with Thunder response disabled.



