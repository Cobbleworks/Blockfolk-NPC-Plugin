# Scenes

A scene is a moment several NPCs play together: the station watcher checks the tracks, walks to the saloon to fetch Luigi, and the two walk back together. Each NPC taking part plays a **role**. The scene's **script** runs in **beats**: in each beat, every role plays its actions for that beat at the same time, and when the beat is done the next one starts.

Scenes can start on their own (at a time of day, when the weather changes, or when a player walks into the scene's area) or by hand with a command or the **Start Scene** action.

| Example | How it is built |
| --- | --- |
| **Last Train Is Late** · western town | Starts at **Evening**. Beat 1: the watcher *Moves To* the station and says the train is late. Beat 2: he *Moves To* the saloon. Beat 3: he tells Luigi. Beat 4: Luigi answers. Beat 5: both *Move To* the station. |
| **Market by the Lake** · jungle | Starts at **Noon**, ends at **Evening**. The shopkeeper and smith *Move To* the lake. Wägö *Waits* two minutes first, and while the scene runs his **On Player Approach** opens his shop. |
| **Champion's Exhibition** · desert city | Starts when a **player enters the area** around the colosseum. Beat 1: Aurelion and his training partner walk in. Beat 2: Aurelion addresses the crowd. Beat 3: the sparring starts. |
| **Snowed In** · snowland | Starts when **snow starts** where Plinfa is and ends **when the weather changes**. Plinfa walks indoors, and while the scene runs her **On Player Approach** calls out to passing players. |

## Create a scene

Open **Scenes** from the main `/bf` menu or run `/bf scenes` to see every scene. **Scenes** in an NPC's editor shows only the scenes that NPC plays in, and **Create Scene** there starts a scene with that NPC already cast. Choose **Create Scene** and enter a name. Use `/` to group scenes, for example `western/last-train`.

The scene editor has four columns:

- **Scene**: name and description. The description opens a multi-line text box; NPCs with [AI behaviour](./ai-behaviour.md) get it as context while they play the scene, so describe the situation rather than giving orders. Middle-click the scene icon at the top to use the item in your hand as its icon.
- **When**: the triggers, and whether they are switched on.
- **Cast & Ending**: the **Script** with the roles and beats, and when the scene ends.
- **Playback**: start or stop the scene right now, the chance that a trigger really starts it, and the cooldown before triggers may start it again.

## Script

**Script** opens a grid: every row is a beat, every column a role. The top row holds the roles, and the first column the beats.

```text
            Cranky      Luigi       Toad
 Beat 1     3 actions   ·           1 action
 Beat 2     ·           2 actions   ·
 Beat 3     1 action    1 action    ·
```

- Click a cell to edit what that NPC does in that beat. Hover over a head to see its actions; a grey cell means the NPC holds still while the others play. In the beat editor, the arrows step to the same NPC's previous or next beat.
- **Add Beat** in the bottom bar adds a beat at the end. The arrows page through beats, and through roles when there are more than eight.
- Click a beat to choose when the next beat starts, or to duplicate or delete it. Shift-left-click a beat to move it up, shift-right-click to move it down.
- The beat playing right now glows while the scene runs.

### Roles

Choose **Add Role** in the top row, then pick the NPC preset that plays it. Click a role's head in the top row to open its role editor, and shift-right-click it to remove the role together with its actions. The role editor holds what applies to the whole scene, in rows built exactly like [event behaviour](./behaviours.md):

| Row | Runs |
| --- | --- |
| **Wrap-Up** | When the scene ends, before the NPC goes back to its normal routine. |
| **While Active: …** | Instead of the preset's own reaction while the scene runs: On Idle, On Right-Click, On Player Approach, On Player Leaves, On Left-Click, On Receive Item, On Nearby NPC, and On Damage Taken. An empty row keeps the preset's own reaction. **While Active: On Idle** repeats once the role has played its part in every beat. |

NPCs play themselves: a role has no name of its own and is shown with its NPC's name. **Played By** in the bottom bar of the role editor swaps the NPC.

Every free instance of the NPC plays the role. Usually there is just one; filler NPCs with several instances all do the same thing. If an NPC should act differently from the others, make it a separate preset. A role needs at least one free instance, otherwise the scene is skipped (see [Skipped scenes](#skipped-scenes)).

An NPC preset plays at most one role per scene; the role picker greys out NPCs that already have one. That role can act in as many beats as needed.

### Beats

Each beat holds at most seven actions per role, and a scene holds up to 64 beats. When a beat starts, every role with actions in it starts them at the same time. Choose when the next beat starts:

| Next beat starts | Use it for |
| --- | --- |
| **When everyone is done** (default) | Most beats: walking somewhere, talking, acting. |
| **After a set time** | Fixed pacing, counted from the start of the beat. |
| **When one role is done** | Moving on without waiting for slow filler NPCs. |
| **Only by Next Beat** | Waiting for a player, see below. |

When a beat moves on, actions still running in it are cut off. **Move To** waits until the NPC arrives (at most 30 seconds), and **Send Dialog** and **Show Holo Dialog** wait until the line had time to be read, so a beat of dialogue moves on by itself once the last line was read.

The NPCs stay in the scene from the first beat to the last. Between beats they are not handed back to their routine, so no other scene can take them halfway through.

### Waiting for a player

The **Next Beat** action moves the scene on to its next beat. Put it in a **While Active** row, for example **While Active: On Right-Click**, or in a question answer, and set the beat to **Only by Next Beat**: the scene then waits until a player talks to the NPC. Several Next Beat actions in the same beat move the scene on only once. Outside a scene, Next Beat does nothing.

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
| **After the last beat** | The last beat is done. This is at most 30 minutes after the start. |
| **After a set time** | The chosen time has passed. |
| **At a time of day** | The chosen part of the day begins. |
| **When the weather changes** | Rain, snow, or thunder starts or stops in the world the scene plays in. |

When a scene ends, beat actions still running stop, every role runs its **Wrap-Up**, and each NPC returns to its normal routine once its wrap-up is done. Wrap-ups are cut off after five minutes.

::: tip While Active reactions need an ending
With **After the last beat**, the scene ends as soon as its last beat is done, so While Active reactions only work until then. With any other ending, the NPCs stay in the scene after the last beat until it ends. Use that for scenes like a market that should stay open.
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

NPC actions can do the same: **Start Scene** and **Stop Scene** are in the **Scenes** group of the action picker, along with **Next Beat**. Start Scene runs one tick later, so a scene's last action can start the next scene with the same NPCs. For steps of the same moment, use beats instead of a follow-up scene.

The scene icon's tooltip shows these chains: **Leads to** lists the scenes its Start Scene actions start, and **Follows** lists the scenes that start it.

## Coming from custom events

Scenes replace custom events. On the first start after the update, Blockfolk converts them automatically:

- every custom event becomes a scene with the same name, without triggers and without cooldown;
- every preset that reacted to it becomes a role, and its reaction becomes the role's actions in the first beat;
- **Emit Custom Event** actions become **Start Scene** actions for that scene;
- `/bf events trigger <event>` keeps working as `/bf scene start <event>`;
- `custom-events.yml` is renamed to `custom-events.yml.migrated`.

Two behaviours differ from custom events: an NPC that is already in a scene does not join a second one at the same time, and NPCs pause their route while they play a role.

## Scenes from before beats

Scenes saved before beats existed load with every role's action row as **Beat 1**. **Give Cue** and **Wait For Cue** were removed: they are left out when such a scene loads. Put the steps that waited for a cue into the next beat instead. The ending **When everyone is done** is now called **After the last beat**.
