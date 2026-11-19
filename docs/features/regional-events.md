# Regional events

Regional events coordinate placed NPCs in a scene. Use them for a market, a patrol change, or an NPC encounter that players can discover in the world.

1. Stand at one corner of the area. Open **/bf → Events → New Region** and enter a name.
2. Stand at the opposite corner, reopen the region, and click **Corner 2**. The area covers the rectangle between those corners at every height.
3. Choose **New Event**, then **Add NPC**. Each NPC has an ordered list of actions, using the same editor as route waypoints. Use **Copy or Paste Actions** to reuse an existing action list.
4. Use **Test Now** to play the scene. Open the event again while it runs to stop it.
5. To start it automatically, pick **Player Enters** or one of the five **Time of Day** slots, then turn **Automatic Start** on.

NPCs in the same phase start together. Use **Add Then** to wait for every action list in the current phase to finish before starting the next one. For example, the station watcher walks to the saloon and announces that the train is late. In the next phase, the watcher and bartender both walk to the station.

A trigger starts an event only if every NPC assigned an action is present in the region's world and available. Each NPC can participate in one event at a time. When an event ends, Blockfolk releases the NPCs back to their routines. If an NPC disappears during an event, the event stops and releases its other participants.

**Player Enters** starts on a new arrival after the region has started watching for players. Player-entry and manual runs pass that player to actions such as **Open Shop**. A time-of-day run has no triggering player. Use actions such as **Send Dialog**, **Move To**, **Face Target**, and **Wait** for scenes that play on their own.

To give an NPC a route during an event, use **Set Route** or **Start Navigation** with a **Wait** to give it time to walk. If you use **AI Trigger**, a **Wait** gives the NPC time to receive and act on the response before the phase ends.

## Start from an action or command

Choose **Emit Event** in an NPC routine, waypoint action, or question branch, then select an event. The NPCs assigned to the new event must be available. If the same NPCs should continue a scene, use **Add Then** to keep their actions in one event.

To start an event from the console or in game, enter its region and event name:

```text
/bf events trigger Western Town/Late Train
```

## Existing custom events

Saved global signals and preset responses still work. On servers with saved custom events, **Events → Legacy Events** opens their settings, and the NPC preset's **Legacy Event Responses** menu edits its responses. Existing **Emit Event** actions and `/bf events trigger <name>` commands keep their saved targets. Create new events from a region in the **Events** menu.
