<p align="center">
  <img src="docs/icon.png" alt="Blockfolk icon" width="160">
</p>

<h1 align="center">Blockfolk</h1>

Blockfolk is a GUI-driven fake-player NPC system for Paper servers. It supports reusable NPC presets, persistent instances, skins, equipment, dialog, combat behaviour, and walking routes.

## Commands

All commands require the `blockfolk.admin` permission (granted to operators by default).

- `/bf` or `/blockfolk` — open the Blockfolk preset UI.
- `/bf create` — start creating an NPC and enter its name in chat.
- `/bf create <name>` — create an NPC preset with the supplied name.
- `/bf npc <name>` — open the editor for an NPC preset. NPC names are tab-completed.
- `/bf routes` — open the route manager.
- `/bf npc <name> spawn` — spawn a persistent copy of the selected NPC preset.
- `/bf npc <name> duplicate` — duplicate a preset with ` (copy)` appended to its display name.

Dialog line duration is calculated automatically from the text length at 12 characters
per second, with a minimum duration of 3 seconds.

Proximity enter/leave transitions are debounced for 3 seconds by default. Adjust
`proximity-transition-cooldown-seconds` in `config.yml` if needed.

## Experimental AI Control

AI Control is an opt-in behaviour action. Add it to an event in the Event Behaviour
editor, then configure its character prompt, mode, and allowed actions. Supported
event-driven inputs include player chat and approach, NPC attack and damage, combat,
nearby living entities, idle cycles, and route waypoints. The model can only select
validated actions enabled for that preset; it cannot return commands or executable code.

Set `openrouter.api-key` and `openrouter.model` in `config.yml` to enable requests.
Requests run asynchronously, time out independently, and never delay damage, combat,
death, routing, or other deterministic behaviour actions.

## Building

Blockfolk requires Java 21 and targets Paper 1.21.11.

```bash
mvn clean test package
```

The plugin jar is written to `target/Blockfolk.jar`.

## Screenshots

<table>
  <tr>
    <td><img src="docs/screenshots/blockfolk-gui-npcs.png" alt="Blockfolk NPC overview"></td>
    <td><img src="docs/screenshots/blockfolk-gui-npcs-details.png" alt="Blockfolk NPC details"></td>
  </tr>
  <tr>
    <td colspan="2"><img src="docs/screenshots/blockfolk-gui-npc-behaviour.png" alt="Blockfolk NPC behaviour editor"></td>
  </tr>
</table>
