# Core concepts

Blockfolk separates reusable configuration from the NPCs placed in the world.

## Presets and variants

An **NPC preset** is the main NPC. It stores the display name, skin, equipment, properties, combat profile, movement configuration, event routines, and AI settings. A **variant** is one persistent spawned copy with its own UUID, position, and spawn point. A variant can also have its own skin and swap the preset's routes for others, and it shares everything else with the preset.

Spawning a preset creates one NPC by default. Additional copies are created explicitly through **Variants** or the spawn command. Integrations refer to each copy's persistent UUID, including after a server restart or combat respawn. See [Variants](/features/npcs#variants).

## Events and actions

A behaviour routine starts with an **event** such as a player approaching, the NPC taking damage, or a route point being reached. The attached **actions** run from left to right. Actions can speak, move, fight, interact with the world, emit another event, or invoke AI.

[Scenes](/features/scenes) coordinate several NPCs. A scene's script runs beat by beat: in each beat every NPC playing a role runs its actions at the same time, and then the next beat starts. A scene starts at a time of day, on a weather change, when a player enters its area, or on command.

## Routes and locations

A **route** belongs to one NPC preset and is a group of blocks in one world forming a repeating walking loop. A **global location** is a named position that movement actions and AI can target. Routes describe a path; locations describe destinations.

## Deterministic and AI behaviour

Normal event routines are deterministic and do not require an external service. **AI Trigger** hands the current context to the configured OpenRouter model, which may return only actions enabled for that preset. AI augments the routine system rather than replacing it.

## Where configuration lives

Global timing and provider values live in `config.yml`. Presets, variants, routes, locations, and scenes are maintained by the plugin in separate YAML files. Use the GUI for editing those files and include the entire `plugins/Blockfolk/` directory in backups.
