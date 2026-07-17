# AI Behaviour request context

Blockfolk sends an AI request only when the NPC preset is active, at least one context
section is configured, OpenRouter is configured, and an enabled trigger fires. The two
automatic triggers are an approach greeting, nearby player chat, and an optional nearby
death reaction. Chat is accepted within eight blocks of the NPC; deaths are observed
within twelve blocks.

One player chat message creates one group request for all eligible NPCs within range,
ordered by distance from the player. The closest NPC is the default speaker. The model
may involve additional nearby NPCs when that is natural, but is explicitly told not to
make every NPC respond merely because it is present. Non-AI On Player Chat actions still
run independently for every nearby NPC.

Opening the NPC preset's admin editor clears AI memory and queued interactions for every
spawned instance of that preset. Conversation history otherwise has no time or distance
expiry. Removing or respawning an instance also clears that instance's runtime memory.

## OpenRouter request

Each request contains two messages:

1. A system message assembled from the configured Identity, Personality & Behaviour,
   Goal / Role, and Knowledge / Information sections. Empty sections are omitted.
2. A user message containing the current event, NPC state, perceived surroundings,
   recent memory, and enabled capabilities.

For single-NPC events, the system message requires a single JSON response containing zero
to three validated actions. Group chat responses contain a list keyed by safe aliases
(`npc_1`, `npc_2`, and so on), with up to three actions per responding NPC. It describes
the accepted action and target formats, requests concise
in-character speech, and asks the model to greet on approach, answer nearby chat, or
respond naturally to a nearby death when the corresponding event triggered the request.
Commands, executable code, unrecognized actions, and capabilities disabled in the
preset are rejected.

The request uses temperature `0.4`, JSON-object response formatting, and the configured
`openrouter.max-tokens` value (1600 by default).

## Current event

The user message begins with a plain-language event description. For automatic AI
behaviour this is currently one of:

- the player's name and the fact that they approached or are already near the NPC;
- the player's name and exact nearby chat message;
- a nearby death, including victim, rounded distance, Minecraft damage cause, and the
  killer, held weapon, and direct cause (such as a projectile) when available.

Chat is sent only when Respond to Nearby Chat is enabled. When it is disabled, Blockfolk
does not store that new chat for later use.

## NPC state

The following state is included when available:

- preset display name and world name;
- current and maximum health;
- whether the NPC is in combat;
- whether a route is configured;
- the material held in the main hand.

Exact NPC or player coordinates are not included.

## Perceived surroundings

Perception uses a 12-block radius for players and other Blockfolk NPCs. It includes:

- up to five nearest players, with name, rounded distance, held item, and whether the
  player triggered the request;
- up to three nearest Blockfolk NPCs, with display name, rounded distance, and combat
  state;
- nearby non-player, non-Blockfolk entities grouped by readable entity type, including
  count, approximate nearest distance, and whether that type contains the triggering
  entity.

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
- up to twenty conversation lines per player, retained until an administrator opens
  that NPC preset's editor or the instance is removed.

Only the conversation belonging to the player who triggered the current request is
included. Conversations are not shared between spawned copies of the same preset, but a
group chat request supplies each participating NPC's own conversation memory to the model.

## Enabled capabilities

The request lists only the capabilities enabled for the preset. The response parser
enforces the same list before anything runs. Current capabilities are:

- Respond to Nearby Chat / speech;
- play animation;
- start or stop combat; Start Combat falls back to the nearest attackable entity within
  16 blocks when the model does not provide a valid target, regardless of the preset's
  aggression setting;
- flee from a target;
- follow a player;
- stop following the current player;
- walk to and toggle the nearest available button or lever within 12 blocks;
- return home;
- start or pause the configured route;
- do nothing, which is always available.

Targeted capabilities may refer only to the triggering player, triggering entity,
nearest player, nearest attackable entity, or current combat target. The model cannot
provide arbitrary entity IDs or server commands.
