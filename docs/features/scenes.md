# Scenes

A scene is a moment several NPCs play together: the station watcher checks the tracks, walks to the saloon to fetch Luigi, and the two walk back together. Each NPC taking part plays a **role**, and every role has its own action row. All roles start at the same time, and **cues** keep them in step.

Scenes can start on their own (at a time of day, when the weather changes, or when a player walks into the scene's area) or by hand with a command or the **Start Scene** action.

| Example | How it is built |
| --- | --- |
| **Last Train Is Late** · western town | Starts at **Evening**. The watcher's scene actions: *Move To station → Send Dialog → Move To saloon → Give Cue `told`*. Luigi's: *Wait For Cue `told` → Send Dialog → Move To station*. |
| **Market by the Lake** · jungle | Starts at **Noon**, ends at **Evening**. The shopkeeper and smith *Move To* the lake. Wägö *Waits* two minutes first, and while the scene runs his **On Player Approach** opens his shop. |
| **Champion's Exhibition** · desert city | Starts when a **player enters the area** around the colosseum. Aurelion and his training partner walk in, Aurelion addresses the crowd, then a cue starts the sparring. |
| **Snowed In** · snowland | Starts when **snow starts** where Plinfa is and ends **when the weather changes**. Plinfa walks indoors, and while the scene runs her **On Player Approach** calls out to passing players. |

## Create a scene

Open **Scenes** from the main `/bf` menu or run `/bf scenes` to see every scene. **Scenes** in an NPC's editor shows only the scenes that NPC plays in, and **Create Scene** there starts a scene with that NPC already cast. Choose **Create Scene** and enter a name. Use `/` to group scenes, for example `western/last-train`.

The scene editor has four columns:

- **Scene**: name and description. Middle-click the scene icon at the top to use the item in your hand as its icon.
- **When**: the triggers, and whether they are switched on.
- **Cast & Ending**: the roles, and when the scene ends.
- **Playback**: start or stop the scene right now, the chance that a trigger really starts it, and the cooldown before triggers may start it again.

## Roles

Open **Roles** and choose **Add Role**, then pick the NPC preset that plays it. A role has these action rows, built exactly like [event behaviour](./behaviours.md):

| Row | Runs |
| --- | --- |
| **Scene Actions** | When the scene starts, at the same time as the other roles. |
| **Wrap-Up** | When the scene ends, before the NPC goes back to its normal routine. |
| **While Active: …** | Instead of the preset's own reaction while the scene runs: On Idle, On Right-Click, On Player Approach, On Player Leaves, On Left-Click, On Receive Item, On Nearby NPC, and On Damage Taken. An empty row keeps the preset's own reaction. **While Active: On Idle** repeats once the role finished its scene actions. |

NPCs play themselves: a role has no name of its own and is shown with its NPC's name. **Played By** in the bottom bar of the role editor swaps the NPC.

Every free instance of the NPC plays the role. Usually there is just one; filler NPCs with several instances all do the same thing. If an NPC should act differently from the others, make it a separate preset. A role needs at least one free instance, otherwise the scene is skipped (see [Skipped scenes](#skipped-scenes)).

An NPC preset plays at most one role per scene; the role picker greys out NPCs that already have one. When a role needs more steps than fit in one row, end the row with **Start Scene** and continue in a follow-up scene (see below).

## Keeping roles in step

Every role runs its row on its own. Use cues when one NPC must wait for another:

- **Give Cue** lets every role waiting for that cue go on.
- **Wait For Cue** holds the row until another role gives that cue. If the cue was already given, the row goes on at once.

Cues only exist while the scene runs. Outside a scene, Give Cue does nothing and Wait For Cue goes on at once. **Move To** already waits until the NPC arrives (at most 30 seconds), so a row like *Move To saloon → Give Cue `arrived`* gives the cue on arrival.

When you add Give Cue or Wait For Cue, the cues already used in the scene are offered as clickable links in chat, so the names always match. The **Cues** button in the role editor lists them too.

## Triggers

Open **Starts When** and switch on everything that should start the scene:

| Group | Triggers |
| --- | --- |
| Time of day | Night (00:00), Morning (05:00), Noon (10:00), Afternoon (14:00), Evening (19:00) |
| Weather | Rain or snow starts, Rain starts, Snow starts, Thunderstorm starts, Weather clears |
| Area | Player enters the area |

Time and weather come from the world the scene's NPCs are in, and only NPCs from that world are cast. **Rain starts** and **Snow starts** depend on whether the biome an NPC of the scene stands in is cold enough for snow.

Clicking **Player enters the area** asks for the area's centre, a [global location](./locations.md), and switches the trigger on once you pick one. While it is on, right-click it to choose another location, and set the **Area Radius** next to it. Only NPCs in the area's world are cast.

Each trigger rolls the scene's **Chance** and respects its **Cooldown**. Switching **Triggers** off in the editor keeps the triggers but only allows manual starts.

## Ending

| Ends | When |
| --- | --- |
| **When everyone is done** | Every role finished its scene actions. This is at most 30 minutes after the start. |
| **After a set time** | The chosen time has passed. |
| **At a time of day** | The chosen part of the day begins. |
| **When the weather changes** | Rain, snow, or thunder starts or stops in the world the scene plays in. |

When a scene ends, scene actions still running stop, every role runs its **Wrap-Up**, and each NPC returns to its normal routine once its wrap-up is done. Wrap-ups are cut off after five minutes.

::: tip While Active reactions need an ending
With **When everyone is done**, the scene ends as soon as the last role's scene actions finish, so While Active reactions only work until then. Use another ending for scenes like a market that should stay open.
:::

## What happens to the NPCs

While an NPC plays a role:

- its route pauses and continues afterwards from where it would have gone next;
- its own **On Idle** and time-of-day routines (At Night … At Evening) wait;
- its other reactions run as usual, unless the role has a **While Active** row for that event.

An NPC plays in one scene at a time.

## Skipped scenes

A scene starts completely or not at all. It is skipped when a role has no free NPC because:

- every instance already plays in another scene or is in combat;
- no instance is loaded (not spawned, its chunk is not loaded, or, for automatic starts, it is in another world than the one whose time, weather, or area started the scene);
- the role has no preset, or the preset was deleted.

Administrators online get a short chat message saying which scene was skipped and why, for example *Scene 'Last Train Is Late' was skipped: Luigi is busy in scene 'Market by the Lake'.* The same message is repeated at most once a minute, and it is also written to the server log. Manual starts tell the player or console who started the scene. The scene editor shows the last skip reason.

Set `scenes.notify-skipped: false` in `config.yml` to stop the chat messages for automatic starts.

## Starting and stopping by hand

```text
/bf scene start western/last-train
/bf scene stop western/last-train
```

Both work from a player or the console, so quest plugins and command blocks can use them. **Stop** runs every role's wrap-up; stopping again during the wrap-up ends the scene at once.

NPC actions can do the same: **Start Scene** and **Stop Scene** are in the **Scenes** group of the action picker, along with **Give Cue** and **Wait For Cue**. Start Scene runs one tick later, so a scene's last action can start the next scene with the same NPCs.

The scene icon's tooltip shows these chains: **Leads to** lists the scenes its Start Scene actions start, and **Follows** lists the scenes that start it.

## Coming from custom events

Scenes replace custom events. On the first start after the update, Blockfolk converts them automatically:

- every custom event becomes a scene with the same name, without triggers and without cooldown;
- every preset that reacted to it becomes a role, and its reaction becomes the role's scene actions;
- **Emit Custom Event** actions become **Start Scene** actions for that scene;
- `/bf events trigger <event>` keeps working as `/bf scene start <event>`;
- `custom-events.yml` is renamed to `custom-events.yml.migrated`.

Two behaviours differ from custom events: an NPC that is already in a scene does not join a second one at the same time, and NPCs pause their route while they play a role.
