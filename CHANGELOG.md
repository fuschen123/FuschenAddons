# Changelog

## 0.0.57

### Changed

- Fit seven configuration rows on a page by tightening the row spacing.

## 0.0.56

### Fixed

- Resolve CI artifact upload paths from the current project name so version bumps do not leave stale filenames.

## 0.0.55

### Fixed

- Wait for a live fishing hook before opening the Pets menu, and close it if the hook disappears during pet handling.

## 0.0.54

### Added

- Add an option to close any open menu when it is time to reel, including the auto-opened Pets menu.

## 0.0.53

### Changed

- Retry pet summon after 20 ticks if the tooltip still says “Left-click to summon!”.
- Close the Pets menu immediately when the selected pet tooltip changes to “Click to despawn!”.

## 0.0.51

### Fixed

- Prevent the Pets menu from remaining open indefinitely after the selected pet is already equipped or the server fails to close the menu.
- Click the selected pet only while its tooltip says “Left-click to summon!”, and stop once it says “Click to despawn!”.

## 0.0.52

### Changed

- Recheck the selected pet's tooltip every two seconds while waiting for the summon to complete.

## 0.0.50

### Changed

- Do not manually animate the hand when right-clicking Hyperion, including during Thunder encounters.
- Recast the fishing rod immediately after the Thunder encounter ends because Thunder has died.

## 0.0.49

### Changed

- Use Hyperion on the fifth tick after a bobber hooks a mob, bypassing the rare-creature and recovery retry delays for that hook.
- Let the server close the Pets menu after a successful pet equip; retry the click after one second only when the pet is not equipped.

## 0.0.48

### Changed

- Keep the Pets menu open while checking the configured pet every second, retrying the equip click until the tooltip confirms it is equipped.

## 0.0.47

### Changed

- Use Hyperion 4–6 ticks after any mob is attached to the owned fishing bobber, with no added delay after that timing window.

## 0.0.46

### Changed

- Remove all automatic camera aiming, downward looking, and view restoration from the Thunder response sequence. Target detection and item use continue without changing the camera.

## 0.0.42

### Fixed

- Access Minecraft's pause-on-lost-focus setting through `client.options`, matching the 26.1.2 API.

## 0.0.41

### Fixed

- Detect the Water Snake armor-stand head shown in the supplied entity data by its skin texture, in addition to its name.

## 0.0.40

### Added

- Recast the fishing rod when its bobber is hooked to an entity named Water Snake, with a two-tick delay between reeling in and casting again.

## 0.0.39

### Changed

- Keep random movement running while Minecraft is unfocused by temporarily disabling pause-on-lost-focus while the feature is active; restore the previous setting when stopped.

## 0.0.38

### Fixed

- Use the supported window handle API when restoring a physically held mouse-bound movement key.

## 0.0.37

### Fixed

- Preserve ordinary movement controls while random movement is inactive, and restore physically held movement keys when the feature stops or pauses for a menu.

## 0.0.36

### Added

- Add a configurable random movement feature. Enable it in the config and toggle it with the K keybind (rebindable in Controls); it holds S and Shift while randomly strafing with A or D every 3–7 ticks.

### Changed

- Suspend random movement and release its movement keys whenever a screen or menu is open.
- Extract the Grinch auto-clicker and movement behavior into separate feature classes, with a shared keybinding category.

## 0.0.35

### Added

- Select Hyperion, Soul Whip, or Flaming Flay for fishing actions; selecting an active weapon again turns all three off.
- Automatically pick up Hoppity calls, accept the offer, and inspect the rabbit offer in inventory slot 22.
- Add a configurable Hoppity auto-buy option. It is off by default and skips rabbits whose tooltip says they were already found.

### Changed

- Pause fishing while the Hoppity window is open, then resume one second after it closes.
- Upload the mod JAR and sources JAR as separate, unzipped GitHub Actions artifacts.
