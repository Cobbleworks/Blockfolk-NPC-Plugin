# Abilities

**Abilities** is the shared ability library for NPC combat. Open it from the **Abilities** button in `/bf`, or use `/bf abilities`. Creating and editing attacks requires `blockfolk.admin`.

## Build and assign an attack

1. Open **Abilities → Create Ability** and choose a starting template.
2. Enter a name. Configure the attack using the tabs along the top: **Shape & Range**, **Timing & Triggers**, **Damage & Effects**, and **Visuals**. The open tab glows.
3. Click **Preview** to see the shape from your position and view direction. Preview only displays particles and sound.
4. Open an NPC's **Fighting & Survival → Abilities → Assign Ability** and select an attack from the library. It is enabled immediately and appears in **Assigned Abilities**. Left-click an assigned attack to edit its shared definition; right-click to remove its assignment.
5. Set **Max Health** above zero and configure aggression or a **Start Combat** action.

Attack definitions are shared. Editing a definition changes it for every NPC assigned that attack. Use **Duplicate** to create a separate variant. Renaming keeps existing assignments. Shift-right-click an attack in the library to delete it, or use **Delete Ability** in its overview. Deleting requires confirmation; NPCs stop using it, and its missing assignment can be removed in the assignment screen.

Choices such as shape, cast mode, trigger, and visual theme are shown side by side. Click one to select it; the selected option glows and is labelled **✔ Selected**. Each tab only shows the settings that apply to the chosen shape.

## Custom icons

Hold the desired item in your main hand, then hover an ability in the library, the NPC assignment screen, or its overview and press **Q / Drop**. The item is copied as the ability's icon, including its custom appearance; it is not consumed or dropped. Use an empty main hand and press Q again to restore the default icon. Icons are saved, preserved when you edit other settings, and copied when you duplicate an ability.

## Shapes

| Shape | Area | Typical use |
| --- | --- | --- |
| Sphere | A round area. Centre it on the **Caster** or on the **Target**, which marks the opponent's position when casting starts. | Defensive shockwave, poison cloud, life drain. |
| Ring | A hollow sphere between the **Safe Inner Radius** and the **Outer Radius**. Entities close to the centre are not hit. It can be centred on the caster or the target. | A frost nova that punishes kiting, or a cage around the target. |
| Cone | Starts at the NPC's eyes and points towards the opponent. **Cone Length** and **Cone Angle** control the volume. | Fire breath or a spreading curse. |
| Beam | Starts at the NPC's eyes. **Beam Radius** controls width, and blocks shorten the beam. | Sonic blast or a searing ray. |
| Chain | Strikes the opponent, then jumps to the nearest eligible entity within **Jump Radius** of the previous victim, up to **Chain Targets** victims in total. | Chain lightning against groups. |
| Dash | The NPC rushes towards the opponent, stopping 1.5 blocks in front of it or before a wall. Entities within **Path Radius** of its path are hit. | A gap-closing charge. |
| Teleport | Repositions the NPC to a nearby safe location within **Blink Distance**. | A short evasive blink. |
| Self | Affects only the caster. Only caster buffs apply. | A war cry or emergency heal. |

**Activation Range** controls how close the opponent must be before the NPC chooses that attack. For beams it is also the beam's length, and for dashes the furthest the NPC dashes. **Minimum Range** makes automatic casts wait until the opponent is at least that far away; use it so a dash closes distance instead of firing point-blank. **Cone Length** (1–24 blocks) is separate from activation range.

Caster-centred spheres and rings follow the NPC throughout the delay. Target-centred areas keep the marked position. Delayed cones, beams, and dashes lock their aim when casting begins, so moving sideways can avoid them; a dash telegraphs its path on the ground. Charged abilities aim when the successful weapon hit releases them. All offensive shapes check line of sight and alliance/target rules. Each chain jump also needs line of sight from the previous victim.

