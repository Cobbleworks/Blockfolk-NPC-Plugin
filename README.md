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

## AI Behaviour

AI Behaviour is configured directly from an NPC preset's dedicated menu. Context is
split into Identity, Personality & Behaviour, Goal / Role, and Knowledge / Information.
Add the `Trigger AI` action to any normal event, custom event, question branch, or route
waypoint to ask the model for a decision. Nearby player conversation is enabled directly
with the AI menu's On Player Chat toggle. For other reactions, add `Trigger AI` to events
such as On Player Approach, On Nearby Death, or On Work Available. Death reactions receive the victim, killer, held weapon,
and Minecraft damage cause when available. Each
spawned NPC keeps its own per-player conversation memory until an administrator opens
that NPC preset's editor, which resets AI state globally for its spawned copies. The
model can only select validated capabilities enabled for that preset; it cannot return
commands or executable code.

Existing presets are migrated on load: the former greeting, nearby-chat, nearby-death,
and autonomous-work toggles become Trigger AI actions on their corresponding events.

Gather Resources can be combined with On Work Available -> Trigger AI for independent
miners or woodcutters. For example,
set the Goal / Role to `You are a pro miner. Try to gather coal and gold in the mines`,
or `You are a builder. Gather nearby sand`, enable Gather Resources, add that trigger,
and optionally enable Temporary Inventory to store gathered drops. The work check interval
is configured with `behaviour.work-available-interval-seconds`.

The same Gather Resources operation is available directly in deterministic behaviour
sequences. Its editor supports broad categories and up to eight exact Minecraft block
IDs, and stores that selection directly on the behaviour action. `Any` includes every
breakable non-container block; fluids, portals, and unbreakable blocks remain protected.
Deterministic gathering places drops in the NPC's temporary inventory and drops only
overflow items.

Long-term memory is optional per preset. When enabled, the model can save durable facts
with a validated `REMEMBER_FACT` action. Up to 45 facts survive restarts and are shared by
the preset's spawned copies; adding another discards the oldest. The AI Behaviour menu
provides a 45-slot memory editor for adding, editing, deleting, or clearing these facts.

Set `openrouter.api-key` and `openrouter.model` in `config.yml` to enable requests.
Requests run asynchronously. Actions after Trigger AI wait until its decision has been
applied or the request has failed. If players
speak while a request or cooldown is active, the latest interaction is queued rather
than silently discarded.

AI requests explicitly disable model reasoning to reduce response latency. While a
request is in flight, a hologram above each participating NPC cycles through `Thinking.`,
`Thinking..`, and `Thinking...`; it disappears when the request finishes or fails.

See [AI Behaviour request context](docs/ai-behaviour.md) for the exact state, nearby
perception, memory, and capability information sent to OpenRouter.

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
