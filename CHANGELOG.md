# Changelog

Notable changes to Blockfolk are documented in GitHub release notes.

## [Unreleased]

## [1.3.0] - 2026-09-30

### Added

- Expose a public plugin-owned temporary NPC API for integrations such as BloodMoon: mannequins, skins, native navigation, looking, and hand/item animations.
- Clean temporary NPCs and navigation helpers when their owning plugin disables; exclude temporary entities from saved administrator definitions and world entity saves.
- Accept precise movement speeds for external encounter controllers while preserving existing walking speed presets.

### Fixed

- Apply the repository's required Java formatting to files that previously blocked the release checks.

