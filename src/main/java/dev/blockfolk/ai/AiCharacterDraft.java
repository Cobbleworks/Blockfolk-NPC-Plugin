package dev.blockfolk.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The five Character sections the AI Quick Setup generates from a free-form
 * description.
 */
public record AiCharacterDraft(String identity, String behaviour, String goal, String information,
        String likesDislikes) {

    /**
     * Longest value kept per section, so one runaway answer cannot bloat every
     * prompt.
     */
    static final int MAX_SECTION_LENGTH = 1500;

    static final String SYSTEM_PROMPT = """
            You turn a server admin's rough notes into a character sheet for an AI-controlled Minecraft NPC.
            The sheet becomes the NPC's system prompt, so write it in the second person ("You are ...").
            Return only a JSON object with exactly these string fields:
            "identity": who the NPC is: name, background, history, and place in the world.
            "behaviour": how it speaks and acts: tone, speech quirks, temperament, how it treats players.
            "goal": what it wants or is responsible for, and what it prioritizes.
            "information": facts, lore, rules, and local knowledge it may share. Use only what the notes give
            or clearly imply; do not invent specific places, items, or mechanics of the server.
            "likes_dislikes": things it enjoys, values, avoids, or strongly dislikes.
            Aim for around 2-10 concise sentences in each field. Give each sentence a distinct detail that
            adds depth to the character. Keep every detail from the notes, and fill gaps with fitting, modest
            choices. Use fewer sentences when the notes only support a few distinct details for a field.
            Write in the language of the notes. Use an empty string only when nothing sensible fits.""";

    public AiCharacterDraft {
        identity = clean(identity);
        behaviour = clean(behaviour);
        goal = clean(goal);
        information = clean(information);
        likesDislikes = clean(likesDislikes);
    }

    static String userPrompt(String npcName, String notes) {
        String name = npcName == null || npcName.isBlank() ? "(unnamed)" : npcName.strip();
        return "NPC display name: " + name + "\n\nAdmin notes:\n" + (notes == null ? "" : notes.strip());
    }

    public boolean isEmpty() {
        return sectionCount() == 0;
    }

    public int sectionCount() {
        return (identity.isEmpty() ? 0 : 1) + (behaviour.isEmpty() ? 0 : 1) + (goal.isEmpty() ? 0 : 1)
                + (information.isEmpty() ? 0 : 1) + (likesDislikes.isEmpty() ? 0 : 1);
    }

    /**
     * Replaces every Character section of {@code settings} with this draft.
     */
    public AiControlSettings applyTo(AiControlSettings settings) {
        return settings.withCharacter(identity, behaviour, likesDislikes, goal, information);
    }

    /**
     * Reads the model's answer. Tolerates a fenced code block or text around
     * the object; throws {@link IllegalStateException} when no usable section
     * exists.
     */
    static AiCharacterDraft parse(String response) {
        if (response == null) {
            throw new IllegalStateException("The AI returned no character");
        }
        String text = response.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("The AI did not return a JSON object");
        }
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(text.substring(start, end + 1));
            if (!element.isJsonObject()) {
                throw new IllegalStateException("The AI did not return a JSON object");
            }
            root = element.getAsJsonObject();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("The AI returned malformed JSON", exception);
        }
        AiCharacterDraft draft = new AiCharacterDraft(field(root, "identity"), field(root, "behaviour", "behavior"),
                field(root, "goal", "role"), field(root, "information", "knowledge"),
                field(root, "likes_dislikes", "likesDislikes", "likes"));
        if (draft.isEmpty()) {
            throw new IllegalStateException("The AI returned an empty character");
        }
        return draft;
    }

    private static String field(JsonObject root, String... names) {
        for (String name : names) {
            JsonElement value = root.get(name);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
            if (value.isJsonArray()) {
                StringBuilder joined = new StringBuilder();
                for (JsonElement part : value.getAsJsonArray()) {
                    if (!part.isJsonPrimitive()) {
                        continue;
                    }
                    if (!joined.isEmpty()) {
                        joined.append(' ');
                    }
                    joined.append(part.getAsString().strip());
                }
                return joined.toString();
            }
        }
        return "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        return stripped.length() <= MAX_SECTION_LENGTH ? stripped : stripped.substring(0, MAX_SECTION_LENGTH).strip();
    }
}
