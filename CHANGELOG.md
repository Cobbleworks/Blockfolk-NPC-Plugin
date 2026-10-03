# Changelog

Notable changes to Blockfolk are documented in GitHub release notes.

## [Unreleased]

### Added

- NPC shops: give any preset item-for-item trades with unlimited stock, shown in the vanilla trading screen. Configure them under **Shop** in the NPC editor or with `/bf shop <name> [title|preview]`.
- Open Shop behaviour action that opens an NPC's shop for the triggering player, for example on right-click or from a question answer.
- Ring, Chain, Dash, and Self ability shapes. Rings spare a safe inner area, chains jump between nearby enemies, dashes rush the caster towards its target and strike along the path, and Self abilities empower the caster.
- Lingering pulses: spheres, rings, cones, and beams can strike up to 10 times at their marked spot and aim, without pausing weapon combat.
- Ability triggers (Always, Caster below 50% / 25% health, Target below 50% health) and a minimum range for automatic casts. Abilities whose trigger is met are preferred.
- Darkness, Nausea, Hunger, Mining Fatigue, Levitation, Glowing, Pull, and Launch victim effects, plus Regeneration, Speed, Strength, Resistance, and Absorption caster buffs.
- Holy, Sculk, Cherry, Wind, and Glow visual themes, with a matching impact sound for every theme.
- Frost Nova, Chain Lightning, Poison Cloud, Gravity Well, Searing Ray, Shadow Dash, Updraft, Finishing Blow, War Cry, and Second Wind templates.
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

- AI NPCs follow through on requested actions more reliably. Each function call now gets its own result. A response with a rejected call runs nothing and is sent back once with the reasons and valid targets, so an NPC no longer says "on my way" while its move is rejected. After acting, the model checks whether its speech promised anything it has not started.
- Long-term AI memory keeps only key facts about people and agreements (treatment, deals, promises, meetings, news) and no longer stores reminders of what the NPC is currently doing.
- AI turns must begin with a function call, so models can no longer answer in plain text that players never see.
- AI targets accept saved location names, player and NPC names, and mob types as well as listed aliases. `move_to` also accepts block coordinates within 128 blocks, and the NPC now knows its own position and facing.
- AI perceives saved locations within 128 blocks (up to 20, with compass direction), up from 64 blocks.
- Per-action guidance moved from the system prompt into each function's description, and only enabled actions are described.
- New `openrouter.reasoning-effort` setting, default `low`. Reasoning was previously always disabled; set it to `none` for the previous behaviour.
- Unreachable AI destinations and invalid follow targets are recorded in the NPC's recent event memory for its next request.
- The ability editor shows every shape, cast mode, trigger, and visual theme as a choice instead of cycling through them. Selected options glow and say "✔ Selected", effects are grouped into Afflictions, Crowd Control, and Caster Buffs, numeric controls show their allowed range, and only the settings that apply to the chosen shape are shown.
- Knockback strength is now Force, shared by Knockback, Pull, and Launch. Knockback and Pull replace each other.
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
