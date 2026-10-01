package dev.blockfolk.fighters;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable shared attack definition; all runtime state belongs to an NPC
 * instance.
 */
public record FighterAttack(String key, String name, Origin origin, Shape shape, double range, double size,
        double angle, int delayTicks, int cooldownTicks, double damage, Set<Effect> effects, int effectSeconds,
        int effectLevel, double knockback, Visual visual) {
    public enum Origin {
        NPC, TARGET;
        public Origin next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
    public enum Shape {
        SPHERE, CONE, BEAM, TELEPORT;
        public Shape next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
    public enum Effect {
        FIRE, POISON, SLOWNESS, WITHER, BLINDNESS, WEAKNESS, LIFE_DRAIN, KNOCKBACK
    }
    public enum Visual {
        FLAME, SONIC, SOUL, ICE, POISON, CLOUD, BLOOD, LIGHTNING, ENDER;
        public Visual next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public FighterAttack {
        key = normalizeKey(key);
        if (key.isEmpty())
            throw new IllegalArgumentException("An attack needs a valid key.");
        name = name == null || name.isBlank() ? key : name.trim().substring(0, Math.min(64, name.trim().length()));
        origin = Objects.requireNonNullElse(origin, Origin.NPC);
        shape = Objects.requireNonNullElse(shape, Shape.SPHERE);
        if (shape != Shape.SPHERE)
            origin = Origin.NPC;
        range = bounded(range, 1, 24, 8);
        size = bounded(size, shape == Shape.TELEPORT ? 1 : 0.25, 8, 2);
        angle = bounded(angle, 10, 160, 60);
        delayTicks = Math.clamp(delayTicks, 0, 200);
        cooldownTicks = Math.clamp(cooldownTicks, 20, 2400);
        damage = bounded(damage, 0, 100, 4);
        effects = effects == null ? Set.of() : Set.copyOf(effects);
        effectSeconds = Math.clamp(effectSeconds, 1, 30);
        effectLevel = Math.clamp(effectLevel, 1, 5);
        knockback = bounded(knockback, 0, 2.5, 0.8);
        visual = Objects.requireNonNullElse(visual, Visual.SOUL);
    }

    public static String normalizeKey(String value) {
        String key = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        return key.substring(0, Math.min(64, key.length()));
    }
    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
    public FighterAttack withName(String value) {
        return new FighterAttack(key, value, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage,
                effects, effectSeconds, effectLevel, knockback, visual);
    }
    public FighterAttack copy(String newKey, String newName) {
        return new FighterAttack(newKey, newName, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage,
                effects, effectSeconds, effectLevel, knockback, visual);
    }
    public FighterAttack withGeometry(Origin origin, Shape shape, double range, double size, double angle) {
        return new FighterAttack(key, name, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage,
                effects, effectSeconds, effectLevel, knockback, visual);
    }
    public FighterAttack withTiming(int delay, int cooldown) {
        return new FighterAttack(key, name, origin, shape, range, size, angle, delay, cooldown, damage, effects,
                effectSeconds, effectLevel, knockback, visual);
    }
    public FighterAttack withEffects(double damage, Set<Effect> effects, int duration, int level, double knockback) {
        return new FighterAttack(key, name, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage,
                effects, duration, level, knockback, visual);
    }
    public FighterAttack withVisual(Visual visual) {
        return new FighterAttack(key, name, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage,
                effects, effectSeconds, effectLevel, knockback, visual);
    }
}
