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
                        Set.of(), 3, 1, 0, Visual.ENDER));
    }
    private static FighterAttack spell(String key, String name, double range, int cooldown, double damage,
            Set<Effect> effects, int duration, int level, Visual visual) {
        return new FighterAttack(key, name, Origin.TARGET, Shape.SPHERE, range, 2, 60, 20, cooldown * 20, damage,
                effects, duration, level, 0.8, visual);
    }
}
