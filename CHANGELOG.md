# Changelog

Notable changes to Blockfolk are documented in GitHub release notes.

## [Unreleased]

### Added

- Use Ability behaviour action with a shared-library selector for event routines, waypoint actions, and question branches.
- Instant, Delayed, and On Next Attack cast modes, with successful-hit charge triggers, caster particles, charge indicators, and saved timing modes.
- Enchant, Hearts, Smoke, Soul Flame, Bubbles, Spores, and Totem visual themes, plus changing icons and descriptions for particle and shape selectors.
- Custom ability icons copied from a held item with Q / Drop, and separately configurable cone length.
- Dedicated Abilities library and template editor, available through `/bf abilities` and the main menu, with per-NPC attack assignments and previews.
- Custom attack origins, spherical areas, aimed cones, beams that stop at blocks, safe short-range teleports, instant or delayed casts, cooldowns, damage, combined effects, and visual themes.
- Fire Breath, Sonic Blast, and Reposition Blink templates; defensive Shockwave follows the caster. Existing special attack assignments remain compatible.
- Persist shared definitions in `abilities.yml` and custom assignments in combat profiles and Change Fight Options actions.
- Native special attacks in Fighting & Survival: life drain, freezing spell, poison spit, wither curse, flame burst, lightning mark, shockwave, and fear.
- Individual attack toggles, adjustable cadence, per-instance cooldowns, and configurable cast warnings with dodgeable impacts.
- Persist special attack settings per preset and configure temporary overrides through Change Fight Options actions for behaviours, waypoints, and question branches.

### Changed

- Rename Fighters to Abilities, including `/bf abilities`, and migrate saved definitions to `abilities.yml`.
- Consolidate health, respawn time, and experience controls into one icon each with left/right adjustment, shift for five steps, middle-click input, and consistent coloured click labels.

### Fixed

- Preserve ongoing combat timers when the same opponent repeatedly attacks an NPC.

## [1.3.0] - 2026-09-30

### Added

- Expose a public plugin-owned temporary NPC API for integrations such as BloodMoon: mannequins, skins, native navigation, looking, and hand/item animations.
- Clean temporary NPCs and navigation helpers when their owning plugin disables; exclude temporary entities from saved administrator definitions and world entity saves.
- Accept precise movement speeds for external encounter controllers while preserving existing walking speed presets.

### Fixed

- Apply the repository's required Java formatting to files that previously blocked the release checks.
