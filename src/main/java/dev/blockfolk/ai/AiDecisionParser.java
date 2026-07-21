package dev.blockfolk.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class AiDecisionParser {

    private static final int MAX_ACTIONS = 3;
    private static final Set<String> TARGETS = Set.of(
            "triggering_player", "triggering_entity", "nearest_player", "nearest_attackable", "current_target");
    private static final Set<String> ANIMATIONS = Set.of("wave", "jump", "sneak", "stand");

    private AiDecisionParser() { }

    public static AiDecision parse(String json, AiControlSettings settings) {
        List<AiDecision.Action> accepted = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(stripFence(json)).getAsJsonObject();
            JsonArray actions = root.has("actions") && root.get("actions").isJsonArray()
                    ? root.getAsJsonArray("actions") : new JsonArray();
            for (JsonElement element : actions) {
                if (accepted.size() >= MAX_ACTIONS || !element.isJsonObject()) break;
                parseAction(element.getAsJsonObject(), settings).ifPresent(accepted::add);
            }
        } catch (RuntimeException ignored) {
            return doNothing();
        }
        return accepted.isEmpty() ? doNothing() : new AiDecision(accepted);
    }

    private static java.util.Optional<AiDecision.Action> parseAction(
            JsonObject object, AiControlSettings settings) {
        if (!object.has("type") || !object.get("type").isJsonPrimitive()) return java.util.Optional.empty();
        AiActionType type;
        try {
            type = AiActionType.fromModel(object.get("type").getAsString());
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
        if (type == AiActionType.REMEMBER_FACT && !settings.memoryEnabled()) {
            return java.util.Optional.empty();
        }
        if (type == AiActionType.DROP_ITEM && !settings.inventoryEnabled()) {
            return java.util.Optional.empty();
        }
        if (type != AiActionType.DO_NOTHING && type != AiActionType.REMEMBER_FACT
                && type != AiActionType.DROP_ITEM
                && !settings.allowedActions().contains(type)) {
            return java.util.Optional.empty();
        }
        String text = string(object, "text", false);
        String target = string(object, "target", true);
        String animation = string(object, "animation", true);
        if (type == AiActionType.GATHER_BLOCKS && target != null) {
            String canonicalTarget = MiningTarget.canonicalSelection(target);
            if (canonicalTarget == null) return java.util.Optional.empty();
            target = canonicalTarget;
        }
        if (type == AiActionType.SAY && (text == null || text.isBlank())) return java.util.Optional.empty();
        if (type == AiActionType.REMEMBER_FACT && (text == null || text.isBlank())) {
            return java.util.Optional.empty();
        }
        if (target != null && !validTarget(type, target)) return java.util.Optional.empty();
        if (requiresTarget(type) && target == null) return java.util.Optional.empty();
        if (type == AiActionType.PLAY_ANIMATION
                && (animation == null || !ANIMATIONS.contains(animation))) return java.util.Optional.empty();
        return java.util.Optional.of(new AiDecision.Action(type, text, target, animation));
    }

    private static boolean requiresTarget(AiActionType type) {
        return type == AiActionType.FLEE_FROM || type == AiActionType.FOLLOW
                || type == AiActionType.MOVE_TO || type == AiActionType.DROP_ITEM;
    }

    private static boolean validTarget(AiActionType type, String target) {
        if (type == AiActionType.GATHER_BLOCKS) return MiningTarget.valid(target);
        if (type == AiActionType.DROP_ITEM) return target.matches("inventory_slot_[1-9][0-9]*");
        if (type == AiActionType.FOLLOW) {
            if (target.equals("triggering_player") || target.equals("nearest_player")) return true;
            if (TARGETS.contains(target)) return false;
            return target.matches("nearby_player_[1-9][0-9]*")
                    || target.matches("[a-z0-9_]{1,16}");
        }
        if (TARGETS.contains(target)) return true;
        if ((type == AiActionType.START_COMBAT || type == AiActionType.FLEE_FROM
                || type == AiActionType.MOVE_TO) && target.matches("[a-z0-9_]{1,16}")) return true;
        return type == AiActionType.MOVE_TO && target.matches("nearby_(location|player|npc|entity)_[1-9][0-9]*");
    }

    private static String string(JsonObject object, String name, boolean normalize) {
        try {
            if (!object.has(name) || !object.get(name).isJsonPrimitive()) return null;
            String value = object.get(name).getAsString().trim();
            return normalize ? value.toLowerCase(java.util.Locale.ROOT) : value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stripFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int newline = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        return newline >= 0 && end > newline ? trimmed.substring(newline + 1, end).trim() : trimmed;
    }

    private static AiDecision doNothing() {
        return new AiDecision(List.of(new AiDecision.Action(AiActionType.DO_NOTHING, null, null, null)));
    }
}
