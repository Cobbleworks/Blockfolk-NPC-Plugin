# AI Behaviour request context

Requests use OpenRouter with reasoning effort set to `none` for lower-latency gameplay
responses. An animated `Thinking.`, `Thinking..`, `Thinking...` hologram is shown above
an NPC only while its request is actively in flight.

Blockfolk sends an AI request only when the NPC preset is active, at least one context
section is configured, OpenRouter is configured, and a trigger fires. `AI Trigger` can be
placed in any standard, custom-event, waypoint, or question-branch behaviour sequence.
Nearby player chat can also trigger requests directly within eight blocks of the NPC.

One player chat message creates one group request for all eligible NPCs within range,
ordered by distance from the player. The closest NPC is the default speaker. The model
may involve additional nearby NPCs when that is natural, but is explicitly told not to
make every NPC respond merely because it is present. Nearby chat is an AI-only trigger and
does not require a manual Event Behaviour sequence.

An NPC that enters chat range while already handling another AI event does not hold up the
existing group. Available NPCs answer immediately, and the busy newcomer becomes eligible
to participate in a later player message after its current request finishes. Chat range is
captured when the message is sent. Moving across the eight-block boundary while the request
is queued therefore does not silently discard the message or change which NPCs heard it.

Opening the NPC preset's admin editor clears AI memory and queued interactions for every
spawned instance of that preset. Conversation history otherwise has no time or distance
expiry. Removing or respawning an instance also clears that instance's runtime memory.

## OpenRouter request

Each request contains two messages:

1. A system message assembled from the configured Identity, Personality & Behaviour,
   Likes & Dislikes, Goal / Role, and Knowledge / Information sections. Empty sections
   are omitted.
2. A user message containing the current event, NPC state, perceived surroundings,
   recent memory, and enabled capabilities.

For single-NPC events, the system message requires a single JSON response containing zero
to three validated actions. Group chat responses contain a list keyed by safe aliases
(`npc_1`, `npc_2`, and so on), with up to three actions per responding NPC. It describes
the accepted action and target formats, requests concise in-character speech, and asks
the model to react naturally to the event that triggered the request.
Commands, executable code, unrecognized actions, and capabilities disabled in the
preset are rejected.

The request uses temperature `0.4`, JSON-object response formatting, and the configured
`openrouter.max-tokens` value (1600 by default).

## Current event

The user message begins with a plain-language event description. It may describe any
event routed through an `AI Trigger` or nearby chat. Examples include:

- the player's name and the fact that they approached or are already near the NPC;
- the player's name and exact nearby chat message;
Chat is sent only when Respond to Nearby Chat is enabled. When it is disabled, Blockfolk
does not store that new chat for later use.

## NPC state

The following state is included when available:

- preset display name and world name;
- current and maximum health;
- whether the NPC is in combat;
- whether a route is configured;
- the material held in the main hand.
- when Temporary Inventory access is enabled, every occupied slot in that spawned
  instance's temporary inventory, including a safe slot alias, stack size, and material.

Exact NPC or player coordinates are not included.

## Perceived surroundings

Perception uses a 12-block radius for players and other Blockfolk NPCs. It includes:

- up to five nearest players, with name, rounded distance, held item, and whether the
  player triggered the request;
- up to three nearest Blockfolk NPCs, with display name, rounded distance, and combat
  state;
- up to five nearby non-player, non-Blockfolk entities, with readable entity type,
  rounded distance, and whether it is the triggering entity;
- up to five nearby levers within 12 blocks, including rounded distance and whether
  each lever is powered;
- when Temporary Inventory and Interact are enabled, up to five nearby containers,
  including their type, distance, and a bounded summary of their contents;
- up to five nearest globally saved locations in the same world and within 64 blocks.
- counts of reachable ores, logs, and pickaxe-mineable materials, with material aliases
  accepted by `MINE_BLOCKS`.

Each perceived player, NPC, entity, and saved location has a request-local alias such as
`nearby_player_1` or `nearby_location_2`. The AI can pass one of those aliases to
`MOVE_TO`. `FOLLOW` requires a player target and accepts `triggering_player`,
`nearest_player`, a listed `nearby_player_N` alias, or that listed player's Minecraft
name. Arbitrary coordinates and unlisted targets are rejected.

Non-player entities are collected from Bukkit's 12-by-12-by-12-axis nearby-entity query.
They are summarized rather than listed with UUIDs or exact positions. Both visible
Blockfolk mannequin entities and their invisible husk navigation helpers are excluded.

## Environment

When the NPC's world is available, the request includes:

- broad time of day: dawn, day, sunset, or night;
- weather: raining or clear;
- biome name;
- broad light level: dark, dim, or bright;
- an approximate indoors assessment based on whether blocks exist above the NPC.
- up to five nearest non-empty signs within 12 blocks, including front and back text,
  approximate distance, and a per-sign text limit.

## Memory

Blockfolk keeps two forms of runtime memory per spawned NPC:

- up to ten recent event summaries, retained for five minutes;
- up to twenty conversation lines, retained until an administrator opens that NPC
  preset's editor or the instance is removed. The menu selects private per-player history
  or one shared history for all players on that spawned instance.

In Private mode, only the conversation belonging to the triggering player is included.
In Shared mode, every player reads and contributes to the same conversation. Conversations
are never shared between spawned copies of the same preset, but a group chat request
supplies each participating NPC's own conversation memory to the model.
After a coordinated response, every participating NPC stores every spoken line from the
group turn so later conversations retain the same shared awareness.

An optional third form is long-term preset memory. When enabled, up to 45 durable facts
are persisted in the NPC definition and included in future requests for every spawned
copy of that preset. The model may add facts through `REMEMBER_FACT`; the oldest fact is
discarded when the grid is full. Administrators can add, edit, or remove individual facts
from the AI Behaviour memory menu, or shift-right-click its Memory entry to clear them all.

## Enabled capabilities

The request lists only the capabilities enabled for the preset. The response parser
enforces the same list before anything runs. Current capabilities are:

- Respond to Nearby Chat / speech;
- play animation;
- start or stop combat; Start Combat accepts a perceived player, NPC, mob, or animal alias
  regardless of the preset's normal combat target categories. With no target it selects
  the nearest safely attackable living entity within 16 blocks;
- flee from a target;
- follow a player;
- stop following the current player;
- walk to and toggle the nearest available button or lever within 12 blocks;
- when Temporary Inventory access is enabled, walk to the nearest suitable container and
  either retrieve its first fitting stack or store as much of the carried inventory as fits,
  using the `take_from_container` or `store_in_container` INTERACT target;
- move to a perceived saved location, player, Blockfolk NPC, or other entity;
- return home;
- start or pause the configured route;
- when Temporary Inventory access is enabled and the instance carries items, drop one
  selected inventory stack using its `inventory_slot_N` alias. The dropping NPC pauses
  automatic item pickup for three seconds so it does not immediately reclaim the stack;
- when Mine Blocks is enabled, mine up to 64 matching reachable blocks within eight blocks
  using `ores`, `trees`, `mineable_blocks`, or a listed material as the target. With
  Temporary Inventory enabled, drops are inserted directly and a block is not broken if
  they will not fit. Otherwise, the block drops its items naturally into the world;
- do nothing, which is always available.

Targeted capabilities may refer only to the triggering player, triggering entity,
nearest player, nearest attackable entity, current combat target, or a compatible safe
alias listed in the request. The model cannot provide arbitrary coordinates, entity IDs,
or server commands.
