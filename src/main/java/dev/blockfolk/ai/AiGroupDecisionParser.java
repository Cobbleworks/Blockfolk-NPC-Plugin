package dev.blockfolk.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.blockfolk.util.TextUtil;

/** Parses a group response while enforcing each NPC's own enabled capabilities. */
public final class AiGroupDecisionParser {

    private AiGroupDecisionParser() { }

    public static Map<String, AiDecision> parse(String json, Map<String, AiControlSettings> participants) {
        Map<String, AiDecision> accepted = new LinkedHashMap<>();
        try {
            JsonObject root = JsonParser.parseString(TextUtil.stripCodeFence(json)).getAsJsonObject();
            JsonArray responses = root.has("responses") && root.get("responses").isJsonArray()
                    ? root.getAsJsonArray("responses") : new JsonArray();
            for (JsonElement element : responses) {
                if (!element.isJsonObject()) continue;
                JsonObject response = element.getAsJsonObject();
                String alias = string(response, "npc");
                AiControlSettings settings = participants.get(alias);
                if (settings == null || accepted.containsKey(alias)) continue;
                JsonObject decision = new JsonObject();
                decision.add("actions", response.has("actions") ? response.get("actions") : new JsonArray());
                accepted.put(alias, AiDecisionParser.parse(decision.toString(), settings));
            }
        } catch (RuntimeException ignored) {
            return Map.of();
        }
        return Map.copyOf(accepted);
    }

    private static String string(JsonObject object, String name) {
        try {
            return object.has(name) && object.get(name).isJsonPrimitive()
                    ? object.get(name).getAsString().trim().toLowerCase(java.util.Locale.ROOT) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

}
