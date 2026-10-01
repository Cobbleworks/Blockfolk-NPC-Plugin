package dev.blockfolk.combat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import dev.blockfolk.model.SpecialAttack;
import dev.blockfolk.model.SpecialAttackOptions;

/**
 * Per-instance cadence and cooldowns, independent of Bukkit and combat targets.
 */
public final class SpecialAttackScheduler {
    private final Map<SpecialAttack, Long> readyAt = new EnumMap<>(SpecialAttack.class);
    private long nextAttemptAt;
    private int intervalSeconds;

    public SpecialAttackScheduler(long tick, SpecialAttackOptions options) {
        intervalSeconds = options.intervalSeconds();
        nextAttemptAt = tick + intervalSeconds * 20L;
    }

    public SpecialAttack select(long tick, SpecialAttackOptions options, double distanceSquared,
            RandomGenerator random) {
        if (intervalSeconds != options.intervalSeconds()) {
            intervalSeconds = options.intervalSeconds();
            nextAttemptAt = tick + intervalSeconds * 20L;
        }
        if (options.enabled().isEmpty() || tick < nextAttemptAt)
            return null;
        List<SpecialAttack> available = options.enabled().stream().sorted()
                .filter(attack -> tick >= readyAt.getOrDefault(attack, 0L))
                .filter(attack -> distanceSquared <= attack.rangeSquared()).toList();
        if (available.isEmpty()) {
            nextAttemptAt = tick + 20;
            return null;
        }
        SpecialAttack selected = available.get(random.nextInt(available.size()));
        readyAt.put(selected, tick + selected.cooldownTicks());
        int intervalTicks = intervalSeconds * 20;
        // Vary the interval by up to 25%, while respecting the minimum cadence.
        nextAttemptAt = tick + Math.max(SpecialAttackOptions.MIN_INTERVAL_SECONDS * 20,
                intervalTicks + random.nextInt(intervalTicks / 2 + 1) - intervalTicks / 4);
        return selected;
    }
}
