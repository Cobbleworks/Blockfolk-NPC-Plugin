# BeautyQuests integration

BeautyQuests is an optional soft dependency. When installed, every spawned Blockfolk NPC is available in the BeautyQuests NPC selector.

## Setup

1. Install compatible versions of Blockfolk and BeautyQuests.
2. Restart the server.
3. Create or edit a BeautyQuests quest.
4. Choose an NPC-based starter or stage.
5. When BeautyQuests asks for an NPC, left-click the spawned Blockfolk NPC directly.

No additional option in `config.yml` is required.

Unlike the Citizens selection flow, Blockfolk selection does not require a stick or another tool. Shift-right-click remains reserved for Blockfolk administrators opening the NPC editor.

BeautyQuests receives clicks before Blockfolk runs the NPC's own click actions. If BeautyQuests consumes a click for selection, a quest starter, or a stage, that click does not also run the NPC's normal behaviour. Other clicks still run its configured actions. Left-click selection also works on invulnerable NPCs.

Quest dialogs temporarily pause route movement, following, walking to a destination, and combat movement (including chasing, fleeing, retreating, dashes, and blinks). This pauses navigation rather than making the NPC invulnerable or disabling its attacks.

## Persistence

Quest dialogs, interaction stages, markers, and navigation pausing use the persistent Blockfolk instance UUID. Assignments therefore survive server restarts and combat respawns. BeautyQuests editor entries use the NPC's skinned player head as their icon.

::: warning Removing instances
BeautyQuests targets a specific spawned instance. Removing it also removes the entity that quest configuration refers to; spawning another copy of the same preset creates a different instance UUID.
:::
