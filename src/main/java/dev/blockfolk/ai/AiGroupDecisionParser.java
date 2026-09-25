package dev.blockfolk.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.blockfolk.util.TextUtil;

/**
 * Parses a group response while enforcing each NPC's own enabled capabilities.
 */
public final class AiGroupDecisionParser {

    private AiGroupDecisionParser() {
    }

    public static Map<String, AiDecision> parse(String json, Map<String, AiControlSettings> participants) {
        return parseDetailed(json, participants).value();
    }

    public static AiParseResult<Map<String, AiDecision>> parseDetailed(String json,
            Map<String, AiControlSettings> participants) {
        return parseDetailed(json, participants, null);
    }

    public static AiParseResult<Map<String, AiDecision>> parseDetailed(String json,
            Map<String, AiControlSettings> participants, Map<String, AiTargetSnapshot> targetsByParticipant) {
        return parseDetailed(json, participants, targetsByParticipant, null);
    }

    public static AiParseResult<Map<String, AiDecision>> parseDetailed(String json,
            Map<String, AiControlSettings> participants, Map<String, AiTargetSnapshot> targetsByParticipant,
            Map<String, java.util.Set<AiActionType>> availableByParticipant) {
        Map<String, AiDecision> accepted = new LinkedHashMap<>();
        int rejected = 0;
        try {
            JsonObject root = JsonParser.parseString(TextUtil.stripCodeFence(json)).getAsJsonObject();
            if (!root.has("responses") || !root.get("responses").isJsonArray())
                return AiParseResult.invalid(Map.of(), "missing responses array");
            JsonArray responses = root.getAsJsonArray("responses");
            for (JsonElement element : responses) {
                if (!element.isJsonObject()) {
                    rejected++;
                    continue;
                }
                JsonObject response = element.getAsJsonObject();
                String alias = string(response, "npc");
                AiControlSettings settings = participants.get(alias);
                if (settings == null || accepted.containsKey(alias)) {
                    rejected++;
                    continue;
                }
                JsonObject decision = new JsonObject();
                if (response.has("actions"))
                    decision.add("actions", response.get("actions"));
                AiTargetSnapshot targets = targetsByParticipant == null ? null : targetsByParticipant.get(alias);
                java.util.Set<AiActionType> available = availableByParticipant == null
                        ? null
                        : availableByParticipant.get(alias);
                AiParseResult<AiDecision> parsed = AiDecisionParser.parseDetailed(decision.toString(), settings,
                        targets, available);
                if (parsed.usable())
                    accepted.put(alias, parsed.value());
                if (!parsed.issue().isEmpty())
                    rejected++;
            }
        } catch (RuntimeException ignored) {
            return AiParseResult.invalid(Map.of(), "malformed JSON object");
        }
        Map<String, AiDecision> decisions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(accepted));
        if (accepted.isEmpty() && rejected > 0)
            return AiParseResult.invalid(decisions, "all NPC responses were rejected");
        return new AiParseResult<>(decisions, true, rejected == 0 ? "" : rejected + " response(s) rejected");
    }

    private static String string(JsonObject object, String name) {
        try {
            return object.has(name) && object.get(name).isJsonPrimitive()
                    ? object.get(name).getAsString().trim().toLowerCase(java.util.Locale.ROOT)
                    : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

}
