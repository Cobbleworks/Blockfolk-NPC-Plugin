# Commands & permissions

All Blockfolk commands require `blockfolk.admin`, granted to server operators by default.

| Command | Description |
| --- | --- |
| `/bf` | Open the NPC preset browser. |
| `/blockfolk` | Long alias for `/bf`. |
| `/bf create` | Begin creating a preset and enter its name in chat. |
| `/bf create <name>` | Create a preset with the supplied display name. |
| `/bf npc <name>` | Open a preset editor; names are tab-completed. |
| `/bf npc <name> spawn` | Spawn a persistent instance of the preset. |
| `/bf npc <name> duplicate` | Copy the preset and append ` (copy)` to its display name. |
| `/bf routes` | Open the route and global-location manager. |
| `/bf events` | Open the custom-event manager. |
| `/bf events trigger <name>` | Emit an existing custom event globally; also works from the console. |

## Permission

```yaml
permissions:
  blockfolk.admin:
    description: Allows creating, editing, and spawning Blockfolk NPCs.
    default: op
```

Grant the permission through your permissions plugin when non-operators should administer NPCs. Blockfolk currently uses one permission for all admin commands and GUI editing.
