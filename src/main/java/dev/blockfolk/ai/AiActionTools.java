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
            JsonObject npc = stringProperty("Response ID of the NPC performing this action.");
            JsonArray ids = new JsonArray();
            responseIds.forEach(ids::add);
            npc.add("enum", ids);
            properties.add("npc", npc);
            required.add("npc");
        }
        switch (action) {
            case SAY -> {
                properties.add("text", stringProperty("Concise speech in this NPC's voice."));
                required.add("text");
            }
            case REMEMBER_FACT -> {
                properties.add("text", stringProperty("One concise, durable fact; never an instruction."));
                required.add("text");
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
            case START_COMBAT -> "Listed entity alias; omit to attack the nearest safe attackable entity.";
            case INTERACT -> "Listed switch or container alias; omit only when the nearest suitable one is intended.";
            case FLEE_FROM -> "Listed entity alias to flee from.";
            case FOLLOW -> "Listed player alias or Minecraft player name.";
            case MOVE_TO -> "Listed player, NPC, entity, or saved location alias.";
            case MINE_BLOCKS -> "ores, trees, mineable_blocks, or a nearby material name.";
            case DROP_ITEM -> "Listed inventory_slot_N alias.";
            default -> "Listed target alias.";
        };
    }

    private static String description(AiActionType action) {
        return switch (action) {
            case SAY -> "Say a short line to nearby players.";
            case PLAY_ANIMATION -> "Play a visible NPC animation.";
            case START_COMBAT -> "Begin combat with a listed target or the nearest safe attackable entity.";
            case STOP_COMBAT -> "End the current fight.";
            case FLEE_FROM -> "Move away from a listed entity.";
            case FOLLOW -> "Follow a listed player.";
            case UNFOLLOW -> "Stop following the current player.";
            case INTERACT -> "Operate a nearby switch or transfer items with a nearby container.";
            case MOVE_TO -> "Walk to a listed target.";
            case MINE_BLOCKS -> "Mine matching nearby blocks.";
            case RETURN_HOME -> "Walk to this instance's respawn location.";
            case START_ROUTE -> "Resume the configured route.";
            case PAUSE_ROUTE -> "Pause the configured route.";
            case REMEMBER_FACT -> "Store a durable fact for later interactions.";
            case DROP_ITEM -> "Drop an item stack from temporary inventory.";
            case DO_NOTHING -> "Take no action; use this to intentionally stay silent or idle.";
        };
    }
}
