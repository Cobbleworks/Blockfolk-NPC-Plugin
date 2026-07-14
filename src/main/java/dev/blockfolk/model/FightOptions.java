package dev.blockfolk.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Aggression and target categories temporarily applied by a behaviour action. */
public record FightOptions(AttackReaction attackReaction,
        boolean mobs, boolean animals, boolean players, boolean npcs) {

    public FightOptions(boolean mobs, boolean animals, boolean players, boolean npcs) {
        this(null, mobs, animals, players, npcs);
    }

    public static FightOptions from(CombatProfile profile) {
        return new FightOptions(profile.attackReaction(), profile.targetMobs(), profile.targetAnimals(),
                profile.targetPlayers(), profile.targetNpcs());
    }

    public static FightOptions fromStored(String value) {
        String stored = value == null ? "" : value.trim();
        AttackReaction reaction = null;
        String targetValue = stored;
        if (stored.startsWith("aggression=")) {
            String[] sections = stored.split(";", 2);
            reaction = AttackReaction.fromStored(sections[0].substring("aggression=".length()));
            targetValue = sections.length == 2 && sections[1].startsWith("targets=")
                    ? sections[1].substring("targets=".length()) : "";
        }
        Set<String> targets = Arrays.stream(targetValue.split(","))
                .map(target -> target.trim().toLowerCase(Locale.ROOT))
                .filter(target -> !target.isEmpty())
                .collect(Collectors.toSet());
        return new FightOptions(reaction, targets.contains("mobs") || targets.contains("monsters"),
                targets.contains("animals"), targets.contains("players"),
                targets.contains("npcs") || targets.contains("npc"));
    }

    public String storedValue() {
        String targets = targetValue();
        if (attackReaction == null) return targets;
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
        return attackReaction == null ? targets : attackReaction.displayName() + "; " + targets;
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
        if (!enabled) return;
        if (!value.isEmpty()) value.append(',');
        value.append(target);
    }
}
