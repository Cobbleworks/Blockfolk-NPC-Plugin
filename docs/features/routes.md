# Routes

Routes are walking loops owned by individual NPC presets. Open the route manager with `/bf routes` to browse and edit them.

## Create a route

1. Open an NPC preset, add a **Set Route** action, then choose **Create Route** and enter a name in chat. Names are unique within that NPC.
2. Blockfolk selects the new route for that action and gives you a unique amethyst-shard route editor.
3. Left-click blocks to add points and shift-right-click points to remove them.
4. Drop the editor shard to save and return to that NPC’s **Behaviour** menu.

All points in one route must be in the same world. Points remain highlighted while editing.

## Point order

Point order is derived from position rather than placement order. An NPC starts at the point nearest to it, repeatedly visits the nearest unvisited point, then tries to close the loop from the final point back to the first. If a leg cannot be reached, it tries the nearest alternative waypoint from its current position and can reverse through the route. After all alternatives fail, it waits before retrying.

## Waypoint actions

Right-click a route point with the editor shard to attach an action sequence. These actions run when an NPC reaches that point and can pause, speak, interact, change movement, or invoke AI.

<div class="screenshot-grid">
  <img src="../screenshots/screenshot-route-editing.jpeg" alt="Editing a highlighted Blockfolk route in the world">
  <img src="../screenshots/screenshot-waypoint-actions.jpeg" alt="Actions configured on a route waypoint">
</div>

## Assign and control routes

Use these behaviour actions:

- **Set Route** selects one of that NPC’s routes. Left-click a route to select it, or right-click to edit its points;
- **Start Navigation** begins or resumes movement;
- **Stop Navigation** pauses it;
- **Set Walk Speed** chooses Slouch, Slow, Normal, Fast, or Very Fast.

AI can also start or pause the configured route when those capabilities are enabled.

## Route browser tools

The root route browser groups routes under folders named after their owning NPC presets, whether or not an action currently uses them. Legacy routes with no owner remain visible at the root. Older shared routes are copied for each NPC that referenced them when the plugin loads.

NPC folders follow the saved NPC preset order. Routes within each folder follow the saved route order. Deleting an NPC preset also deletes its routes.

To customize route order, click **Route Overview** at the bottom of the route browser. In **Reorder Routes**, pick up and drop route icons, then choose **Save Order**. Middle-click a route to use your main-hand item as its browser icon. Deleting a route removes direct and question-branch references and unassigns affected presets.
