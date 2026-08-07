package dev.blockfolk.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggression and target categories temporarily applied by a behaviour action.
 */
public record FightOptions(AttackReaction attackReaction, boolean mobs, boolean animals, boolean players,
        boolean npcs) {

    public FightOptions {
        attackReaction = Objects.requireNonNullElse(attackReaction, AttackReaction.IGNORE);
    }

    public static FightOptions from(CombatProfile profile) {
        return new FightOptions(profile.attackReaction(), profile.targetMobs(), profile.targetAnimals(),
                profile.targetPlayers(), profile.targetNpcs());
    }

    public static FightOptions fromStored(String value) {
        String stored = value == null ? "" : value.trim();
        String[] sections = stored.split(";", 2);
        AttackReaction reaction = sections.length > 0 && sections[0].startsWith("aggression=")
                ? AttackReaction.fromStored(sections[0].substring("aggression=".length()))
                : AttackReaction.IGNORE;
        String targetValue = sections.length == 2 && sections[1].startsWith("targets=")
                ? sections[1].substring("targets=".length())
                : "";
        Set<String> targets = Arrays.stream(targetValue.split(","))
                .map(target -> target.trim().toLowerCase(Locale.ROOT)).filter(target -> !target.isEmpty())
                .collect(Collectors.toSet());
        return new FightOptions(reaction, targets.contains("mobs"), targets.contains("animals"),
                targets.contains("players"), targets.contains("npcs"));
    }

    public String storedValue() {
        String targets = targetValue();
        return "aggression=" + attackReaction.name().toLowerCase(Locale.ROOT) + ";targets=" + targets;
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
        return attackReaction.displayName() + "; " + targets;
    }

    public FightOptions withAttackReaction(AttackReaction reaction) {
        return new FightOptions(reaction, mobs, animals, players, npcs);
    }

    public FightOptions withMobs(boolean enabled) {
        return new FightOptions(attackReaction, enabled, animals, players, npcs);
    }

    public FightOptions withAnimals(boolean enabled) {
        return new FightOptions(attackReaction, mobs, enabled, players, npcs);
    }

    public FightOptions withPlayers(boolean enabled) {
        return new FightOptions(attackReaction, mobs, animals, enabled, npcs);
    }

    public FightOptions withNpcs(boolean enabled) {
        return new FightOptions(attackReaction, mobs, animals, players, enabled);
    }

    private static void append(StringBuilder value, boolean enabled, String target) {
        if (!enabled)
            return;
        if (!value.isEmpty())
            value.append(',');
        value.append(target);
    }
}
