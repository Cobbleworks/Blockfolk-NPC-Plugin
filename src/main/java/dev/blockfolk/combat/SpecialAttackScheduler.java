package dev.blockfolk.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import dev.blockfolk.model.SpecialAttack;
import dev.blockfolk.model.SpecialAttackOptions;
import dev.blockfolk.fighters.FighterAttack;
import dev.blockfolk.fighters.FighterTemplates;

/** Per-instance cadence and cooldowns independent of combat targets. */
public final class SpecialAttackScheduler {
    private final Map<String, Long> readyAt = new HashMap<>();
    private long nextAttemptAt;
    private int intervalSeconds;

    public SpecialAttackScheduler(long tick, SpecialAttackOptions options) {
        intervalSeconds = options.intervalSeconds();
        nextAttemptAt = tick + intervalSeconds * 20L;
    }
    public SpecialAttack select(long tick, SpecialAttackOptions options, double distanceSquared,
            RandomGenerator random) {
        FighterAttack selected = select(tick, options, FighterTemplates.defaults(), distanceSquared, random);
        return selected == null ? null : SpecialAttack.fromStored(selected.key()).orElse(null);
    }
    public FighterAttack select(long tick, SpecialAttackOptions options, List<FighterAttack> definitions,
            double distanceSquared, RandomGenerator random) {
        if (intervalSeconds != options.intervalSeconds()) {
            intervalSeconds = options.intervalSeconds();
            nextAttemptAt = tick + intervalSeconds * 20L;
        }
        if (options.assignedAttackKeys().isEmpty() || tick < nextAttemptAt)
            return null;
        readyAt.keySet()
                .retainAll(definitions.stream().map(FighterAttack::key).collect(java.util.stream.Collectors.toSet()));
        var keys = options.assignedAttackKeys();
        List<FighterAttack> available = definitions.stream().filter(attack -> keys.contains(attack.key()))
                .filter(attack -> tick >= readyAt.getOrDefault(attack.key(), 0L))
                .filter(attack -> distanceSquared <= attack.range() * attack.range()).toList();
        if (available.isEmpty()) {
            nextAttemptAt = tick + 20;
            return null;
        }
        FighterAttack selected = available.get(random.nextInt(available.size()));
        readyAt.put(selected.key(), tick + selected.delayTicks() + selected.cooldownTicks());
        int intervalTicks = intervalSeconds * 20;
        nextAttemptAt = tick + selected.delayTicks() + Math.max(SpecialAttackOptions.MIN_INTERVAL_SECONDS * 20,
                intervalTicks + random.nextInt(intervalTicks / 2 + 1) - intervalTicks / 4);
        return selected;
    }
}
