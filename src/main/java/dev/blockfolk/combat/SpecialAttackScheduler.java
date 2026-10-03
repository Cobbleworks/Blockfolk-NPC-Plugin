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
    public boolean isReady(String key, long tick) {
        return tick >= readyAt.getOrDefault(key, 0L);
    }

    public void markUsed(FighterAttack attack, long tick) {
        readyAt.put(attack.key(), tick + attack.delayTicks() + attack.cooldownTicks());
    }

    public SpecialAttack select(long tick, SpecialAttackOptions options, double distanceSquared,
            RandomGenerator random) {
        FighterAttack selected = select(tick, options, FighterTemplates.defaults(), distanceSquared, random);
        return selected == null ? null : SpecialAttack.fromStored(selected.key()).orElse(null);
    }
    public FighterAttack select(long tick, SpecialAttackOptions options, List<FighterAttack> definitions,
            double distanceSquared, RandomGenerator random) {
        return select(tick, options, definitions, CastContext.atDistance(distanceSquared), random);
    }
    /**
     * Picks a ready ability. Abilities whose trigger condition is currently met
     * take precedence over unconditional ones.
     */
    public FighterAttack select(long tick, SpecialAttackOptions options, List<FighterAttack> definitions,
            CastContext context, RandomGenerator random) {
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
                .filter(attack -> tick >= readyAt.getOrDefault(attack.key(), 0L)).filter(context::allows).toList();
        if (available.isEmpty()) {
            nextAttemptAt = tick + 20;
            return null;
        }
        List<FighterAttack> reactive = available.stream()
                .filter(attack -> attack.condition() != FighterAttack.Condition.ALWAYS).toList();
        if (!reactive.isEmpty())
            available = reactive;
        FighterAttack selected = available.get(random.nextInt(available.size()));
        markUsed(selected, tick);
        int intervalTicks = intervalSeconds * 20;
        nextAttemptAt = tick + selected.delayTicks() + Math.max(SpecialAttackOptions.MIN_INTERVAL_SECONDS * 20,
                intervalTicks + random.nextInt(intervalTicks / 2 + 1) - intervalTicks / 4);
        return selected;
    }
}
