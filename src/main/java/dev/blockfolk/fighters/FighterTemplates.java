package dev.blockfolk.fighters;

import java.util.List;
import java.util.Set;
import dev.blockfolk.fighters.FighterAttack.*;

public final class FighterTemplates {
    private FighterTemplates() {
    }
    public static List<FighterAttack> defaults() {
        return List.of(spell("life_drain", "Life Drain", 10, 16, 4, Set.of(Effect.LIFE_DRAIN), 3, 1, Visual.BLOOD),
                spell("freezing_spell", "Freezing Spell", 12, 14, 2, Set.of(Effect.SLOWNESS), 3, 4, Visual.ICE),
                spell("poison_spit", "Poison Spit", 12, 12, 2, Set.of(Effect.POISON), 4, 1, Visual.POISON),
                spell("wither_curse", "Wither Curse", 12, 16, 2, Set.of(Effect.WITHER), 3, 1, Visual.SOUL),
                spell("flame_burst", "Flame Burst", 8, 12, 4, Set.of(Effect.FIRE), 3, 1, Visual.FLAME),
                spell("lightning_mark", "Lightning Mark", 12, 20, 6, Set.of(), 3, 1, Visual.LIGHTNING),
                new FighterAttack("shockwave", "Defensive Shockwave", Origin.NPC, Shape.SPHERE, 4, 4, 60, 10, 200, 4,
                        Set.of(Effect.KNOCKBACK), 3, 1, 0.8, Visual.CLOUD),
                spell("fear", "Fear", 8, 14, 1, Set.of(Effect.BLINDNESS, Effect.WEAKNESS), 3, 1, Visual.SOUL),
                new FighterAttack("fire_breath", "Fire Breath", Origin.NPC, Shape.CONE, 8, 2, 60, 15, 240, 4,
                        Set.of(Effect.FIRE), 3, 1, 0.8, Visual.FLAME),
                new FighterAttack("sonic_blast", "Sonic Blast", Origin.NPC, Shape.BEAM, 16, 0.75, 60, 25, 320, 6,
                        Set.of(Effect.KNOCKBACK), 3, 1, 0.6, Visual.SONIC),
                new FighterAttack("blink", "Reposition Blink", Origin.NPC, Shape.TELEPORT, 8, 5, 60, 0, 240, 0,
                        Set.of(), 3, 1, 0, Visual.ENDER),
                FighterAttack.builder("frost_nova", "Frost Nova").shape(Shape.RING).range(6).size(5).innerRadius(2)
                        .delayTicks(15).cooldownTicks(280).damage(3).effects(Effect.SLOWNESS).effectLevel(3)
                        .visual(Visual.ICE).build(),
                FighterAttack.builder("chain_lightning", "Chain Lightning").shape(Shape.CHAIN).range(12).size(5)
                        .chainTargets(4).delayTicks(15).cooldownTicks(320).damage(4).visual(Visual.LIGHTNING).build(),
                FighterAttack.builder("poison_cloud", "Poison Cloud").origin(Origin.TARGET).range(12).size(3)
                        .cooldownTicks(400).damage(1).effects(Effect.POISON).effectSeconds(2).pulses(5)
                        .visual(Visual.POISON).build(),
                FighterAttack.builder("gravity_well", "Gravity Well").origin(Origin.TARGET).range(12).size(4)
                        .cooldownTicks(440).damage(1).effects(Effect.PULL, Effect.SLOWNESS).knockback(0.5).pulses(4)
                        .pulseIntervalTicks(15).visual(Visual.ENDER).build(),
                FighterAttack.builder("searing_ray", "Searing Ray").shape(Shape.BEAM).range(14).size(0.6)
                        .cooldownTicks(400).damage(2).effects(Effect.FIRE).pulses(4).pulseIntervalTicks(10)
                        .visual(Visual.SOUL_FLAME).build(),
                FighterAttack.builder("shadow_dash", "Shadow Dash").shape(Shape.DASH).range(10).minRange(4).size(1.25)
                        .delayTicks(10).cooldownTicks(280).damage(4).effects(Effect.BLINDNESS).effectSeconds(2)
                        .visual(Visual.SMOKE).build(),
                FighterAttack.builder("updraft", "Updraft").range(4).size(3.5).delayTicks(10).cooldownTicks(320)
                        .damage(2).effects(Effect.LAUNCH).knockback(0.9).visual(Visual.WIND).build(),
                FighterAttack.builder("finishing_blow", "Finishing Blow").origin(Origin.TARGET).range(4).size(1.5)
                        .castMode(CastMode.NEXT_ATTACK).cooldownTicks(300).damage(6).effects(Effect.WITHER)
                        .condition(Condition.TARGET_WOUNDED).visual(Visual.BLOOD).build(),
                FighterAttack.builder("war_cry", "War Cry").shape(Shape.SELF).range(8).delayTicks(10).cooldownTicks(600)
                        .damage(0).effects(Effect.STRENGTH, Effect.RESISTANCE).effectSeconds(8)
                        .condition(Condition.CASTER_HURT).visual(Visual.TOTEM).build(),
                FighterAttack.builder("second_wind", "Second Wind").shape(Shape.SELF).range(16).cooldownTicks(900)
                        .damage(0).effects(Effect.REGENERATION, Effect.ABSORPTION).effectSeconds(6).effectLevel(2)
                        .condition(Condition.CASTER_CRITICAL).visual(Visual.HEARTS).build());
    }
    private static FighterAttack spell(String key, String name, double range, int cooldown, double damage,
            Set<Effect> effects, int duration, int level, Visual visual) {
        return new FighterAttack(key, name, Origin.TARGET, Shape.SPHERE, range, 2, 60, 20, cooldown * 20, damage,
                effects, duration, level, 0.8, visual);
    }
}
