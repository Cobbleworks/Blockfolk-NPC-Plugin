package dev.blockfolk.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.blockfolk.util.TextUtil;

public final class AiDecisionParser {

    static final int MAX_ACTIONS = 3;
    static final String OVER_LIMIT_REJECTION = "more than " + MAX_ACTIONS
            + " actions in one response; send the rest in your next response";
    private static final int MAX_HINT_ALIASES = 12;
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
        List<AiCallOutcome> outcomes = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(TextUtil.stripCodeFence(json)).getAsJsonObject();
            if (!root.has("actions") || !root.get("actions").isJsonArray())
                return AiParseResult.invalid(doNothing(), "missing actions array");
            JsonArray actions = root.getAsJsonArray("actions");
            int index = 0;
            for (JsonElement element : actions) {
                int call = callIndex(element, index++);
                if (!element.isJsonObject()) {
                    outcomes.add(AiCallOutcome.rejected(call, null, "malformed call"));
                    continue;
                }
                AiCallOutcome outcome = evaluate(call, element.getAsJsonObject(), settings, targets, availableActions);
                if (outcome.isAccepted() && accepted.size() >= MAX_ACTIONS)
                    outcome = AiCallOutcome.rejected(call, outcome.function(), OVER_LIMIT_REJECTION);
                if (outcome.isAccepted())
                    accepted.add(outcome.action());
                outcomes.add(outcome);
            }
        } catch (RuntimeException ignored) {
            return AiParseResult.invalid(doNothing(), "malformed JSON object");
        }
        long rejected = outcomes.stream().filter(outcome -> !outcome.isAccepted()).count();
        AiDecision decision = accepted.isEmpty() ? doNothing() : new AiDecision(accepted);
        if (accepted.isEmpty() && rejected > 0)
            return new AiParseResult<>(decision, false, "all actions were rejected", outcomes);
        return new AiParseResult<>(decision, true, rejected == 0 ? "" : rejected + " action(s) rejected", outcomes);
    }

    private static int callIndex(JsonElement element, int fallback) {
        if (element.isJsonObject() && element.getAsJsonObject().has("call")) {
            try {
                return element.getAsJsonObject().get("call").getAsInt();
            } catch (RuntimeException ignored) {
                // Fall back to array order.
            }
        }
        return fallback;
    }

    private static AiCallOutcome evaluate(int call, JsonObject object, AiControlSettings settings,
            AiTargetSnapshot targets, Set<AiActionType> availableActions) {
        String rawType = string(object, "type", false);
        String function = rawType == null ? null : rawType.toLowerCase(Locale.ROOT);
        if (rawType == null)
            return AiCallOutcome.rejected(call, null, "missing function name");
        String clientRejection = string(object, "rejection", false);
        if (clientRejection != null)
            return AiCallOutcome.rejected(call, function, clientRejection);
        AiActionType type;
        try {
            type = AiActionType.fromModel(rawType);
        } catch (IllegalArgumentException exception) {
            return AiCallOutcome.rejected(call, function, "unknown function; call only the functions provided");
        }
        function = type.name().toLowerCase(Locale.ROOT);
        // Permanent facts are extracted by the post-action dream request, never by
        // the gameplay decision that is returned to the NPC action runner.
        if (type == AiActionType.REMEMBER_FACT)
            return AiCallOutcome.rejected(call, function, "facts are remembered automatically after the conversation");
        if ((availableActions != null && !availableActions.contains(type)) || (type != AiActionType.DO_NOTHING
                && type != AiActionType.DROP_ITEM && !settings.allowedActions().contains(type)))
            return AiCallOutcome.rejected(call, function, "this NPC cannot use " + function + " right now");
        String text = string(object, "text", false);
        String target = string(object, "target", true);
        String animation = string(object, "animation", true);
        String locationName = string(object, "name", false);
        if (target != null && target.isBlank())
            target = null;
        if (target != null && targets != null)
            target = targets.canonical(target);
        if (type == AiActionType.SAY && (text == null || text.isBlank()))
            return AiCallOutcome.rejected(call, function, "missing text");
        if (type == AiActionType.REMEMBER_LOCATION
                && (locationName == null || locationName.isBlank() || locationName.length() > 64))
            return AiCallOutcome.rejected(call, function, "name must be 1 to 64 characters");
        if (requiresTarget(type) && target == null)
            return AiCallOutcome.rejected(call, function, "missing target" + hint(type, targets));
        if (target != null && !validTarget(type, target))
            return AiCallOutcome.rejected(call, function,
                    "'" + target + "' is not a valid " + function + " target" + hint(type, targets));
        if (target != null && !targetBound(type, target, targets))
            return AiCallOutcome.rejected(call, function,
                    "'" + target + "' is not present in the current context" + hint(type, targets));
        if (type == AiActionType.PLAY_ANIMATION && (animation == null || !ANIMATIONS.contains(animation)))
            return AiCallOutcome.rejected(call, function, "animation must be wave, jump, sneak, or stand");
        return AiCallOutcome.accepted(call, function,
                new AiDecision.Action(type, text, target, animation, locationName));
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
        if (type == AiActionType.MOVE_TO && AiTargetSnapshot.isCoordinates(target))
            return true;
        if (TARGETS.contains(target))
            return true;
        return type == AiActionType.MOVE_TO && (target.matches("nearby_(location|player|entity)_[1-9][0-9]*")
                || target.matches("nearby_npc_[a-z0-9_]+"));
    }

    private static boolean targetBound(AiActionType type, String target, AiTargetSnapshot targets) {
        if (targets == null)
            return true;
        return switch (type) {
            case START_COMBAT, FLEE_FROM, FOLLOW ->
                targets.entityId(target).isPresent() || targets.npcInstanceId(target).isPresent();
            case MOVE_TO -> targets.entityId(target).isPresent() || targets.npcInstanceId(target).isPresent()
                    || targets.location(target).isPresent();
            case INTERACT -> target.equals("nearest_switch") || target.equals("take_from_container")
                    || target.equals("store_in_container") || targets.location(target).isPresent();
            default -> true;
        };
    }

    /** Lists valid aliases for this action so the model can correct itself. */
    private static String hint(AiActionType type, AiTargetSnapshot targets) {
        String fixed = switch (type) {
            case MINE_BLOCKS -> "; use ores, trees, mineable_blocks, or a listed material name";
            case DROP_ITEM -> "; use a listed inventory_slot_N";
            case MOVE_TO -> "; block coordinates as x,y,z within " + (int) AiTargetSnapshot.MAX_COORDINATE_DISTANCE
                    + " blocks are also accepted";
            default -> "";
        };
        if (targets == null)
            return fixed;
        List<String> valid = targets.aliases().stream().filter(alias -> validTarget(type, alias))
                .filter(alias -> targetBound(type, alias, targets)).limit(MAX_HINT_ALIASES).toList();
        if (valid.isEmpty())
            return fixed.isEmpty() ? "; no valid targets are currently in range" : fixed;
        return "; valid targets: " + String.join(", ", valid) + fixed;
    }

    private static String string(JsonObject object, String name, boolean normalize) {
        try {
            if (!object.has(name) || !object.get(name).isJsonPrimitive())
                return null;
            String value = object.get(name).getAsString().trim();
            return normalize ? value.toLowerCase(Locale.ROOT) : value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static AiDecision doNothing() {
        return new AiDecision(List.of(new AiDecision.Action(AiActionType.DO_NOTHING, null, null, null)));
    }
}
