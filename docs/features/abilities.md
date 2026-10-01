# Abilities

**Abilities** is the shared ability library for NPC combat. Open it from the **Abilities** button in `/bf`, or use `/bf abilities`. Creating and editing attacks requires `blockfolk.admin`.

## Build and assign an attack

1. Open **Abilities → Create Ability** and choose a starting template.
2. Enter a name. Configure the attack using **Shape & Origin**, **Timing**, **Damage & Effects**, and **Visuals**.
3. Click **Preview** to see the shape from your position and view direction. Preview only displays particles and sound.
4. Open an NPC's **Fighting & Survival → Abilities**. Left-click attacks to assign or unassign them.
5. Set **Max Health** above zero and configure aggression or a **Start Combat** action.

Attack definitions are shared. Editing a definition changes it for every NPC assigned that attack. Use **Duplicate** to create a separate variant. Renaming keeps existing assignments. Deleting an attack requires confirmation; NPCs stop using it, and its missing assignment can be removed in the assignment screen.

## Custom icons

Hold the desired item in your main hand, then hover an ability in the library, the NPC assignment screen, or its overview and press **Q / Drop**. The item is copied as the ability's icon, including its custom appearance; it is not consumed or dropped. Use an empty main hand and press Q again to restore the default icon. Icons are saved, preserved when you edit other settings, and copied when you duplicate an ability.

## Origins and shapes

| Shape | Origin and area | Typical use |
| --- | --- | --- |
| Sphere | Choose **NPC** for an area around the caster, or **Target** to mark the opponent's position when casting starts. The radius controls its size. | Defensive shockwave, poison field, life drain. |
| Cone | Starts at the NPC's eye position and points towards the opponent. Cone length and angle control the affected volume. | Fire breath or a spreading curse. |
| Beam | Starts at the NPC's eye position. Beam radius controls width, and blocks shorten the beam. | Warden-style sonic blast or a narrow laser. |
| Teleport | Repositions the NPC to a nearby safe location within the blink distance. | A short evasive blink. |

**Activation Range** controls how close the opponent must be before the NPC chooses that attack. For beams, it also controls the length of the attack. **Cone Length** is a separate setting from 1–24 blocks; changing it does not change activation range. Sphere radius is separate, so a four-block defensive area can have a different activation range.

NPC spheres follow the caster throughout the delay. Target spheres keep the marked position. Cones and beams lock their aim when casting begins, so moving sideways can avoid them. A sphere hits eligible entities inside its radius, a cone hits eligible entities in front, and a beam hits eligible entities along its width. All offensive shapes check line of sight and alliance/target rules.

Teleport checks loaded chunks, world bounds, the world border, clear space for the NPC, solid ground, and common landing hazards. It searches nearby positions with limited vertical changes. If no safe destination exists, the NPC stays in place. Teleport preserves the NPC's preset spawnpoint and rebuilds its navigator from the new position. Teleport does not apply damage or effects to other entities.

## Timing

- **Cast Delay:** `0` fires instantly. Delays up to 10 seconds pause movement and weapon combat and show a shape-specific warning.
- **Cooldown:** 1–120 seconds, counted after the cast delay. Each instance maintains its own cooldown for each attack, including across opponent changes.
- **Usage Interval:** configured on the NPC assignment screen, from 3–60 seconds, defaulting to about 8 seconds. It varies by up to 25%, with a three-second minimum. Ready attacks are chosen randomly; weapon combat continues between casts.

Changing or deleting a definition during a cast interrupts that cast. Leaving combat, changing opponents, losing a valid target, or unassigning the attack also prevents its pending impact.

## Damage and effects

Damage uses health points: **2 HP = 1 heart**. Configure damage from 0 to 100 HP. A zero-damage attack can still apply effects. Armour, resistance, immunity, and cancelled damage events affect normal damaging hits.

Toggle any combination of these effects:

| Effect | Behaviour |
| --- | --- |
| Fire | Ignites the victim for the configured duration. |
| Poison, Slowness, Wither, Blindness, Weakness | Applies the selected potion effect at the configured duration and level. |
| Life Drain | Heals the caster by damage actually dealt, up to its maximum health. |
| Knockback | Pushes victims away from the caster using the configured strength. |

Potion duration ranges from 1–30 seconds and level from I–V. Knockback strength ranges from 0–2.5. Slowness also affects native NPC navigation; Weakness affects their melee damage.

All victims must be attackable. The current opponent is eligible; collateral victims must also match an enabled target category. Allied NPCs, allied players, creative/spectator players, and navigation helpers are excluded. Cancelled or fully resisted damaging hits prevent their secondary effects. Attacks with effects only send a cancellable zero-damage protection event before applying effects. Attacks leave blocks unchanged and do not summon entities.

## Templates and visuals

The library initially contains **11 editable templates**: Life Drain, Freezing Spell, Poison Spit, Wither Curse, Flame Burst, Lightning Mark, Defensive Shockwave, Fear, Fire Breath, Sonic Blast, and Reposition Blink.

Choose Flame, Sonic, Soul, Ice, Poison, Cloud, Blood, Lightning, or Ender particles. Sonic uses the Warden's sonic boom particles and sound; its damage and shape are configured by the attack definition. Lightning is a visual strike; the NPC owns the configured damage.

Numeric controls use **left-click to increase**, **right-click to decrease**, **shift-click for five steps**, and **middle-click to enter a number**. Values are bounded to the supported ranges. Edits save immediately.

## Behaviours and storage

**Change Fight Options → Abilities** uses the same assignment screen for behaviour routines, waypoint actions, and question branches. It temporarily replaces the NPC's assigned attacks and usage interval.

Shared definitions are saved in `plugins/Blockfolk/abilities.yml`. Existing `fighters.yml` data is loaded and copied into the new file automatically; the old file is preserved. Once `abilities.yml` exists, it is used in preference to the old file. Assignments are saved with the NPC combat profile or behaviour action. Existing assignments to the original eight special attacks remain valid. The library is seeded on first use; deleted templates are not recreated on restart.
