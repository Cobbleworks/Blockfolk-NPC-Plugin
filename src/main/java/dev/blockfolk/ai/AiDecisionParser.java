package dev.blockfolk.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.blockfolk.util.TextUtil;

public final class AiDecisionParser {

    private static final int MAX_ACTIONS = 3;
    private static final Set<String> TARGETS = Set.of("triggering_player", "triggering_entity", "nearest_player",
            "nearest_attackable", "current_target");
    private static final Set<String> ANIMATIONS = Set.of("wave", "jump", "sneak", "stand");

    private AiDecisionParser() {
    }

    public static AiDecision parse(String json, AiControlSettings settings) {
        return parseDetailed(json, settings).value();
    }

    public static AiParseResult<AiDecision> parseDetailed(String json, AiControlSettings settings) {
        return parseDetailed(json, settings, null);
    }

    public static AiParseResult<AiDecision> parseDetailed(String json, AiControlSettings settings,
            AiTargetSnapshot targets) {
        return parseDetailed(json, settings, targets, null);
    }

    public static AiParseResult<AiDecision> parseDetailed(String json, AiControlSettings settings,
            AiTargetSnapshot targets, Set<AiActionType> availableActions) {
        List<AiDecision.Action> accepted = new ArrayList<>();
        int rejected = 0;
        try {
            JsonObject root = JsonParser.parseString(TextUtil.stripCodeFence(json)).getAsJsonObject();
            if (!root.has("actions") || !root.get("actions").isJsonArray())
                return AiParseResult.invalid(doNothing(), "missing actions array");
            JsonArray actions = root.getAsJsonArray("actions");
            for (JsonElement element : actions) {
                if (accepted.size() >= MAX_ACTIONS)
                    break;
                if (!element.isJsonObject()) {
                    rejected++;
                    continue;
                }
                java.util.Optional<AiDecision.Action> action = parseAction(element.getAsJsonObject(), settings,
                        availableActions);
                if (action.isPresent() && targetBound(action.get(), targets))
                    accepted.add(action.get());
                else
                    rejected++;
            }
        } catch (RuntimeException ignored) {
            return AiParseResult.invalid(doNothing(), "malformed JSON object");
        }
        AiDecision decision = accepted.isEmpty() ? doNothing() : new AiDecision(accepted);
        if (accepted.isEmpty() && rejected > 0)
            return AiParseResult.invalid(decision, "all actions were rejected");
        return new AiParseResult<>(decision, true, rejected == 0 ? "" : rejected + " action(s) rejected");
    }

    private static java.util.Optional<AiDecision.Action> parseAction(JsonObject object, AiControlSettings settings,
            Set<AiActionType> availableActions) {
        if (!object.has("type") || !object.get("type").isJsonPrimitive())
            return java.util.Optional.empty();
        AiActionType type;
        try {
            type = AiActionType.fromModel(object.get("type").getAsString());
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
        if (availableActions != null && !availableActions.contains(type))
            return java.util.Optional.empty();
        // Permanent facts are extracted by the post-action dream request, never by
        // the gameplay decision that is returned to the NPC action runner.
        if (type == AiActionType.REMEMBER_FACT) {
            return java.util.Optional.empty();
        }
        if (type != AiActionType.DO_NOTHING && type != AiActionType.REMEMBER_FACT && type != AiActionType.DROP_ITEM
                && !settings.allowedActions().contains(type)) {
            return java.util.Optional.empty();
        }
        String text = string(object, "text", false);
        String target = string(object, "target", true);
        String animation = string(object, "animation", true);
        if (type == AiActionType.SAY && (text == null || text.isBlank()))
            return java.util.Optional.empty();
        if (target != null && !validTarget(type, target))
            return java.util.Optional.empty();
        if (requiresTarget(type) && target == null)
            return java.util.Optional.empty();
        if (type == AiActionType.PLAY_ANIMATION && (animation == null || !ANIMATIONS.contains(animation)))
            return java.util.Optional.empty();
        return java.util.Optional.of(new AiDecision.Action(type, text, target, animation));
    }

    private static boolean requiresTarget(AiActionType type) {
        return type == AiActionType.FLEE_FROM || type == AiActionType.FOLLOW || type == AiActionType.MOVE_TO
                || type == AiActionType.DROP_ITEM || type == AiActionType.MINE_BLOCKS;
    }

    private static boolean validTarget(AiActionType type, String target) {
        if (type == AiActionType.DROP_ITEM)
            return target.matches("inventory_slot_[1-9][0-9]*");
        if (type == AiActionType.MINE_BLOCKS)
            return target.matches("[a-z0-9_]{1,64}");
        if (type == AiActionType.INTERACT) {
            return target.equals("nearest_switch") || target.matches("nearby_(lever|button)_[1-9][0-9]*")
                    || target.matches("(take_from|store_in)_container(?:_[1-9][0-9]*)?");
        }
        if (type == AiActionType.START_COMBAT) {
            return TARGETS.contains(target) || target.matches("nearby_(player|entity)_[1-9][0-9]*")
                    || target.matches("nearby_npc_[a-z0-9_]+");
        }
        if (type == AiActionType.FLEE_FROM)
            return TARGETS.contains(target) || target.matches("nearby_(player|entity)_[1-9][0-9]*")
                    || target.matches("nearby_npc_[a-z0-9_]+");
        if (type == AiActionType.FOLLOW) {
            if (target.equals("triggering_player") || target.equals("nearest_player"))
                return true;
            if (TARGETS.contains(target))
                return false;
            return target.matches("nearby_player_[1-9][0-9]*") || target.matches("[a-z0-9_]{1,16}");
        }
        if (TARGETS.contains(target))
            return true;
        return type == AiActionType.MOVE_TO && (target.matches("nearby_(location|player|entity)_[1-9][0-9]*")
                || target.matches("nearby_npc_[a-z0-9_]+"));
    }

    private static boolean targetBound(AiDecision.Action action, AiTargetSnapshot targets) {
        if (targets == null || action.target() == null)
            return true;
        String target = action.target();
        return switch (action.type()) {
            case START_COMBAT, FLEE_FROM, FOLLOW ->
                targets.entityId(target).isPresent() || targets.npcInstanceId(target).isPresent();
            case MOVE_TO -> targets.entityId(target).isPresent() || targets.npcInstanceId(target).isPresent()
                    || targets.location(target).isPresent();
            case INTERACT -> target.equals("nearest_switch") || target.equals("take_from_container")
                    || target.equals("store_in_container") || targets.location(target).isPresent();
            default -> true;
        };
    }

    private static String string(JsonObject object, String name, boolean normalize) {
        try {
            if (!object.has(name) || !object.get(name).isJsonPrimitive())
                return null;
            String value = object.get(name).getAsString().trim();
            return normalize ? value.toLowerCase(java.util.Locale.ROOT) : value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static AiDecision doNothing() {
        return new AiDecision(List.of(new AiDecision.Action(AiActionType.DO_NOTHING, null, null, null)));
    }
}