Teleport and Dash check loaded chunks, world bounds, the world border, clear space for the NPC, solid ground, and common landing hazards. Teleport searches nearby positions with limited vertical changes; if no safe destination exists, the NPC stays in place. A dash without a safe landing spot fizzles without hitting anyone. Both preserve the NPC's preset spawnpoint and rebuild its navigator from the new position.

## Timing and triggers

- **Cast Mode:** choose **Instant**, **Delayed**, or **On Next Attack**. Instant releases immediately. Choosing Delayed applies a one-second default delay.
- **Cast Delay:** adjustable in instant/delayed mode, up to 10 seconds. Setting it to `0` selects Instant. A delayed cast pauses movement and weapon combat, shows the affected shape, surrounds the caster with particles, and plays the same warning sound for every ability so players learn to react to it.
- **On Next Attack:** surrounds the NPC with themed particles and enchantment glyphs while letting it move and use weapons. The next valid melee or projectile hit that removes health or absorption consumes the charge and releases the ability towards that victim's current position. Missed, cancelled, and fully blocked hits leave the charge armed. Charges expire after 30 seconds and are cleared when combat ends, the opponent changes, or the NPC disappears.
- **Cooldown:** 1–120 seconds, counted after the cast delay. Charged abilities restart their cooldown when released. Each instance maintains its own cooldown for each ability, including across opponent changes.
- **Pulses:** spheres, rings, cones, and beams can linger and strike 1–10 times. The first strike happens on release; the rest follow every **Pulse Interval** (0.5–3 seconds) at the same spot and aim. Caster-centred areas follow the NPC. Lingering areas do not pause weapon combat, and leaving the area avoids later pulses. Caster buffs apply once, on release. Pulses end when combat ends, the NPC disappears, or the definition is edited or deleted.
- **Trigger:** controls when automatic combat may choose the ability: **Always**, **Caster below 50% health**, **Caster below 25% health**, or **Target below 50% health**. When the trigger of a ready ability is met, it takes precedence over abilities set to Always. Behaviour actions ignore triggers and minimum range.
- **Usage Interval:** configured on the NPC assignment screen, from 3–60 seconds, defaulting to about 8 seconds. It varies by up to 25%, with a three-second minimum. Left-click the interval clock to decrease it by one second, right-click to increase it, or hold Shift to change it by five seconds. Ready attacks are chosen randomly; weapon combat continues between casts.

Changing or deleting a definition during a cast interrupts that cast. Leaving combat, changing opponents, losing a valid target, or unassigning the attack also prevents its pending impact.

## Damage and effects

Damage uses health points: **2 HP = 1 heart**. Configure damage from 0 to 100 HP. A zero-damage attack can still apply effects. Armour, resistance, immunity, and cancelled damage events affect normal damaging hits. A charged ability permits its bonus damage through the triggering hit's brief hurt-immunity window, then restores that window; damage protection and resistance still apply.

Effects are grouped into three rows. Enabled effects glow and are labelled **✔ Enabled**. Toggle any combination:

| Group | Effects | Behaviour |
| --- | --- | --- |
| Afflictions | Fire, Poison, Wither, Weakness, Blindness, Darkness, Nausea, Hunger | Fire ignites victims; the others apply the potion effect at the configured duration and level. |
| Crowd Control | Slowness, Mining Fatigue, Levitation, Glowing | Applies the potion effect at the configured duration and level. |
| Crowd Control | Knockback, Pull, Launch | Knockback pushes victims away from the caster. Pull draws them towards the area's centre. Launch throws them upwards and can be combined with either. Knockback and Pull replace each other. |
| Caster Buffs | Life Drain | Heals the caster by damage actually dealt, up to its maximum health. |
| Caster Buffs | Regeneration, Speed, Strength, Resistance, Absorption | Applied to the caster when the ability is released, even if nothing is hit. |

**Effect Duration** ranges from 1–30 seconds and **Effect Level** from I–V; both apply to afflictions, potion crowd control, and caster buffs. **Force** ranges from 0–2.5 and controls Knockback, Pull, and Launch. Slowness also affects native NPC navigation; Weakness and Strength affect their melee damage. Undead NPC entity types are immune to Poison and Regeneration, as in vanilla.

