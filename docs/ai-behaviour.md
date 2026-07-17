# AI Behaviour request context

Blockfolk sends an AI request only when the NPC preset is active, at least one context
section is configured, OpenRouter is configured, and an enabled trigger fires. The two
automatic triggers are an approach greeting and nearby player chat. Chat is accepted
within eight blocks of the NPC.

Opening the NPC preset's admin editor clears AI memory and queued interactions for every
spawned instance of that preset. Conversation history otherwise has no time or distance
expiry. Removing or respawning an instance also clears that instance's runtime memory.

## OpenRouter request

Each request contains two messages:

1. A system message assembled from the configured Identity, Personality & Behaviour,
   Goal / Role, and Knowledge / Information sections. Empty sections are omitted.
2. A user message containing the current event, NPC state, perceived surroundings,
   recent memory, and enabled capabilities.

The system message also requires a single JSON response containing zero to three
validated actions. It describes the accepted action and target formats, requests concise
in-character speech, and asks the model to greet on approach or answer nearby chat when
the corresponding event triggered the request. Commands, executable code, unrecognized
actions, and capabilities disabled in the preset are rejected.

The request uses temperature `0.4`, JSON-object response formatting, and the configured
`openrouter.max-tokens` value (800 by default).

## Current event

The user message begins with a plain-language event description. For automatic AI
behaviour this is currently one of:

- the player's name and the fact that they approached or are already near the NPC;
- the player's name and exact nearby chat message.

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
They are summarized rather than listed with UUIDs or exact positions.

## Environment

When the NPC's world is available, the request includes:

- broad time of day: dawn, day, sunset, or night;
- weather: raining or clear;
- biome name;
- broad light level: dark, dim, or bright;
- an approximate indoors assessment based on whether blocks exist above the NPC.

## Memory

Blockfolk keeps two forms of runtime memory per spawned NPC:

- up to ten recent event summaries, retained for five minutes;
- up to twenty conversation lines per player, retained until an administrator opens
  that NPC preset's editor or the instance is removed.

Only the conversation belonging to the player who triggered the current request is
included. Conversations are not shared between spawned copies of the same preset.

## Enabled capabilities

The request lists only the capabilities enabled for the preset. The response parser
enforces the same list before anything runs. Current capabilities are:

- Respond to Nearby Chat / speech;
- play animation;
- start or stop combat;
- flee from a target;
- follow a player;
- return home;
- start or pause the configured route;
- do nothing, which is always available.

Targeted capabilities may refer only to the triggering player, triggering entity,
nearest player, or current combat target. The model cannot provide arbitrary entity IDs
or server commands.
