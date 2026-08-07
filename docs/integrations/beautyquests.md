# BeautyQuests integration

BeautyQuests is an optional soft dependency. When installed, every spawned Blockfolk NPC is available in the BeautyQuests NPC selector.

## Setup

1. Install compatible versions of Blockfolk and BeautyQuests.
2. Restart the server.
3. Create or edit a BeautyQuests quest.
4. Choose an NPC-based starter or stage, then select the Blockfolk NPC in the world.

No additional option in `config.yml` is required.

## Persistence

Quest dialogs, interaction stages, markers, and navigation pausing use the persistent Blockfolk instance UUID. Assignments therefore survive server restarts and combat respawns. BeautyQuests editor entries use the NPC's skinned player head as their icon.

::: warning Removing instances
BeautyQuests targets a specific spawned instance. Removing it also removes the entity that quest configuration refers to; spawning another copy of the same preset creates a different instance UUID.
:::
