# Blockfolk

Blockfolk is a GUI-driven fake-player NPC system for Paper servers. It supports reusable NPC presets, persistent instances, skins, equipment, dialog, combat behaviour, and walking routes.

## Commands

All commands require the `blockfolk.admin` permission (granted to operators by default).

- `/bf` or `/blockfolk` — open the Blockfolk preset UI.
- `/bf create` — start creating an NPC and enter its name in chat.
- `/bf create <name>` — create an NPC preset with the supplied name.
- `/bf routes` — open the route manager.
- `/bf npc <name>` — spawn a persistent copy of the selected NPC preset. NPC names are tab-completed.
- `/bf npc <name> duplicate` — duplicate a preset with ` (copy)` appended to its display name.
- `/bf config -seconds-per-line <seconds>` — set the global delay used for dialog lines and queued dialog actions.

## Building

Blockfolk requires Java 21 and targets Paper 1.21.11.

```bash
mvn clean test package
```

The plugin jar is written to `target/Blockfolk.jar`.
