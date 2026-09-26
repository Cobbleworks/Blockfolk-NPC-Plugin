# Commands & permissions

`/bf` and `/blockfolk` open the NPC preset browser. NPC administration requires `blockfolk.admin`, granted to server operators by default. Players can use `/bf config ai mute-me` without that permission.

| Command | Description |
| --- | --- |
| `/bf`, `/bf npc` | Open the NPC preset browser. |
| `/bf locations` | Open global locations. |
| `/bf routes` | Open routes. |
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
| `/bf config ai model <model>` | Save and immediately use the OpenRouter model. Also works from the console. |
| `/bf config ai mute-me <on\|off>` | Stop or allow nearby AIs hearing your chat. Off by default; saved per player. |

NPC names in commands are stable preset keys, shown in the editor. Saved locations are global location keys. Tab completion lists both.

Existing `/bf create [name]`, `/bf events [trigger <event>]`, and `/bf npc <name> duplicate` commands remain available. `/bf events trigger` also works from the console.
