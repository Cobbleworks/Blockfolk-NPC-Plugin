package dev.blockfolk.gui;

import java.util.function.Function;

import dev.blockfolk.model.Scene;
import dev.blockfolk.model.SceneBeat;
import dev.blockfolk.model.SceneRole;

/** Short texts describing beats, shared by the script grid and role editor. */
final class SceneBeatText {

    private SceneBeatText() {
    }

    /** When the beat moves on, e.g. "after 10 s" or "when Luigi is done". */
    static String advance(Scene scene, SceneBeat beat, Function<SceneRole, String> roleName) {
        return switch (beat.getAdvance()) {
            case EVERYONE_DONE -> "when everyone is done";
            case AFTER_TIME -> "after " + seconds(beat.getSeconds());
            case ROLE_DONE ->
                scene.findRole(beat.getAdvanceRoleId()).map(role -> "when " + roleName.apply(role) + " is done")
                        .orElse("when everyone is done (no role chosen)");
            case NEXT_BEAT_ONLY -> "only by a Next Beat action";
        };
    }

    static String seconds(int seconds) {
        if (seconds < 60)
            return seconds + " s";
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return rest == 0 ? minutes + " min" : minutes + " min " + rest + " s";
    }
}
