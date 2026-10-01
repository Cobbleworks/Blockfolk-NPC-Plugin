# Combat

Combat is configured per preset under **Fighting & Survival**.

<div class="screenshot-grid">
  <img src="../screenshots/screenshot-combat-menu.jpeg" alt="Blockfolk combat configuration menu">
  <img src="../screenshots/screenshot-combat-target-menu.jpeg" alt="Blockfolk combat target and aggression menu">
</div>

## Health and respawning

- Maximum health ranges from `0` to `1024` and changes in steps of 5 (shift-click uses ten steps).
- A value of `0` makes the NPC invulnerable.
- Respawn time changes in 10-second steps. `0` disables respawning.
- A respawning NPC needs a preset spawnpoint.
- Dropped experience changes in steps of 5.
- The optional boss bar is visible to players within 16 blocks.

![Adjusting an NPC combat respawn time](../screenshots/screenshot-combat-respawn.jpeg)

Pending combat respawns survive server restarts and keep the persistent instance UUID.

## Aggression

| Mode | Behaviour |
| --- | --- |
| Ignore | Does not retaliate or seek targets. |
| Fights Back | Retaliates after being attacked. |
| Flee | Moves away after being attacked. |
| Hunting | Proactively seeks enabled target categories. |

Target categories are non-animal mobs, animals, survival/adventure players, and other vulnerable Blockfolk NPCs.

## Fighter attacks

Open **Fighting & Survival → Fighter Attacks** to assign attacks from the shared **Fighters** library and adjust how often the NPC uses them. The library provides editable spells, fire breath, a sonic beam, a defensive shockwave, and a short reposition blink. Build custom attacks with origins, shapes, instant or delayed casts, cooldowns, damage, combined effects, and particle themes.

See [Fighters](/features/fighters) for the attack editor, templates, and casting rules. Attacks require active combat and maximum health above zero. Each instance has independent cooldowns and mixes assigned attacks with its weapon combat. Existing assignments to the original eight special attacks remain valid.

**Change Fight Options** includes the same attack assignment screen, so behaviours, waypoint actions, and question branches can temporarily change the NPC's attacks and usage interval.

## Alliances

NPCs with the same non-empty alliance value do not fight each other. Use a consistent spelling across the presets
that should cooperate. A player can appear allied to an NPC by carrying an item whose custom display name matches
that NPC's alliance. The comparison ignores capitalization and checks every inventory slot; allied NPCs will neither
choose that player as a target nor retaliate against them.

## Runtime combat actions

**Start Combat** starts an encounter from a behaviour routine. **Change Fight Options** temporarily changes aggression, target categories, and special attacks, allowing a routine to switch stance without modifying the stored combat profile.

AI combat is separately gated by the preset's enabled AI capabilities. The response still passes validated nearby targets only.

If the main hand and off hand contain a melee weapon and a bow or crossbow, the NPC uses the bow beyond three blocks and switches to melee within three blocks. The equipped hands return to their configured order when combat ends.

## Loot and experience

Experience is dropped when a vulnerable NPC dies. Items come from the independently rolled loot slots configured in [Customization & equipment](/features/customization).
