package dev.blockfolk.runtime;

import java.util.Locale;

/** Words a death an NPC saw or heard nearby, for its AI event memory. */
final class WitnessedDeath {

    private WitnessedDeath() {
    }

    /**
     * "The player Alex killed a cow nearby." or, without a killer, "The player Alex
     * died nearby (fall)."; {@code cause} may be null.
     */
    static String describe(String killer, String victim, String cause) {
        if (killer != null)
            return capitalize(killer) + " killed " + victim + " nearby.";
        String readableCause = cause == null || cause.isBlank()
                ? ""
                : " (" + cause.toLowerCase(Locale.ROOT).replace('_', ' ') + ")";
        return capitalize(victim) + " died nearby" + readableCause + ".";
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
