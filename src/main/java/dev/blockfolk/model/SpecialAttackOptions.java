package dev.blockfolk.model;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import dev.blockfolk.fighters.FighterAttack;

public record SpecialAttackOptions(Set<SpecialAttack> enabled, int intervalSeconds, Set<String> fighterAttacks) {
    public static final int DEFAULT_INTERVAL_SECONDS = 8;
    public static final int MIN_INTERVAL_SECONDS = 3;
    public static final int MAX_INTERVAL_SECONDS = 60;

    public SpecialAttackOptions {
        Set<SpecialAttack> legacy = EnumSet.noneOf(SpecialAttack.class);
        if (enabled != null)
            legacy.addAll(enabled);
        Set<String> custom = new HashSet<>();
        if (fighterAttacks != null) {
            for (String value : fighterAttacks) {
                String key = FighterAttack.normalizeKey(value);
                var known = SpecialAttack.fromStored(key);
                if (known.isPresent())
                    legacy.add(known.get());
                else if (!key.isEmpty())
                    custom.add(key);
            }
        }
        enabled = Set.copyOf(legacy);
        fighterAttacks = Set.copyOf(custom);
        intervalSeconds = Math.clamp(intervalSeconds, MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS);
    }

    public SpecialAttackOptions(Set<SpecialAttack> enabled, int intervalSeconds) {
        this(enabled, intervalSeconds, Set.of());
    }

    public Set<String> assignedAttackKeys() {
        Set<String> keys = new HashSet<>(fighterAttacks);
        enabled.forEach(attack -> keys.add(attack.storedValue()));
        return Set.copyOf(keys);
    }

    public SpecialAttackOptions toggle(String key) {
        var legacy = SpecialAttack.fromStored(key);
        if (legacy.isPresent())
            return toggle(legacy.get());
        Set<String> keys = new HashSet<>(fighterAttacks);
        key = FighterAttack.normalizeKey(key);
        if (!key.isEmpty() && !keys.remove(key))
            keys.add(key);
        return new SpecialAttackOptions(enabled, intervalSeconds, keys);
    }

    public SpecialAttackOptions withFighterAttacks(Collection<String> keys) {
        return new SpecialAttackOptions(enabled, intervalSeconds, keys == null ? Set.of() : new HashSet<>(keys));
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
        return new SpecialAttackOptions(updated, intervalSeconds, fighterAttacks);
    }

    public SpecialAttackOptions withIntervalSeconds(int seconds) {
        return new SpecialAttackOptions(enabled, seconds, fighterAttacks);
    }
}