All victims must be attackable. The current opponent is eligible; collateral victims must also match an enabled target category. Allied NPCs, allied players, creative/spectator players, and navigation helpers are excluded. Cancelled or fully resisted damaging hits prevent their secondary effects. Attacks with effects only send a cancellable zero-damage protection event before applying effects. Attacks leave blocks unchanged and do not summon entities.

## Templates and visuals

There are **21 editable templates**. A new library is seeded with all of them; existing libraries keep their abilities, and every template is always offered through **Create Ability**. The classic templates are Life Drain, Freezing Spell, Poison Spit, Wither Curse, Flame Burst, Lightning Mark, Defensive Shockwave, Fear, Fire Breath, Sonic Blast, and Reposition Blink. The others show off the newer options:

| Template | Shows off |
| --- | --- |
| Frost Nova | Ring around the caster that slows victims outside melee range. |
| Chain Lightning | Chain that strikes up to four enemies. |
| Poison Cloud | Target sphere that lingers for five pulses. |
| Gravity Well | Lingering target sphere that pulls victims in and slows them. |
| Searing Ray | Beam that burns for four rapid pulses. |
| Shadow Dash | Dash with a minimum range that blinds the target. |
| Updraft | Caster sphere that launches nearby enemies. |
| Finishing Blow | On Next Attack strike, triggered when the target is below 50% health. |
| War Cry | Self ability granting Strength and Resistance when the caster is hurt. |
| Second Wind | Self ability granting Regeneration and Absorption as a last resort. |

The **Visuals** tab shows every particle theme: Flame, Sonic, Soul, Ice, Poison, Cloud, Blood, Lightning, Ender, Enchant, Hearts, Smoke, Soul Flame, Bubbles, Spores, Totem, Holy, Sculk, Cherry, Wind, and Glow. Click a theme to select it and hear its impact sound. Themes change particles and sound only; configure damage and gameplay effects separately. Sonic uses the Warden's sonic boom; Lightning spheres add a visual lightning strike, and the NPC owns the configured damage.

Numeric controls use **left-click to decrease**, **right-click to increase**, **shift-click for five steps**, and **middle-click to enter a number**. Each control lists its allowed range, and values are bounded to it. Edits save immediately.

## Behaviours and storage

**Use Ability** is available in ordinary and custom event routines, waypoint actions, and question branches. It opens an ability selector and stores the chosen ability's stable key. The NPC can cast it without assigning it for random combat use. Cooldowns are shared with automatic casts, and an NPC can have one delayed cast or charge at a time.

Targeted abilities use the current combat opponent, otherwise a valid triggering actor, otherwise the nearest selected target in sight. Target-centred areas, cones, beams, chains, and dashes require a target within activation range. Caster-centred spheres and rings, teleport, Self abilities, and charging can run without an opponent, including on invulnerable NPCs. Collateral targets still follow alliance and target-category rules. Missing definitions or unavailable casts are skipped. A delayed action waits for its cast before the sequence continues; an instant or charge action continues immediately. For example, **Use Ability (On Next Attack) → Start Combat** arms the NPC before engaging the triggering actor.

**Change Fight Options → Abilities** uses the same assignment screen for behaviour routines, waypoint actions, and question branches. It temporarily replaces the NPC's assigned attacks and usage interval.

Shared definitions are saved in `plugins/Blockfolk/abilities.yml`. Existing `fighters.yml` data is loaded and copied into the new file automatically; the old file is preserved. Once `abilities.yml` exists, it is used in preference to the old file. Abilities saved by earlier versions load unchanged, with a single pulse, no minimum range, and the Always trigger. Assignments are saved with the NPC combat profile or behaviour action. Existing assignments to the original eight special attacks remain valid. The library is seeded on first use; deleted templates are not recreated on restart.
