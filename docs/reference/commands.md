# Commands & permissions

All Blockfolk commands require `blockfolk.admin`, granted to server operators by default.

| Command | Description |
| --- | --- |
| `/bf` | Open the NPC preset browser. |
| `/blockfolk` | Long alias for `/bf`. |
| `/bf config` | Show the active OpenRouter model, timeout, and token limit. Also works from the console. |
| `/bf config --model <id>` | Save a new model identifier and use it for subsequent AI requests. Also works from the console. |
| `/bf config --timeout-seconds <seconds>` | Save a request timeout of at least 2 seconds and apply it to subsequent requests. |
| `/bf config --max-tokens <count>` | Save an output limit of at least 350 tokens and apply it to subsequent requests. |
| `/bf create` | Begin creating a preset and enter its name in chat. |
| `/bf create <name>` | Create a preset with the supplied display name. |
| `/bf npc <name>` | Open a preset editor; names are tab-completed. |
| `/bf npc <name> edit` | Open the same preset editor explicitly. |
| `/bf npc <name> spawn` | Spawn a persistent instance of the preset. |
| `/bf npc <name> duplicate` | Copy the preset and append ` (copy)` to its display name. |
| `/bf routes` | Open the route and global-location manager. |
| `/bf locations` | Open the global-location manager directly. |
| `/bf events` | Open the custom-event manager. |
| `/bf events trigger <name>` | Emit an existing custom event globally; also works from the console. |

## Permission

```yaml
permissions:
  blockfolk.admin:
    description: Allows managing Blockfolk NPCs and plugin settings.
    default: op
```

Grant the permission through your permissions plugin when non-operators should administer NPCs. Blockfolk currently uses one permission for all admin commands and GUI editing.
