# Custom events

Custom events let one NPC, a command, or an external workflow trigger behaviour on any active NPC configured to react. Unlike standard events such as **On Spawn** or **On Player Approach**, their names and meanings are defined by administrators.

## Manage events

Open **Custom Events** from the main `/bf` preset browser, or run `/bf events`.

Choose **Create Event** and enter a unique name. Names accept letters, numbers, `_`, and `-`; use `/` between segments to create browser groups such as `town/alarm` or `festival/opening`.

From the event browser:

Events are grouped by the NPCs whose actions emit them, including waypoint and question actions. An event emitted by several NPCs appears in each NPC group; events with no NPC emitter appear under **Unassigned**. Event name groups created with `/` remain available inside these groups.

- right-click an event to set or clear its description;
- middle-click to set its icon from your main hand;
- shift-right-click to delete it and remove NPC reactions referencing it;
- click **Event Overview** to enter **Reorder Custom Events**, then pick up and drop icons and save the new order.

## Emit an event

Add **Emit Custom Event** to a standard routine, custom-event response, question branch, or waypoint action, then select the event. When that action runs, the event is broadcast to all active Blockfolk NPC instances.

Administrators and automation can also emit an event with:

```text
/bf events trigger town/alarm
```

The trigger command works from a player or the server console.

## React to an event

Open an NPC preset and choose **Custom Event Behaviour**. Click **Add Event**, select an existing event or create one from the **Select Custom Event** screen, then build its ordered action row just like a standard behaviour routine. Only selected events appear as rows. Right-click a row's event icon to remove that row. Every active instance of that preset runs the row when the event is emitted.

One event can coordinate many presets. For example, `town/alarm` might make guards start combat, civilians flee, and a gatekeeper close a lever-controlled gate.

## Event chains

A custom-event response may emit another custom event, making reusable sequences possible. Blockfolk stops a chain after 64 emissions in one server tick to protect against accidental loops.

::: warning Deleting events
Deleting an event also removes reaction rows for that event from NPC presets. Actions elsewhere that emit its old name should be reviewed separately.
:::
