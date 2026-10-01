# Abilities

**Abilities** is the shared ability library for NPC combat. Open it from the **Abilities** button in `/bf`, or use `/bf abilities`. Creating and editing attacks requires `blockfolk.admin`.

## Build and assign an attack

1. Open **Abilities → Create Ability** and choose a starting template.
2. Enter a name. Configure the attack using **Shape & Origin**, **Timing**, **Damage & Effects**, and **Visuals**.
3. Click **Preview** to see the shape from your position and view direction. Preview only displays particles and sound.
4. Open an NPC's **Fighting & Survival → Abilities → Assign Ability** and select an attack from the library. It is enabled immediately and appears in **Assigned Abilities**. Left-click an assigned attack to edit its shared definition; right-click to remove its assignment.
5. Set **Max Health** above zero and configure aggression or a **Start Combat** action.

Attack definitions are shared. Editing a definition changes it for every NPC assigned that attack. Use **Duplicate** to create a separate variant. Renaming keeps existing assignments. Shift-right-click an attack in the library to delete it, or use **Delete Ability** in its overview. Deleting requires confirmation; NPCs stop using it, and its missing assignment can be removed in the assignment screen.

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

NPC spheres follow the caster throughout the delay. Target spheres keep the marked position. Delayed cones and beams lock their aim when casting begins, so moving sideways can avoid them. Charged abilities aim when the successful weapon hit releases them. The shape selector uses distinct icons for spheres, cones, beams, and teleport. A sphere hits eligible entities inside its radius, a cone hits eligible entities in front, and a beam hits eligible entities along its width. All offensive shapes check line of sight and alliance/target rules.

Teleport checks loaded chunks, world bounds, the world border, clear space for the NPC, solid ground, and common landing hazards. It searches nearby positions with limited vertical changes. If no safe destination exists, the NPC stays in place. Teleport preserves the NPC's preset spawnpoint and rebuilds its navigator from the new position. Teleport does not apply damage or effects to other entities.

## Timing

- **Cast Mode:** click to cycle **Instant → Delayed → On Next Attack**. Instant releases immediately. Switching to Delayed applies a one-second default delay.
- **Cast Delay:** adjustable in instant/delayed mode, up to 10 seconds. Setting it to `0` selects Instant. A delayed cast pauses movement and weapon combat, shows the affected shape, and surrounds the caster with particles.
- **On Next Attack:** surrounds the NPC with themed particles and enchantment glyphs while letting it move and use weapons. The next valid melee or projectile hit that removes health or absorption consumes the charge and releases the ability towards that victim's current position. Missed, cancelled, and fully blocked hits leave the charge armed. Charges expire after 30 seconds and are cleared when combat ends, the opponent changes, or the NPC disappears.
- **Cooldown:** 1–120 seconds, counted after the cast delay. Charged abilities restart their cooldown when released. Each instance maintains its own cooldown for each ability, including across opponent changes.
- **Usage Interval:** configured on the NPC assignment screen, from 3–60 seconds, defaulting to about 8 seconds. It varies by up to 25%, with a three-second minimum. Left-click the interval clock to decrease it by one second, right-click to increase it, or hold Shift to change it by five seconds. Ready attacks are chosen randomly; weapon combat continues between casts.

Changing or deleting a definition during a cast interrupts that cast. Leaving combat, changing opponents, losing a valid target, or unassigning the attack also prevents its pending impact.

## Damage and effects

Damage uses health points: **2 HP = 1 heart**. Configure damage from 0 to 100 HP. A zero-damage attack can still apply effects. Armour, resistance, immunity, and cancelled damage events affect normal damaging hits. A charged ability permits its bonus damage through the triggering hit’s brief hurt-immunity window, then restores that window; damage protection and resistance still apply.

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

The **Particles** selector changes its icon and description for the selected theme. Choose Flame, Sonic, Soul, Ice, Poison, Cloud, Blood, Lightning, Ender, Enchant, Hearts, Smoke, Soul Flame, Bubbles, Spores, or Totem particles. These themes change the visuals; configure damage and gameplay effects separately. Sonic uses the Warden's sonic boom particles and sound; its damage and shape are configured by the attack definition. Lightning is a visual strike; the NPC owns the configured damage.

Numeric controls use **left-click to decrease**, **right-click to increase**, **shift-click for five steps**, and **middle-click to enter a number**. Values are bounded to the supported ranges. Edits save immediately.

## Behaviours and storage

**Use Ability** is available in ordinary and custom event routines, waypoint actions, and question branches. It opens an ability selector and stores the chosen ability's stable key. The NPC can cast it without assigning it for random combat use. Cooldowns are shared with automatic casts, and an NPC can have one delayed cast or charge at a time.

Targeted abilities use the current combat opponent, otherwise a valid triggering actor, otherwise the nearest selected target in sight. They require a target within activation range. NPC-centred spheres, teleport, and charging can run without an opponent, including on invulnerable NPCs. Collateral targets still follow alliance and target-category rules. Missing definitions or unavailable casts are skipped. A delayed action waits for its cast before the sequence continues; an instant or charge action continues immediately. For example, **Use Ability (On Next Attack) → Start Combat** arms the NPC before engaging the triggering actor.

**Change Fight Options → Abilities** uses the same assignment screen for behaviour routines, waypoint actions, and question branches. It temporarily replaces the NPC's assigned attacks and usage interval.

Shared definitions are saved in `plugins/Blockfolk/abilities.yml`. Existing `fighters.yml` data is loaded and copied into the new file automatically; the old file is preserved. Once `abilities.yml` exists, it is used in preference to the old file. Assignments are saved with the NPC combat profile or behaviour action. Existing assignments to the original eight special attacks remain valid. The library is seeded on first use; deleted templates are not recreated on restart.
