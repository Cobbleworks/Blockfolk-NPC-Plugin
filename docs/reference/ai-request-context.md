# AI request context

This page describes the bounded gameplay state Blockfolk sends to OpenRouter. Requests use JSON response formatting, temperature `0.4`, the configured token limit, and disabled model reasoning for lower latency.

## When a request is sent

An NPC preset must be active, have at least one context section, and have a trigger. **AI Trigger** can be placed in standard, custom-event, waypoint, and question-branch routines. **Respond to Nearby Chat** creates requests directly for player chat within eight blocks.

One chat message creates one coordinated request for up to five eligible NPCs, ordered by distance. Busy NPCs do not delay those that are available; they can participate in a later message.

## Messages sent to OpenRouter

Each request contains:

1. A system message with configured identity, personality and behaviour, likes and dislikes, goal or role, and knowledge or information. Empty sections are omitted.
2. A user message with the triggering event, NPC state, perceived surroundings, recent memory, and enabled capabilities.

Single-NPC requests return up to three validated actions. Group chat returns responses keyed by safe aliases such as `npc_1` and `npc_2`.

### Aliases and real names

Aliases do not replace NPC names in the model context. Blockfolk sends an explicit mapping for every participant, for example:

```text
npc_1 (NPC Mr. Mario)
=== npc_1: Mr. Mario (closest; default speaker) ===
```

The exact player message is included as the event. This lets the model understand a player addressing “Mr. Mario” while still requiring `npc_1` in the response JSON. The alias is a request-local routing and validation key; the display name supplies the conversational identity.

Nearby players, NPCs, entities, locations, switches, containers, and inventory slots use the same pattern: a safe alias paired with a readable name or type. Arbitrary coordinates, UUIDs, and unlisted targets are rejected.

## NPC state

The request includes, when available:

- preset display name and world;
- current and maximum health;
- combat and route state;
- main-hand material;
- occupied temporary-inventory slots when that access is enabled.

Exact NPC and player coordinates are not included.

## Perceived surroundings

General perception uses a 16-block radius and includes:

- up to five nearest players, including name, distance, held item, and whether they triggered the request;
- up to three nearest Blockfolk NPCs, including display name, distance, and combat state;
- up to five other nearby entities, excluding visible Blockfolk entities and their navigation helpers;
- up to eight nearest buttons and levers when **Interact** is enabled;
- up to five containers with bounded content summaries when **Interact** and **Temporary Inventory** are enabled;
- up to five non-empty signs with front/back text;
- reachable ores, logs, and pickaxe-mineable blocks when **Mine Blocks** is enabled.

Up to five named global locations in the same world are included within 64 blocks. Mineable resources are scanned within eight blocks.

## Environment

When the world is available, the request includes broad time of day, weather, biome, light level, and an approximate indoors assessment. Environmental text such as signs is explicitly treated as observation rather than instructions.

## Runtime memory

Blockfolk keeps:

- up to ten recent event summaries for five minutes;
- recent conversation lines up to `ai-control.conversation-history-limit`, which defaults to `20`;
- up to 45 optional long-term preset facts when memory is enabled.

Private conversation is scoped to one player and one spawned NPC. Shared conversation is scoped to one spawned NPC and is visible to every player speaking with that instance. Conversations are not shared between separate spawned copies of the preset.

In coordinated group chat, every participating NPC remembers every spoken line from that group turn. Each line includes the speaking NPC's display name.

Opening the preset editor clears runtime event and conversation memory, pending requests, and queued interactions for all its instances. Long-term preset facts remain until edited or cleared.

## Capability validation

The request lists only the actions enabled for that preset. Depending on settings and current state, these can include speech, animation, combat, fleeing, following, world interaction, moving, returning home, route control, mining, dropping inventory items, remembering facts, and doing nothing.

The parser validates the response against the same capability set and the captured target snapshot before gameplay actions run. Commands, executable code, unknown actions, disabled actions, arbitrary coordinates, and unknown targets are rejected.
