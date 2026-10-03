# AI request context

This page describes the bounded gameplay state Blockfolk sends to OpenRouter. Gameplay requests use native function calls, temperature `0.3`, the configured token limit, and the configured `openrouter.reasoning-effort` (default `low`). Long-term memory review uses JSON response formatting.

## When a request is sent

An NPC preset must be active, have at least one context section, and have a trigger. **AI Trigger** can be placed in standard, custom-event, waypoint, and question-branch routines. Each trigger can include optional prompt guidance, sent alongside the event in single-NPC requests. **Respond to Nearby Chat** creates requests directly for player chat within eight blocks.

One chat message creates one coordinated request for up to five eligible NPCs. The named NPC, or the closest one when no NPC is named, is the intended speaker and appears first. If that NPC is busy, the chat turn waits in a bounded queue. Other busy NPCs can join a later turn.

## Messages sent to OpenRouter

Each request contains:

1. A system message with configured identity, personality and behaviour, likes and dislikes, goal or role, and knowledge or information. Empty sections are omitted. If `plugins/Blockfolk/<world-name>.md` exists, its Markdown is appended as world context for NPCs in that world.
2. A user message with the triggering event, NPC state, perceived surroundings, and recent memory. Enabled capabilities are sent as function definitions, each describing its own targets and usage.

Gameplay turns can continue for up to three model rounds. The first response of a turn must contain at least one function call (`do_nothing` covers deliberate silence); providers that reject a required tool choice fall back to optional tool calls automatically. Each response can call up to three action functions per NPC, and each NPC can take up to eight actions and speak once in a turn. Extra `say` calls from the same NPC are skipped.

Every function call receives its own result. When any call in a response is rejected, nothing from that response runs: the model receives the reason for each rejected call, including the valid targets, and gets one chance to send a corrected set. This keeps an NPC from saying "on my way" while its `move_to` call was rejected. After a batch runs, Blockfolk returns per-call results and a fresh snapshot of the NPC state, and asks the model to check whether its speech promised anything it has not yet started. The model can then call a dependent next action or finish. Failures that only show up later, such as a destination with no walkable path, are added to the NPC's recent event memory for its next request. Group chat calls include readable NPC Response IDs derived from display names, such as `npc_mr_mario`.

World context files are optional. For example, `plugins/Blockfolk/world.md`, `plugins/Blockfolk/world_nether.md`, and `plugins/Blockfolk/customworld.md` supply context only to NPCs in the matching worlds. The files are read as UTF-8 when a gameplay request starts, so changes apply to the next request without a server restart. Empty or missing files add no context. In a group chat request, each participating world file is included once.

### Aliases and real names

Response IDs do not replace NPC names in the model context. Blockfolk sends an explicit mapping for every participant, for example:

```text
Response ID: npc_mr_mario
Display name: Mr. Mario
=== Mr. Mario [Response ID: npc_mr_mario] (intended speaker) ===
```

The exact player message is included as the event. This lets Blockfolk select “Mr. Mario” as the intended speaker when the player addresses him, even when another NPC is closer. The model supplies that NPC's Response ID in each group action call so Blockfolk can apply actions to the correct NPC. When names repeat within one request, IDs get `_01`, `_02`, and so on. These aliases are unique within the request and may change when the participant list changes.

Nearby NPC action targets use the same naming pattern, prefixed with `nearby_`, such as `nearby_npc_mr_mario`. Duplicate names in a request receive numbered aliases such as `nearby_npc_mr_mario_01`. Players, other entities, locations, switches, containers, and inventory slots use safe aliases paired with a readable name or type. Blockfolk also maps the names the model commonly uses to the listed alias: a saved location's name or last path segment (`market` for `town/market`), a player or NPC name, an entity's custom name or type (nearest first), and aliases with the `nearby_` prefix left off. A name shared by two targets is not guessed. `move_to` also accepts block coordinates as `x,y,z` within 128 blocks in the NPC's world. Full UUIDs and unlisted targets are rejected.

## NPC state

The request includes, when available:

- preset display name, world, block position, and facing direction;
- current and maximum health;
- combat and route state, including an active walking destination when pathfinding;
- whether the NPC is in water or burning, plus active status effects;
- main-hand material;
- occupied temporary-inventory slots when that access is enabled.

Player coordinates are not included.

## Perceived surroundings

General perception uses a 16-block radius and includes:

- up to five nearest players, including name, distance, held item, and whether they triggered the request;
- up to three nearest Blockfolk NPCs, including display name, distance, and combat state;
- up to five other nearby entities, excluding visible Blockfolk entities and their navigation helpers;
- up to eight nearest buttons and levers when **Interact** is enabled;
- up to eight nearest doors, including whether each is open or closed;
- up to five containers with bounded content summaries when **Interact** and **Temporary Inventory** are enabled;
- up to five non-empty signs with front/back text;
- reachable ores, logs, and pickaxe-mineable blocks when **Mine Blocks** is enabled.

Up to 20 named global locations in the same world are included within 128 blocks, ordered nearest first, with distance and compass direction. Mineable resources are scanned within eight blocks.

## Environment

When the world is available, the request includes broad time of day, weather, biome, light level, and an approximate indoors assessment. Environmental text such as signs is explicitly treated as observation rather than instructions.

## Runtime memory

Blockfolk keeps:

- up to ten recent event summaries for five minutes;
- recent conversation lines up to `ai-control.conversation-history-limit`, which defaults to `20`;
- up to 45 optional long-term preset facts when memory is enabled, plus nearby Regional facts from other presets.

Enabled long-term memory reviews the recent conversation after 20 seconds without a new player chat message. Follow-up messages reset the timer; the review waits for pending chat responses to finish. Up to 20 conversation lines are available and the previous exchange overlaps the next review. The review saves only key facts about people and agreements (treatment, deals, promises, meetings, notable news), never reminders of in-progress tasks or small talk. It can save up to three Personal, Regional, or Temporal facts, and usually saves none or one. Personal and Temporal facts appear only in the source NPC preset’s context. Regional facts are anchored where they were learned and appear in every AI NPC context within 50 blocks, including NPCs with memory saving disabled. Temporal facts expire after 24 hours. At the 45-fact limit, saving a new fact replaces the oldest Temporal fact if one exists; otherwise, it replaces the oldest fact. Saved Regional facts produce a "spreading rumors" notice; other saved facts produce a "remembered this" notice. This review is separate from gameplay actions.

Private conversation is scoped to one player and one spawned NPC. Shared conversation is scoped to one spawned NPC and is visible to every player speaking with that instance. Conversations are not shared between separate spawned copies of the preset.

In coordinated group chat, every participating NPC remembers every spoken line from that group turn. Each line includes the speaking NPC's display name.

Opening the preset editor clears runtime event and conversation memory, pending requests, and queued interactions for all its instances. Long-term preset facts remain until edited or cleared.

## Capability validation

The request provides functions for the actions available to the NPC. Depending on settings and current state, these can include speech, animation, combat, fleeing, following, world interaction, moving, returning home, route control, mining, remembering a named location, dropping inventory items, and doing nothing. Group requests use the union of available functions, with each NPC's own capabilities checked on receipt.

The parser validates calls against the advertised functions, the NPC's capability set, and the captured target snapshot before gameplay actions run. Commands, executable code, unknown actions, disabled actions, out-of-range coordinates, and unknown targets are rejected, and each rejection is explained to the model. **Remember Location** takes a label only; Blockfolk saves the NPC's current position when that action runs and cannot overwrite an existing name.
