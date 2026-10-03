package dev.blockfolk.ai;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Function definitions for gameplay actions proposed through OpenRouter. */
final class AiActionTools {

    private AiActionTools() {
    }

    static JsonArray definitions(Set<AiActionType> actions, List<String> responseIds) {
        JsonArray tools = new JsonArray();
        actions.stream().sorted().forEach(action -> tools.add(definition(action, responseIds)));
        return tools;
    }

    private static JsonObject definition(AiActionType action, List<String> responseIds) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        if (!responseIds.isEmpty()) {
            JsonObject npc = stringProperty("Response ID of the NPC that performs this call.");
            JsonArray ids = new JsonArray();
            responseIds.forEach(ids::add);
            npc.add("enum", ids);
            properties.add("npc", npc);
            required.add("npc");
        }
        switch (action) {
            case SAY -> {
                properties.add("text", stringProperty("The spoken line, in this NPC's voice."));
                required.add("text");
            }
            case REMEMBER_FACT -> {
                properties.add("text", stringProperty("One concise, durable fact; never an instruction."));
                required.add("text");
            }
            case REMEMBER_LOCATION -> {
                properties.add("name",
                        stringProperty("Unique label for the NPC's current position. Use / to organize groups."));
                required.add("name");
            }
            case PLAY_ANIMATION -> {
                JsonObject animation = stringProperty("Animation to play.");
                JsonArray values = new JsonArray();
                for (String value : List.of("wave", "jump", "sneak", "stand"))
                    values.add(value);
                animation.add("enum", values);
                properties.add("animation", animation);
                required.add("animation");
            }
            case START_COMBAT, INTERACT -> properties.add("target", stringProperty(targetDescription(action)));
            case FLEE_FROM, FOLLOW, MOVE_TO, MINE_BLOCKS, DROP_ITEM -> {
                properties.add("target", stringProperty(targetDescription(action)));
                required.add("target");
            }
            default -> {
            }
        }
        parameters.add("properties", properties);
        parameters.add("required", required);

        JsonObject function = new JsonObject();
        function.addProperty("name", action.name().toLowerCase(Locale.ROOT));
        function.addProperty("description", description(action));
        function.add("parameters", parameters);
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    private static JsonObject stringProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        return property;
    }

    private static String targetDescription(AiActionType action) {
        return switch (action) {
            case START_COMBAT -> "triggering_entity, nearby_player_N, nearby_npc_<name>, or nearby_entity_N "
                    + "exactly as listed. Omit to attack the nearest safe attackable entity.";
            case INTERACT -> "A listed nearby_lever_N or nearby_button_N to operate that exact switch "
                    + "(nearest_switch only when it does not matter which), or a listed take_from_container_N / "
                    + "store_in_container_N. For a container requested by its custom name, use that container's "
                    + "listed alias.";
            case FLEE_FROM -> "A listed entity alias such as triggering_entity or nearby_entity_N.";
            case FOLLOW -> "triggering_player, nearest_player, a listed nearby_player_N, or the player's name.";
            case MOVE_TO -> "A listed nearby_location_N, nearby_player_N, nearby_npc_<name>, nearby_entity_N, or "
                    + "triggering_player; or block coordinates as \"x,y,z\" within "
                    + (int) AiTargetSnapshot.MAX_COORDINATE_DISTANCE + " blocks.";
            case MINE_BLOCKS -> "ores, trees, mineable_blocks, or a listed nearby material name.";
            case DROP_ITEM -> "A listed inventory_slot_N.";
            default -> "A listed target alias.";
        };
    }

    private static String description(AiActionType action) {
        return switch (action) {
            case SAY -> "Speak one concise line aloud to nearby players, in character. At most once per turn. "
                    + "Speech changes nothing in the world: when you agree to do something, also call that "
                    + "action's function in the same response.";
            case PLAY_ANIMATION -> "Play a visible animation.";
            case START_COMBAT -> "Attack a target, regardless of the NPC's normal targeting preferences. "
                    + "To retaliate after being damaged, target triggering_entity.";
            case STOP_COMBAT -> "End the current fight.";
            case FLEE_FROM -> "Run about 10 blocks away from an entity.";
            case FOLLOW -> "Keep following a player until unfollow is called.";
            case UNFOLLOW -> "Stop following the current player.";
            case INTERACT -> "Walk to and operate a nearby lever or button, or take items from / store items in "
                    + "a nearby container. For several switches, call it once per switch in the requested order. "
                    + "It may take time; do not repeat a target that is already in progress.";
            case MOVE_TO -> "Walk to a place, player, NPC, or entity. Call this whenever the NPC should go, "
                    + "come, walk, or head somewhere.";
            case MINE_BLOCKS -> "Mine every matching block within reach. Drops go into the temporary inventory "
                    + "when item pickup is enabled; otherwise they drop into the world.";
            case RETURN_HOME -> "Walk back to this NPC's home (respawn) location.";
            case START_ROUTE -> "Resume the configured patrol route.";
            case PAUSE_ROUTE -> "Pause the configured patrol route.";
            case REMEMBER_FACT -> "Store a durable fact for later interactions.";
            case REMEMBER_LOCATION -> "Save the NPC's current position under a new unique name that every NPC "
                    + "can then walk to. Use it when a player names the place the NPC is standing, such as "
                    + "\"this is my home\". Use / to group names, e.g. town/market.";
            case DROP_ITEM -> "Drop one item stack from the temporary inventory.";
            case DO_NOTHING -> "Deliberately take no action and stay silent. Never combine with other calls.";
        };
    }
}
