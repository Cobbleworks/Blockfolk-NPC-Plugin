# Core concepts

Blockfolk separates reusable configuration from the NPCs placed in the world.

## Presets and instances

An **NPC preset** stores the display name, skin, equipment, properties, combat profile, movement configuration, event routines, and AI settings. An **instance** is one persistent spawned copy with its own UUID and position.

One preset can have multiple instances. Integrations refer to the persistent instance UUID, including after a server restart or combat respawn.

## Events and actions

A behaviour routine starts with an **event** such as a player approaching, the NPC taking damage, or a route point being reached. The attached **actions** run from left to right. Actions can speak, move, fight, interact with the world, emit another event, or invoke AI.

## Routes and locations

A **route** is a group of blocks in one world forming a repeating walking loop. A **global location** is a named position that movement actions and AI can target. Routes describe a path; locations describe destinations.

## Deterministic and AI behaviour

Normal event routines are deterministic and do not require an external service. **AI Trigger** hands the current context to the configured OpenRouter model, which may return only actions enabled for that preset. AI augments the routine system rather than replacing it.

## Where configuration lives

Global timing and provider values live in `config.yml`. Presets, instances, routes, locations, and custom events are maintained by the plugin in separate YAML files. Use the GUI for editing those files and include the entire `plugins/Blockfolk/` directory in backups.
