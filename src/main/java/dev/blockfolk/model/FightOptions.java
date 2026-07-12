package dev.blockfolk.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Target categories that an NPC is allowed to attack. */
public record FightOptions(boolean mobs, boolean animals, boolean players, boolean npcs) {

    public static FightOptions from(CombatProfile profile) {
        return new FightOptions(profile.targetMobs(), profile.targetAnimals(),
                profile.targetPlayers(), profile.targetNpcs());
    }

    public static FightOptions fromStored(String value) {
        Set<String> targets = value == null ? Set.of() : Arrays.stream(value.split(","))
                .map(target -> target.trim().toLowerCase(Locale.ROOT))
                .filter(target -> !target.isEmpty())
                .collect(Collectors.toSet());
        return new FightOptions(targets.contains("mobs") || targets.contains("monsters"),
                targets.contains("animals"), targets.contains("players"),
                targets.contains("npcs") || targets.contains("npc"));
    }

    public String storedValue() {
        StringBuilder value = new StringBuilder();
        append(value, mobs, "mobs");
        append(value, animals, "animals");
        append(value, players, "players");
        append(value, npcs, "npcs");
        return value.toString();
    }

    public String displayName() {
        return storedValue().isEmpty() ? "No targets" : storedValue();
    }

    private static void append(StringBuilder value, boolean enabled, String target) {
        if (!enabled) return;
        if (!value.isEmpty()) value.append(',');
        value.append(target);
    }
}
