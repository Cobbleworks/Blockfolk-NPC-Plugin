package dev.blockfolk.model;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record SpecialAttackOptions(Set<SpecialAttack> enabled, int intervalSeconds) {
    public static final int DEFAULT_INTERVAL_SECONDS = 8;
    public static final int MIN_INTERVAL_SECONDS = 3;
    public static final int MAX_INTERVAL_SECONDS = 60;

    public SpecialAttackOptions {
        enabled = enabled == null ? Set.of() : Set.copyOf(enabled);
        intervalSeconds = Math.clamp(intervalSeconds, MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS);
    }

    public static SpecialAttackOptions disabled() {
        return new SpecialAttackOptions(Set.of(), DEFAULT_INTERVAL_SECONDS);
    }

    public static SpecialAttackOptions fromStored(Collection<String> attacks, int intervalSeconds) {
        Set<SpecialAttack> enabled = EnumSet.noneOf(SpecialAttack.class);
        if (attacks != null) {
            for (String attack : attacks)
                SpecialAttack.fromStored(attack).ifPresent(enabled::add);
        }
        return new SpecialAttackOptions(enabled, intervalSeconds);
    }

    public List<String> storedAttacks() {
        return enabled.stream().sorted().map(SpecialAttack::storedValue).toList();
    }

    public SpecialAttackOptions toggle(SpecialAttack attack) {
        Set<SpecialAttack> updated = EnumSet.noneOf(SpecialAttack.class);
        updated.addAll(enabled);
        if (!updated.remove(attack))
            updated.add(attack);
        return new SpecialAttackOptions(updated, intervalSeconds);
    }

    public SpecialAttackOptions withIntervalSeconds(int seconds) {
        return new SpecialAttackOptions(enabled, seconds);
    }
}
