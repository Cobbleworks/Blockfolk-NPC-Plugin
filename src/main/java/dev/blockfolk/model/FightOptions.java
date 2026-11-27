package dev.blockfolk.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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
        Set<TargetCategory> targets = TargetCategory
                .fromStored(Arrays.asList(sections.getOrDefault("targets", "").split(",")));
        Set<TargetCategory> abilityTargets = sections.containsKey("ability-targets")
                ? TargetCategory.fromStored(Arrays.asList(sections.get("ability-targets").split(",")))
                : targets;
        int interval = SpecialAttackOptions.DEFAULT_INTERVAL_SECONDS;
        try {
            interval = Integer.parseInt(sections.getOrDefault("special-interval", String.valueOf(interval)));
        } catch (NumberFormatException ignored) {
            // Invalid values in manually edited action data use the default interval.
        }
        SpecialAttackOptions special = SpecialAttackOptions
                .fromStored(Arrays.asList(sections.getOrDefault("special-attacks", "").split(",")), interval)
                .withFighterAttacks(Arrays.asList(sections.getOrDefault("fighter-attacks", "").split(",")))
                .withTargets(abilityTargets);
        return new FightOptions(reaction, targets.contains(TargetCategory.MOBS),
                targets.contains(TargetCategory.ANIMALS), targets.contains(TargetCategory.PLAYERS),
                targets.contains(TargetCategory.NPCS), special);
    }

    public String storedValue() {
        String stored = "aggression=" + attackReaction.name().toLowerCase(Locale.ROOT) + ";targets=" + targetValue()
                + ";ability-targets=" + String.join(",", TargetCategory.storedValues(specialAttacks.targets()));
        if (!specialAttacks.enabled().isEmpty()
                || specialAttacks.intervalSeconds() != SpecialAttackOptions.DEFAULT_INTERVAL_SECONDS) {
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
        return String.join(",", TargetCategory.storedValues(huntTargets()));
    }

    public Set<TargetCategory> huntTargets() {
        return TargetCategory.fromFlags(mobs, animals, players, npcs);
    }

    public String displayName() {
        String targets = targetValue();
        return attackReaction.displayName()
                + (attackReaction == AttackReaction.HUNTING
                        ? "; hunts " + (targets.isEmpty() ? "nothing" : targets)
                        : "")
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

}
