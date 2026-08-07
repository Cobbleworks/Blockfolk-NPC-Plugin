# Locations

Global locations are named destinations shared across NPC presets. Open `/bf routes`, then choose **Locations**.

## Create locations

1. Select **Edit Locations** to receive the location editor shard.
2. Left-click a block and enter a unique name in chat.
3. Repeat for additional locations.
4. Drop the shard to finish editing.

Right-click an existing location while editing to delete it. Saved locations are highlighted in green during the session.

## Manage locations

From the locations browser:

- left-click a location to teleport to it;
- middle-click to set or clear its icon from your main hand;
- shift-right-click to delete it.

## How locations are used

**Move To** and **Teleport To** behaviour actions can target a saved location. AI perception includes up to five nearby locations in the same world within 64 blocks, using safe request-local aliases rather than exposing arbitrary coordinates.

Locations differ from routes: a location is one destination, while a route is a closed sequence of walking points.

<div class="screenshot-placeholder">
  <strong>Screenshot coming later</strong><br>
  Suggested asset: <code>docs/screenshots/blockfolk-locations.png</code>
</div>
