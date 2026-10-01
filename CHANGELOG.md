# Changelog

Notable changes to Blockfolk are documented in GitHub release notes.

## [Unreleased]

### Added

- Dedicated Fighters library and template editor, available through `/bf fighters` and the main menu, with per-NPC attack assignments and previews.
- Custom attack origins, spherical areas, aimed cones, beams that stop at blocks, safe short-range teleports, instant or delayed casts, cooldowns, damage, combined effects, and visual themes.
- Fire Breath, Sonic Blast, and Reposition Blink templates; defensive Shockwave follows the caster. Existing special attack assignments remain compatible.
- Persist shared definitions in `fighters.yml` and custom assignments in combat profiles and Change Fight Options actions.
- Native special attacks in Fighting & Survival: life drain, freezing spell, poison spit, wither curse, flame burst, lightning mark, shockwave, and fear.
- Individual attack toggles, adjustable cadence, per-instance cooldowns, and configurable cast warnings with dodgeable impacts.
- Persist special attack settings per preset and configure temporary overrides through Change Fight Options actions for behaviours, waypoints, and question branches.

### Fixed

- Preserve ongoing combat timers when the same opponent repeatedly attacks an NPC.

## [1.3.0] - 2026-09-30

### Added

- Expose a public plugin-owned temporary NPC API for integrations such as BloodMoon: mannequins, skins, native navigation, looking, and hand/item animations.
- Clean temporary NPCs and navigation helpers when their owning plugin disables; exclude temporary entities from saved administrator definitions and world entity saves.
- Accept precise movement speeds for external encounter controllers while preserving existing walking speed presets.

### Fixed

- Apply the repository's required Java formatting to files that previously blocked the release checks.

