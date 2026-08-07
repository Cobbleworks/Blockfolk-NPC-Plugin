package dev.blockfolk.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.NpcQuestion;
import dev.blockfolk.model.QuestionOption;

final class BehaviourActionCodec {

    private BehaviourActionCodec() {
    }

    static Map<String, Object> encode(BehaviourAction action) {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("type", action.type().name().toLowerCase(Locale.ROOT));
        if (action.value() != null)
            stored.put("value", action.value());
        if (action.question() != null)
            stored.put("question", encodeQuestion(action.question()));
        return stored;
    }

    static BehaviourAction decode(Map<?, ?> stored) {
        Object rawType = stored.get("type");
        if (rawType == null)
            throw new IllegalArgumentException("Action type is required");
        BehaviourActionType type = BehaviourActionType.fromStored(rawType.toString());
        if (type == BehaviourActionType.ASK_QUESTION) {
            Object rawQuestion = stored.get("question");
            if (!(rawQuestion instanceof Map<?, ?> question)) {
                throw new IllegalArgumentException("Question data is required");
            }
            return BehaviourAction.ask(decodeQuestion(question));
        }
        Object value = stored.get("value");
        return new BehaviourAction(type, value == null ? null : value.toString());
    }

    static List<Map<String, Object>> encodeList(List<BehaviourAction> actions) {
        return actions.stream().map(BehaviourActionCodec::encode).toList();
    }

    static List<BehaviourAction> decodeList(Object raw) {
        if (!(raw instanceof List<?> list))
            return List.of();
        List<BehaviourAction> actions = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map)
                actions.add(decode(map));
        }
        return actions;
    }

    private static Map<String, Object> encodeQuestion(NpcQuestion question) {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("id", question.id().toString());
        stored.put("prompt", question.prompt());
        stored.put("options", question.options().stream().map(option -> {
            Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("label", option.label());
            encoded.put("actions", encodeList(option.actions()));
            return encoded;
        }).toList());
        stored.put("cancel-actions", encodeList(question.cancelActions()));
        return stored;
    }

    private static NpcQuestion decodeQuestion(Map<?, ?> stored) {
        Object rawId = stored.get("id");
        UUID id = rawId == null ? UUID.randomUUID() : UUID.fromString(rawId.toString());
        Object rawPrompt = stored.get("prompt");
        String prompt = rawPrompt == null ? "" : rawPrompt.toString();
        List<QuestionOption> options = new ArrayList<>();
        Object rawOptions = stored.get("options");
        if (rawOptions instanceof List<?> list) {
            for (Object rawOption : list) {
                if (options.size() == NpcQuestion.MAX_OPTIONS)
                    break;
                if (!(rawOption instanceof Map<?, ?> option))
                    continue;
                Object label = option.get("label");
                if (label != null)
                    options.add(new QuestionOption(label.toString(), decodeList(option.get("actions"))));
            }
        }
        return new NpcQuestion(id, prompt, options, decodeList(stored.get("cancel-actions")));
    }
}
