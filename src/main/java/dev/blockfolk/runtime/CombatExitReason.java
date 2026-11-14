package dev.blockfolk.runtime;

/**
 * Why an NPC left combat, worded for the AI so its role-play matches what
 * actually happened.
 */
public enum CombatExitReason {
    /** The opponent died to this NPC's attacks. */
    DEFEATED_TARGET,
    /** The opponent died, but not by this NPC's hand. */
    TARGET_DIED,
    /**
     * The opponent left chase range, changed world, or can no longer be attacked.
     */
    TARGET_LOST,
    /** The opponent stayed out of sight or out of reach for too long. */
    TARGET_UNREACHABLE,
    /**
     * A flight ended because the threat was left behind or the flee timer ran out.
     */
    ESCAPED,
    /**
     * Combat was stopped deliberately: an action, new fight options, or
     * invulnerability.
     */
    STOPPED,
    /** The NPC itself died. */
    NPC_DIED;

    public String describe(String opponent) {
        String name = opponent == null || opponent.isBlank() ? "your opponent" : opponent;
        return switch (this) {
            case DEFEATED_TARGET -> "Combat ended: you defeated " + name + ".";
            case TARGET_DIED -> "Combat ended: " + name + " died, but not by your hand.";
            case TARGET_LOST -> "Combat ended: " + name + " got away or moved out of your reach.";
            case TARGET_UNREACHABLE -> "Combat ended: you could not reach " + name + " and gave up the chase.";
            case ESCAPED -> "Combat ended: you escaped from " + name + ".";
            case STOPPED -> "Combat ended: you stopped fighting " + name + ".";
            case NPC_DIED -> "Combat ended: you were killed while fighting " + name + ".";
        };
    }
}
