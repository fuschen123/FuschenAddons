# Changelog

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
