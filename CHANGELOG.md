# Changelog

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
