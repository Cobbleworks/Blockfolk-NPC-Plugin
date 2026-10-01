package dev.blockfolk.model;

import java.util.Locale;
import java.util.Optional;

/** Native attacks inspired by BloodMoon's encounter abilities. */
public enum SpecialAttack {
    LIFE_DRAIN("Life Drain", "Deals 4 damage and heals the NPC by damage dealt", 10, 16), FREEZING_SPELL(
            "Freezing Spell", "Deals 2 damage and heavily slows the target for 3 seconds", 12,
            14), POISON_SPIT("Poison Spit", "Deals 2 damage and poisons the target for 4 seconds", 12,
                    12), WITHER_CURSE("Wither Curse", "Deals 2 damage and withers the target for 3 seconds", 12,
                            16), FLAME_BURST("Flame Burst", "Deals 4 damage and ignites the target for 3 seconds", 8,
                                    12), LIGHTNING_MARK("Lightning Mark", "Marks a spot, then strikes for 6 damage", 12,
                                            20), SHOCKWAVE("Shockwave",
                                                    "Deals 4 damage and knocks back nearby combat targets", 4,
                                                    10), FEAR("Fear",
                                                            "Deals 1 damage, blinds and weakens the target for 3 seconds",
                                                            8, 14);

    private final String displayName;
    private final String description;
    private final double range;
    private final int cooldownSeconds;

    SpecialAttack(String displayName, String description, double range, int cooldownSeconds) {
        this.displayName = displayName;
        this.description = description;
        this.range = range;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String displayName() {
        return displayName;
    }
    public String description() {
        return description;
    }
    public double rangeSquared() {
        return range * range;
    }
    public int cooldownTicks() {
        return cooldownSeconds * 20;
    }
    public String storedValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SpecialAttack> fromStored(String value) {
        if (value == null)
            return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
