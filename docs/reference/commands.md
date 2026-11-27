# Commands & permissions

`/bf` and `/blockfolk` open the NPC preset browser. NPC administration requires `blockfolk.admin`, granted to server operators by default. Players can use `/bf config ai mute-me` without that permission.

| Command | Description |
| --- | --- |
| `/bf`, `/bf npc` | Open the NPC preset browser. |
| `/bf locations` | Open global locations. |
| `/bf routes` | Open routes. |
| `/bf scenes` | Open the scene browser. |
| `/bf scene <scene>` | Open a scene's script. |
| `/bf scene start <scene>` | Start a scene now. Skip reasons are shown to you. Also works from the console. |
| `/bf scene stop <scene>` | End a running scene with its wrap-up; again to end it at once. Also works from the console. |
| `/bf abilities` | Open the shared attack library and editor. |
| `/bf shops` | Open the shared shop library. |
| `/bf shop` | Open the shared shop library. |
| `/bf shop create <name>` | Create a shop and open its trade editor. |
| `/bf shop <shop>` | Open a shop's trade editor. |
| `/bf shop <shop> rename <name>` | Rename a shop; the name is the shop screen's title. |
| `/bf shop <shop> preview` | Open the shop for yourself as players see it. |
| `/bf shop <shop> restock` | Restock the shop's limited trades now; a one-page-per-day shop also turns to its next page. |
| `/bf npc <name> edit` | Open the NPC editor. |
| `/bf npc <name> set spawnpoint [here\|location]` | Set the preset spawnpoint to your position or a saved location. |
| `/bf npc <name> set name <display name>` | Rename the NPC. |
| `/bf npc <name> set color <color>` | Set the NPC name color. |
| `/bf npc <name> set pickupitems <on\|off>` | Toggle dropped item pickup. |
| `/bf npc <name> set health <0–1024>` | Set maximum health; `0` makes the NPC invulnerable. |
| `/bf npc <name> tp here` | Move the first spawned instance to you. |
| `/bf npc <name> tp to` | Teleport to the first spawned instance. |
| `/bf npc <name> tp toloc <location>` | Move the first spawned instance to a saved location. |
| `/bf npc <name> inventory` | Open the preset's temporary inventory. |
| `/bf npc <name> memory <on\|off\|open\|clear>` | Configure or inspect AI memory. `clear` erases saved facts and runtime memory. |
| `/bf npc <name> events` | Open the NPC's event behaviour editor. |
| `/bf npc <name> combat` | Open Fighting & Survival. |
| `/bf npc <name> equipment` | Open Equipment & Loot. |
| `/bf npc <name> delete` | Delete the preset, its instances, and its owned routes. |
| `/bf npc <name> spawn [here\|location]` | Spawn a persistent instance at your position or a saved location. |
| `/bf npc <name> duplicate [new name]` | Copy the preset under a new name, which also becomes the copy's key. Without a name, you are asked in chat. |
| `/bf config ai model [model]` | Without a model, show the one in use. With a model, save and immediately use it. Also works from the console. |
| `/bf config ai reasoning [none\|minimal\|low\|medium\|high]` | Without a value, show the reasoning effort in use. With a value, save and immediately use it. Also works from the console. |
| `/bf config chunkloaders` | List all NPC presets with Keep Chunks Loaded enabled, including presets without spawned instances. Click an entry to open its NPC editor. Also works from the console as a text list. |
| `/bf config ai mute-me <on\|off>` | Stop or allow nearby AIs hearing your chat. Off by default; saved per player. |

NPC names in commands are stable preset keys, made from the name a preset was created with. Commands also accept a preset's current name in the same form (lowercase, spaces as dashes), so a renamed "Town Baker" called "Town Florist" answers to both `town-baker` and `town-florist`. An exact key always wins; if several presets share a name, the command lists their keys to choose from. Saved locations are global location keys. Tab completion lists keys, current names, and locations.

Existing `/bf create [name]` commands remain available. The former custom-event commands still work: `/bf events` opens the scene browser and `/bf events trigger <event>` starts the scene that event became.
