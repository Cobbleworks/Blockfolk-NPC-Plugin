package dev.blockfolk.fighters;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;
import org.bukkit.inventory.ItemStack;

/**
 * Immutable shared attack definition; all runtime state belongs to an NPC
 * instance. Use {@link #builder} or {@link #toBuilder} to create variants.
 */
public record FighterAttack(String key, String name, Origin origin, Shape shape, double range, double size,
        double angle, int delayTicks, int cooldownTicks, double damage, Set<Effect> effects, int effectSeconds,
        int effectLevel, double knockback, Visual visual, double coneLength, ItemStack icon, CastMode castMode,
        double minRange, double innerRadius, int chainTargets, int pulses, int pulseIntervalTicks,
        Condition condition) {
    public static final double MAX_RANGE = 24;
    public static final double MAX_SIZE = 8;
    public static final double MAX_DAMAGE = 100;
    public static final double MAX_FORCE = 2.5;
    public static final int MAX_DELAY_TICKS = 200;
    public static final int MIN_COOLDOWN_TICKS = 20;
    public static final int MAX_COOLDOWN_TICKS = 2400;
    public static final int MAX_EFFECT_SECONDS = 30;
    public static final int MAX_EFFECT_LEVEL = 5;
    public static final int MIN_CHAIN_TARGETS = 2;
    public static final int MAX_CHAIN_TARGETS = 8;
    public static final int MAX_PULSES = 10;
    /** Matches vanilla hurt immunity so every pulse can deal its damage. */
    public static final int MIN_PULSE_INTERVAL_TICKS = 10;
    public static final int MAX_PULSE_INTERVAL_TICKS = 60;

    public enum Origin {
        NPC, TARGET;
        public Origin next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
    public enum Shape {
        SPHERE, RING, CONE, BEAM, CHAIN, DASH, TELEPORT, SELF;
        public Shape next() {
            return values()[(ordinal() + 1) % values().length];
        }
        /** Shapes that can be centred on the opponent instead of the caster. */
        public boolean supportsOrigin() {
            return this == SPHERE || this == RING;
        }
        /** Shapes that are aimed at, or travel to, the opponent. */
        public boolean aimed() {
            return this == CONE || this == BEAM || this == CHAIN || this == DASH;
        }
        /** Shapes that can linger and strike repeatedly. */
        public boolean supportsPulses() {
            return this == SPHERE || this == RING || this == CONE || this == BEAM;
        }
        /** Shapes that strike other entities; the rest only affect the caster. */
        public boolean hitsVictims() {
            return this != TELEPORT && this != SELF;
        }
    }
    public enum CastMode {
        INSTANT, DELAYED, NEXT_ATTACK;
        public CastMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
    /** Who an effect is applied to, and how it is grouped in the editor. */
    public enum EffectCategory {
        AFFLICTION, CONTROL, CASTER
    }
    public enum Effect {
        FIRE(EffectCategory.AFFLICTION), POISON(EffectCategory.AFFLICTION), WITHER(EffectCategory.AFFLICTION), WEAKNESS(
                EffectCategory.AFFLICTION), BLINDNESS(EffectCategory.AFFLICTION), DARKNESS(
                        EffectCategory.AFFLICTION), NAUSEA(EffectCategory.AFFLICTION), HUNGER(
                                EffectCategory.AFFLICTION), SLOWNESS(EffectCategory.CONTROL), MINING_FATIGUE(
                                        EffectCategory.CONTROL), LEVITATION(EffectCategory.CONTROL), GLOWING(
                                                EffectCategory.CONTROL), KNOCKBACK(
                                                        EffectCategory.CONTROL), PULL(EffectCategory.CONTROL), LAUNCH(
                                                                EffectCategory.CONTROL), LIFE_DRAIN(
                                                                        EffectCategory.CASTER), REGENERATION(
                                                                                EffectCategory.CASTER), SPEED(
                                                                                        EffectCategory.CASTER), STRENGTH(
                                                                                                EffectCategory.CASTER), RESISTANCE(
                                                                                                        EffectCategory.CASTER), ABSORPTION(
                                                                                                                EffectCategory.CASTER);

        private final EffectCategory category;

        Effect(EffectCategory category) {
            this.category = category;
        }
        public EffectCategory category() {
            return category;
        }
        /** Effects that move victims and therefore use the force setting. */
        public boolean movement() {
            return this == KNOCKBACK || this == PULL || this == LAUNCH;
        }
        /** Potion buffs applied to the caster when the ability is released. */
        public boolean casterBuff() {
            return category == EffectCategory.CASTER && this != LIFE_DRAIN;
        }
    }
    /** When automatic combat may choose an ability. Scripted casts ignore it. */
    public enum Condition {
        ALWAYS, CASTER_HURT, CASTER_CRITICAL, TARGET_WOUNDED;
        public boolean test(DoubleSupplier casterHealth, DoubleSupplier targetHealth) {
            return switch (this) {
                case ALWAYS -> true;
                case CASTER_HURT -> casterHealth.getAsDouble() <= 0.5;
                case CASTER_CRITICAL -> casterHealth.getAsDouble() <= 0.25;
                case TARGET_WOUNDED -> targetHealth.getAsDouble() <= 0.5;
            };
        }
    }
    public enum Visual {
        FLAME, SONIC, SOUL, ICE, POISON, CLOUD, BLOOD, LIGHTNING, ENDER, ENCHANT, HEARTS, SMOKE, SOUL_FLAME, BUBBLES, SPORES, TOTEM, HOLY, SCULK, CHERRY, WIND, GLOW;
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
        if (!shape.supportsOrigin())
            origin = Origin.NPC;
        range = bounded(range, 1, MAX_RANGE, 8);
        minRange = bounded(minRange, 0, range - 1, 0);
        coneLength = bounded(coneLength, 1, MAX_RANGE, range);
        icon = icon == null || icon.isEmpty() ? null : icon.clone();
        if (icon != null)
            icon.setAmount(1);
        boolean wholeBlocks = shape == Shape.TELEPORT || shape == Shape.RING || shape == Shape.CHAIN;
        size = bounded(size, wholeBlocks ? 1 : 0.25, MAX_SIZE, 2);
        innerRadius = bounded(innerRadius, 0, MAX_SIZE, 0);
        if (shape == Shape.RING)
            innerRadius = innerRadius <= 0 ? size / 2 : Math.clamp(innerRadius, 0.5, size - 0.5);
        angle = bounded(angle, 10, 160, 60);
        delayTicks = Math.clamp(delayTicks, 0, MAX_DELAY_TICKS);
        castMode = castMode == null ? (delayTicks == 0 ? CastMode.INSTANT : CastMode.DELAYED) : castMode;
        delayTicks = castMode == CastMode.DELAYED ? Math.max(1, delayTicks) : 0;
        cooldownTicks = Math.clamp(cooldownTicks, MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS);
        damage = bounded(damage, 0, MAX_DAMAGE, 4);
        Set<Effect> normalized = EnumSet.noneOf(Effect.class);
        if (effects != null)
            effects.stream().filter(Objects::nonNull).forEach(normalized::add);
        if (normalized.contains(Effect.KNOCKBACK))
            normalized.remove(Effect.PULL); // Opposite directions; keep the established behaviour.
        effects = Set.copyOf(normalized);
        effectSeconds = Math.clamp(effectSeconds, 1, MAX_EFFECT_SECONDS);
        effectLevel = Math.clamp(effectLevel, 1, MAX_EFFECT_LEVEL);
        knockback = bounded(knockback, 0, MAX_FORCE, 0.8);
        visual = Objects.requireNonNullElse(visual, Visual.SOUL);
        chainTargets = Math.clamp(chainTargets, MIN_CHAIN_TARGETS, MAX_CHAIN_TARGETS);
        pulses = shape.supportsPulses() ? Math.clamp(pulses, 1, MAX_PULSES) : 1;
        pulseIntervalTicks = Math.clamp(pulseIntervalTicks, MIN_PULSE_INTERVAL_TICKS, MAX_PULSE_INTERVAL_TICKS);
        condition = Objects.requireNonNullElse(condition, Condition.ALWAYS);
    }

    public FighterAttack(String key, String name, Origin origin, Shape shape, double range, double size, double angle,
            int delayTicks, int cooldownTicks, double damage, Set<Effect> effects, int effectSeconds, int effectLevel,
            double knockback, Visual visual) {
        this(key, name, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage, effects, effectSeconds,
                effectLevel, knockback, visual, range, null, null);
    }

    public FighterAttack(String key, String name, Origin origin, Shape shape, double range, double size, double angle,
            int delayTicks, int cooldownTicks, double damage, Set<Effect> effects, int effectSeconds, int effectLevel,
            double knockback, Visual visual, double coneLength, ItemStack icon) {
        this(key, name, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage, effects, effectSeconds,
                effectLevel, knockback, visual, coneLength, icon, null);
    }

    public FighterAttack(String key, String name, Origin origin, Shape shape, double range, double size, double angle,
            int delayTicks, int cooldownTicks, double damage, Set<Effect> effects, int effectSeconds, int effectLevel,
            double knockback, Visual visual, double coneLength, ItemStack icon, CastMode castMode) {
        this(key, name, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage, effects, effectSeconds,
                effectLevel, knockback, visual, coneLength, icon, castMode, 0, 0, 3, 1, 20, Condition.ALWAYS);
    }

    public static Builder builder(String key, String name) {
        return new Builder(key, name);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public ItemStack icon() {
        return icon == null ? null : icon.clone();
    }

    public double reach() {
        return shape == Shape.CONE ? coneLength : range;
    }

    /** Victims are only affected when there is damage or a victim effect. */
    public boolean affectsVictims() {
        return shape.hitsVictims()
                && (damage > 0 || effects.stream().anyMatch(effect -> effect.category() != EffectCategory.CASTER));
    }

    public FighterAttack withCastMode(CastMode mode) {
        int delay = mode == CastMode.DELAYED ? (delayTicks > 0 ? delayTicks : 20) : 0;
        return toBuilder().delayTicks(delay).castMode(mode).build();
    }
    public FighterAttack withConeLength(double length) {
        return toBuilder().coneLength(length).build();
    }
    public FighterAttack withIcon(ItemStack item) {
        return toBuilder().icon(item).build();
    }
    public FighterAttack withName(String value) {
        return toBuilder().name(value).build();
    }
    public FighterAttack copy(String newKey, String newName) {
        return toBuilder().key(newKey).name(newName).build();
    }
    public FighterAttack withGeometry(Origin origin, Shape shape, double range, double size, double angle) {
        return toBuilder().origin(origin).shape(shape).range(range).size(size).angle(angle).build();
    }
    /**
     * Changing the delay re-derives instant or delayed casting; charges stay
     * charges.
     */
    public FighterAttack withTiming(int delay, int cooldown) {
        return toBuilder().delayTicks(delay).cooldownTicks(cooldown)
                .castMode(castMode == CastMode.NEXT_ATTACK ? castMode : null).build();
    }
    public FighterAttack withEffects(double damage, Set<Effect> effects, int duration, int level, double knockback) {
        return toBuilder().damage(damage).effects(effects).effectSeconds(duration).effectLevel(level)
                .knockback(knockback).build();
    }
    public FighterAttack withVisual(Visual visual) {
        return toBuilder().visual(visual).build();
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

    /** Mutable staging area; {@link #build} applies the record's validation. */
    public static final class Builder {
        private String key;
        private String name;
        private Origin origin = Origin.NPC;
        private Shape shape = Shape.SPHERE;
        private double range = 8;
        private double size = 2;
        private double angle = 60;
        private int delayTicks = 20;
        private int cooldownTicks = 200;
        private double damage = 4;
        private Set<Effect> effects = Set.of();
        private int effectSeconds = 3;
        private int effectLevel = 1;
        private double knockback = 0.8;
        private Visual visual = Visual.SOUL;
        private double coneLength = Double.NaN;
        private ItemStack icon;
        private CastMode castMode;
        private double minRange;
        private double innerRadius;
        private int chainTargets = 3;
        private int pulses = 1;
        private int pulseIntervalTicks = 20;
        private Condition condition = Condition.ALWAYS;

        private Builder(String key, String name) {
            this.key = key;
            this.name = name;
        }
        private Builder(FighterAttack attack) {
            key = attack.key;
            name = attack.name;
            origin = attack.origin;
            shape = attack.shape;
            range = attack.range;
            size = attack.size;
            angle = attack.angle;
            delayTicks = attack.delayTicks;
            cooldownTicks = attack.cooldownTicks;
            damage = attack.damage;
            effects = attack.effects;
            effectSeconds = attack.effectSeconds;
            effectLevel = attack.effectLevel;
            knockback = attack.knockback;
            visual = attack.visual;
            coneLength = attack.coneLength;
            icon = attack.icon;
            castMode = attack.castMode;
            minRange = attack.minRange;
            innerRadius = attack.innerRadius;
            chainTargets = attack.chainTargets;
            pulses = attack.pulses;
            pulseIntervalTicks = attack.pulseIntervalTicks;
            condition = attack.condition;
        }

        public Builder key(String value) {
            key = value;
            return this;
        }
        public Builder name(String value) {
            name = value;
            return this;
        }
        public Builder origin(Origin value) {
            origin = value;
            return this;
        }
        public Builder shape(Shape value) {
            shape = value;
            return this;
        }
        public Builder range(double value) {
            range = value;
            return this;
        }
        public Builder size(double value) {
            size = value;
            return this;
        }
        public Builder angle(double value) {
            angle = value;
            return this;
        }
        public Builder delayTicks(int value) {
            delayTicks = value;
            return this;
        }
        public Builder cooldownTicks(int value) {
            cooldownTicks = value;
            return this;
        }
        public Builder damage(double value) {
            damage = value;
            return this;
        }
        public Builder effects(Set<Effect> value) {
            effects = value;
            return this;
        }
        public Builder effects(Effect... value) {
            effects = Set.of(value);
            return this;
        }
        public Builder effectSeconds(int value) {
            effectSeconds = value;
            return this;
        }
        public Builder effectLevel(int value) {
            effectLevel = value;
            return this;
        }
        public Builder knockback(double value) {
            knockback = value;
            return this;
        }
        public Builder visual(Visual value) {
            visual = value;
            return this;
        }
        public Builder coneLength(double value) {
            coneLength = value;
            return this;
        }
        public Builder icon(ItemStack value) {
            icon = value;
            return this;
        }
        public Builder castMode(CastMode value) {
            castMode = value;
            return this;
        }
        public Builder minRange(double value) {
            minRange = value;
            return this;
        }
        public Builder innerRadius(double value) {
            innerRadius = value;
            return this;
        }
        public Builder chainTargets(int value) {
            chainTargets = value;
            return this;
        }
        public Builder pulses(int value) {
            pulses = value;
            return this;
        }
        public Builder pulseIntervalTicks(int value) {
            pulseIntervalTicks = value;
            return this;
        }
        public Builder condition(Condition value) {
            condition = value;
            return this;
        }
        public FighterAttack build() {
            return new FighterAttack(key, name, origin, shape, range, size, angle, delayTicks, cooldownTicks, damage,
                    effects, effectSeconds, effectLevel, knockback, visual, coneLength, icon, castMode, minRange,
                    innerRadius, chainTargets, pulses, pulseIntervalTicks, condition);
        }
    }
}
