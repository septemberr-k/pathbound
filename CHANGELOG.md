# Changelog

## 1.1.1

- Dedicated servers: a fresh server has no operators, so nobody could choose and everyone saw "waiting for the host". Now:
  - `/op <name>` opens the chooser for that player at once (and `/deop` turns it back into the waiting notice), no rejoin needed;
  - players on a server see "Waiting for an operator..." with a hint for the admin (`op <name>`, or `pathbound mode set <id>` in the console);
  - the server console logs the same hint at start while the world has no difficulty.
- New server option `choosePermission` in `config/pathbound-server.toml`: the permission level needed to choose (2 = operators, default; 0 = any player).
- `defaultMode` accepts ids in any case.
- Build: the dev runs find the FTB / KubeJS jars by name, any version.

## 1.1.0

- KubeJS recipes per difficulty: `PathboundEvents.recipes('<difficulty id>', e => ...)` takes the same `e` as `ServerEvents.recipes` and applies only in worlds with that difficulty.
- Pathbound reloads the server data when script recipes depend on the difficulty: on a choice or change (also for a difficulty without datapacks) and once after a world opens.
- `PathboundEvents.afterChoose` now fires after that reload, together with the difficulty's commands.
- Binding `Pathbound.current()`: the world's difficulty without a server at hand. Java: `DifficultyApi.currentOrNull()`, hook `Hooks.needsDataReload`.

## 1.0.0

First release.

- One-time world difficulty choice on first join: `classic` (Classic) and `trial` (Trial) out of the box, shown as plain cards.
- Each difficulty grants its own player stages (`pathbound_classic`, `pathbound_trial`), enables its own datapacks and runs its own commands; switching away undoes stages and packs.
- Difficulty datapacks from `config/pathbound/datapacks/` (`config:<name>`, folder or `.zip`), from the mod jar (`builtin:<name>`) or any pack id.
- Everything configurable in `config/pathbound/difficulties.json`: names and descriptions (per language), colours, item or texture icons, drawn title images (`banner`), frame sprites (nine-slice, animated).
- The chooser's look from resource packs: `assets/pathbound/chooser/layout.json` (size, padding, columns, text alignment, frame inset, colours, background, sounds, animations).
- Optional KubeJS support: `PathboundEvents.register / beforeChoose / afterChoose / playerJoin`, binding `Pathbound`.
- Commands `/pathbound mode`, `/pathbound mode set <id|none>`, `/pathbound choose`, `/pathbound list`.
- Server option `defaultMode` in `config/pathbound-server.toml`.
- English and Russian translations.
