package dev.blockfolk.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggression, targets, and special attacks temporarily applied by a behaviour
 * action.
 */
public record FightOptions(AttackReaction attackReaction, boolean mobs, boolean animals, boolean players, boolean npcs,
        SpecialAttackOptions specialAttacks) {

    public FightOptions {
        attackReaction = Objects.requireNonNullElse(attackReaction, AttackReaction.IGNORE);
        specialAttacks = Objects.requireNonNullElse(specialAttacks, SpecialAttackOptions.disabled());
    }

    public FightOptions(AttackReaction attackReaction, boolean mobs, boolean animals, boolean players, boolean npcs) {
        this(attackReaction, mobs, animals, players, npcs, SpecialAttackOptions.disabled());
    }

    public static FightOptions from(CombatProfile profile) {
        return new FightOptions(profile.attackReaction(), profile.targetMobs(), profile.targetAnimals(),
                profile.targetPlayers(), profile.targetNpcs(), profile.specialAttacks());
    }

    public static FightOptions fromStored(String value) {
        String stored = value == null ? "" : value.trim();
        java.util.Map<String, String> sections = new java.util.HashMap<>();
        for (String section : stored.split(";")) {
            String[] pair = section.split("=", 2);
            if (pair.length == 2)
                sections.put(pair[0].trim(), pair[1].trim());
        }
        AttackReaction reaction = AttackReaction.fromStored(sections.get("aggression"));
        Set<String> targets = Arrays.stream(sections.getOrDefault("targets", "").split(","))
                .map(target -> target.trim().toLowerCase(Locale.ROOT)).filter(target -> !target.isEmpty())
                .collect(Collectors.toSet());
        int interval = SpecialAttackOptions.DEFAULT_INTERVAL_SECONDS;
        try {
            interval = Integer.parseInt(sections.getOrDefault("special-interval", String.valueOf(interval)));
        } catch (NumberFormatException ignored) {
            // Invalid values in manually edited action data use the default interval.
        }
        SpecialAttackOptions special = SpecialAttackOptions
                .fromStored(Arrays.asList(sections.getOrDefault("special-attacks", "").split(",")), interval)
                .withFighterAttacks(Arrays.asList(sections.getOrDefault("fighter-attacks", "").split(",")));
        return new FightOptions(reaction, targets.contains("mobs"), targets.contains("animals"),
                targets.contains("players"), targets.contains("npcs"), special);
    }

    public String storedValue() {
        String stored = "aggression=" + attackReaction.name().toLowerCase(Locale.ROOT) + ";targets=" + targetValue();
        if (!specialAttacks.equals(SpecialAttackOptions.disabled())) {
            stored += ";special-attacks=" + String.join(",", specialAttacks.storedAttacks()) + ";special-interval="
                    + specialAttacks.intervalSeconds();
        }
        if (!specialAttacks.fighterAttacks().isEmpty()) {
            stored += ";fighter-attacks="
                    + String.join(",", specialAttacks.fighterAttacks().stream().sorted().toList());
        }
        return stored;
    }

    public String targetValue() {
        StringBuilder value = new StringBuilder();
        append(value, mobs, "mobs");
        append(value, animals, "animals");
        append(value, players, "players");
        append(value, npcs, "npcs");
        return value.toString();
    }

    public String displayName() {
        String targets = targetValue().isEmpty() ? "No targets" : targetValue();
        return attackReaction.displayName() + "; " + targets
                + (specialAttacks.assignedAttackKeys().isEmpty()
                        ? ""
                        : "; " + specialAttacks.assignedAttackKeys().size() + " special attacks");
    }

    public FightOptions withAttackReaction(AttackReaction reaction) {
        return new FightOptions(reaction, mobs, animals, players, npcs, specialAttacks);
    }

    public FightOptions withMobs(boolean enabled) {
        return new FightOptions(attackReaction, enabled, animals, players, npcs, specialAttacks);
    }

    public FightOptions withAnimals(boolean enabled) {
        return new FightOptions(attackReaction, mobs, enabled, players, npcs, specialAttacks);
    }

    public FightOptions withPlayers(boolean enabled) {
        return new FightOptions(attackReaction, mobs, animals, enabled, npcs, specialAttacks);
    }

    public FightOptions withNpcs(boolean enabled) {
        return new FightOptions(attackReaction, mobs, animals, players, enabled, specialAttacks);
    }

    public FightOptions withSpecialAttacks(SpecialAttackOptions options) {
        return new FightOptions(attackReaction, mobs, animals, players, npcs, options);
    }

    private static void append(StringBuilder value, boolean enabled, String target) {
        if (!enabled)
            return;
        if (!value.isEmpty())
            value.append(',');
        value.append(target);
    }
}
