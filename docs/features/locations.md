# Locations

Global locations are named destinations shared across NPC presets. Open `/bf locations` directly, or open `/bf routes`
and choose **Manage Locations**.

## Create locations

1. Select **Edit Locations** to receive the location editor shard.
2. Left-click a block and enter a unique name in chat. Use `/` to organize it into groups, such as
   `Town/Market` or `Town/Shops/Forge`.
3. Repeat for additional locations.
4. Drop the shard to finish editing.

Shift-left-click an existing location to enter a new name and replace it. Right-click one to delete it. Saved locations
are highlighted in green during the session, with their names shown above the marked blocks when nearby.

## Manage locations

From the locations browser:

- left-click a location to teleport to it;
- middle-click to set or clear its icon from your main hand;
- shift-right-click to delete it.

Click a group to browse its locations. Custom location icons are also shown when choosing a saved location for a
**Move To** action. Click **Location Overview** to reorder all locations, then pick up and drop icons and save the new
order.

## How locations are used

**Move To** and **Teleport To** behaviour actions can target a saved location. AI perception includes up to 15 nearby locations in the same world within 64 blocks, ordered nearest first and exposed through safe request-local aliases rather than arbitrary coordinates.

Enable **Remember Location** in an NPC preset's **AI Behaviour** menu to let it save a unique label and X, Y, Z coordinates in its current world. The action can use the NPC's own coordinates or coordinates supplied in conversation. AI-created locations appear in the same location browser and action selector, with a diamond icon. Nearby players see an italic "NPC now knows about Location..." notice when one is saved. Existing names are preserved; the AI cannot overwrite a location.

Locations differ from routes: a location is one destination, while a route is a closed sequence of walking points.

![Blockfolk global locations browser](../screenshots/screenshot-locations-menu.jpeg)
