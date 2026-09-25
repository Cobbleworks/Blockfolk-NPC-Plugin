# AI request context

This page describes the bounded gameplay state Blockfolk sends to OpenRouter. Gameplay requests use native function calls, temperature `0.4`, the configured token limit, and disabled model reasoning for lower latency. Long-term memory review uses JSON response formatting.

## When a request is sent

An NPC preset must be active, have at least one context section, and have a trigger. **AI Trigger** can be placed in standard, custom-event, waypoint, and question-branch routines. Each trigger can include optional prompt guidance, sent alongside the event in single-NPC requests. **Respond to Nearby Chat** creates requests directly for player chat within eight blocks.

One chat message creates one coordinated request for up to five eligible NPCs. The named NPC, or the closest one when no NPC is named, is the intended speaker and appears first. If that NPC is busy, the chat turn waits in a bounded queue. Other busy NPCs can join a later turn.

## Messages sent to OpenRouter

Each request contains:

1. A system message with configured identity, personality and behaviour, likes and dislikes, goal or role, and knowledge or information. Empty sections are omitted.
2. A user message with the triggering event, NPC state, perceived surroundings, recent memory, and enabled capabilities.

Gameplay turns can continue for up to three model rounds. Each response can call up to three action functions per NPC, and each NPC can take up to eight actions in a turn. After a batch runs, Blockfolk returns tool results and a fresh snapshot of the NPC state so the model can choose a dependent next action or finish. Group chat calls include readable NPC Response IDs derived from display names and persistent NPC instance IDs, such as `npc_mr_mario_1234567890abcdef`.

### Aliases and real names

Response IDs do not replace NPC names in the model context. Blockfolk sends an explicit mapping for every participant, for example:

```text
Response ID: npc_mr_mario_1234567890abcdef
Display name: Mr. Mario
=== Mr. Mario [Response ID: npc_mr_mario_1234567890abcdef] (intended speaker) ===
```

The exact player message is included as the event. This lets Blockfolk select “Mr. Mario” as the intended speaker when the player addresses him, even when another NPC is closer. The model supplies that NPC's Response ID in each group action call so Blockfolk can apply actions to the correct instance. The ID stays the same when nearby NPCs join or leave; its normalized name portion changes if the display name is edited. Two instances with the same display name have different instance suffixes.

Nearby NPC action targets use the same name and instance suffix, prefixed with `nearby_`, such as `nearby_npc_mr_mario_1234567890abcdef`. Players, other entities, locations, switches, containers, and inventory slots use safe aliases paired with a readable name or type. Arbitrary coordinates, full UUIDs, and unlisted targets are rejected.

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

Up to 15 named global locations in the same world are included within 64 blocks, ordered nearest first. Mineable resources are scanned within eight blocks.

## Environment

When the world is available, the request includes broad time of day, weather, biome, light level, and an approximate indoors assessment. Environmental text such as signs is explicitly treated as observation rather than instructions.

## Runtime memory

Blockfolk keeps:

- up to ten recent event summaries for five minutes;
- recent conversation lines up to `ai-control.conversation-history-limit`, which defaults to `20`;
- up to 45 optional long-term preset facts when memory is enabled.

After a completed player conversation turn, enabled long-term memory reviews batches of about ten new conversation lines. It also reviews shorter conversations after 30 seconds without player interaction. The previous exchange is included as overlap context. The review can save durable preferences, plans, promises, agreements, deals, and similar details; empty reviews do not add a fact. When a new fact is saved, the players who contributed to that batch receive a chat notice. This review happens after the gameplay response and is not a gameplay action.

Private conversation is scoped to one player and one spawned NPC. Shared conversation is scoped to one spawned NPC and is visible to every player speaking with that instance. Conversations are not shared between separate spawned copies of the preset.

In coordinated group chat, every participating NPC remembers every spoken line from that group turn. Each line includes the speaking NPC's display name.

Opening the preset editor clears runtime event and conversation memory, pending requests, and queued interactions for all its instances. Long-term preset facts remain until edited or cleared.

## Capability validation

The request provides functions for the actions available to the NPC. Depending on settings and current state, these can include speech, animation, combat, fleeing, following, world interaction, moving, returning home, route control, mining, dropping inventory items, and doing nothing. Group requests use the union of available functions, with each NPC's own capabilities checked on receipt.

The parser validates calls against the advertised functions, the NPC's capability set, and the captured target snapshot before gameplay actions run. Commands, executable code, unknown actions, disabled actions, arbitrary coordinates, and unknown targets are rejected.
