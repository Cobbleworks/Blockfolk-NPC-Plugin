package dev.blockfolk.util;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Shared visual language for Blockfolk's player-facing text. */
public final class UiText {

    private static final int NPC_DIALOG_CHUNK_LENGTH = 240;
    private static final Component PREFIX = Component.text("Blockfolk ", NamedTextColor.DARK_AQUA)
            .decorate(TextDecoration.BOLD);

    private UiText() {
    }

    public static Component info(String message) {
        return feedback(message, NamedTextColor.GRAY);
    }

    public static Component success(String message) {
        return feedback(message, NamedTextColor.GREEN);
    }

    public static Component warning(String message) {
        return feedback(message, NamedTextColor.GOLD);
    }

    public static Component error(String message) {
        return feedback(message, NamedTextColor.RED);
    }

    public static Component prompt(String message) {
        return PREFIX.append(plain(message, NamedTextColor.YELLOW));
    }

    public static Component title(String title) {
        return Component.text(title, NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD);
    }

    public static Component title(String section, String subject) {
        return title(section, subject, NamedTextColor.GREEN);
    }

    public static Component manageTitle(String subject) {
        return title("Manage", subject, NamedTextColor.GOLD);
    }

    private static Component title(String section, String subject, NamedTextColor subjectColor) {
        return Component.text(section + ": ", NamedTextColor.DARK_AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(subject, subjectColor).decorate(TextDecoration.BOLD));
    }

    public static Component npcDialog(String npcName, String line) {
        return npcDialog(npcName, line, NamedTextColor.GOLD);
    }

    public static Component npcDialog(String npcName, String line, NamedTextColor nameColor) {
        return Component.text(npcName, nameColor == null ? NamedTextColor.GOLD : nameColor)
                .decorate(TextDecoration.BOLD)
                .append(plain(": ", NamedTextColor.DARK_GRAY))
                .append(plain(line, NamedTextColor.WHITE));
    }

    public static List<Component> npcDialogMessages(String npcName, String text) {
        return npcDialogMessages(npcName, text, NamedTextColor.GOLD);
    }

    public static List<Component> npcDialogMessages(String npcName, String text, NamedTextColor nameColor) {
        return splitNpcDialog(text).stream()
                .map(line -> npcDialog(npcName, line, nameColor))
                .toList();
    }

    static List<String> splitNpcDialog(String text) {
        String remaining = text == null ? "" : text.strip();
        if (remaining.isEmpty()) return List.of();

        List<String> messages = new ArrayList<>();
        while (remaining.length() > NPC_DIALOG_CHUNK_LENGTH) {
            int splitAt = lastWhitespaceBefore(remaining, NPC_DIALOG_CHUNK_LENGTH);
            if (splitAt <= 0) splitAt = safeEndIndex(remaining, NPC_DIALOG_CHUNK_LENGTH);
            messages.add(remaining.substring(0, splitAt).stripTrailing());
            remaining = remaining.substring(splitAt).stripLeading();
        }
        if (!remaining.isEmpty()) messages.add(remaining);
        return List.copyOf(messages);
    }

    private static int lastWhitespaceBefore(String text, int limit) {
        int end = safeEndIndex(text, limit);
        for (int index = end; index > 0; index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) return index - 1;
        }
        return -1;
    }

    private static int safeEndIndex(String text, int limit) {
        int end = Math.min(limit, text.length());
        if (end < text.length() && end > 0
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return end;
    }

    private static Component feedback(String message, NamedTextColor color) {
        return PREFIX.append(plain(message, color));
    }

    private static Component plain(String message, NamedTextColor color) {
        return Component.text(message, color).decoration(TextDecoration.BOLD, false);
    }
}
