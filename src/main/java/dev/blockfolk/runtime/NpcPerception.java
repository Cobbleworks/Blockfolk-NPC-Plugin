package dev.blockfolk.runtime;

import java.util.function.BooleanSupplier;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import dev.blockfolk.model.NpcInstance;

/**
 * Decides whether an NPC notices another entity it is already in range of.
 * Anything within the awareness range is noticed, as if heard or sensed; beyond
 * it the NPC needs a clear line of sight, so walls and floors hide what is
 * behind them.
 *
 * <p>
 * Callers apply their own range first; this only removes what the NPC cannot
 * see. When either entity is not loaded, the range check alone decides.
 */
public final class NpcPerception {

    public static final double DEFAULT_AWARENESS_RANGE = 4.0;

    private final NpcInstanceRegistry instances;
    private final boolean lineOfSight;
    private final double awarenessRange;

    public NpcPerception(NpcInstanceRegistry instances, boolean lineOfSight, double awarenessRange) {
        this.instances = instances;
        this.lineOfSight = lineOfSight;
        this.awarenessRange = Math.max(0.0, awarenessRange);
    }

    /** Line of sight off: NPCs notice everything in range, as before. */
    public static NpcPerception unrestricted(NpcInstanceRegistry instances) {
        return new NpcPerception(instances, false, DEFAULT_AWARENESS_RANGE);
    }

    public boolean perceives(NpcInstance observer, Entity target) {
        if (!lineOfSight || target == null) {
            return true;
        }
        LivingEntity eyes = instances.findEntity(observer).orElse(null);
        if (eyes == null || eyes.equals(target)) {
            return true;
        }
        if (eyes.getWorld() != target.getWorld()) {
            return false;
        }
        return perceives(eyes.getLocation().distanceSquared(target.getLocation()), awarenessRange,
                () -> eyes.hasLineOfSight(target));
    }

    public boolean perceives(NpcInstance observer, NpcInstance target) {
        if (!lineOfSight) {
            return true;
        }
        return instances.findEntity(target).map(entity -> perceives(observer, (Entity) entity)).orElse(true);
    }

    static boolean perceives(double distanceSquared, double awarenessRange, BooleanSupplier lineOfSight) {
        return distanceSquared <= awarenessRange * awarenessRange || lineOfSight.getAsBoolean();
    }
}
