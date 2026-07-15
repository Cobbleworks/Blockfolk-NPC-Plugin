package dev.blockfolk.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Shared visual language for Blockfolk's player-facing text. */
public final class UiText {

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
        return Component.text(section + ": ", NamedTextColor.DARK_AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(subject, NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
    }

    public static Component npcDialog(String npcName, String line) {
        return Component.text(npcName, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .append(plain(": ", NamedTextColor.DARK_GRAY))
                .append(plain(line, NamedTextColor.WHITE));
    }

    private static Component feedback(String message, NamedTextColor color) {
        return PREFIX.append(plain(message, color));
    }

    private static Component plain(String message, NamedTextColor color) {
        return Component.text(message, color).decoration(TextDecoration.BOLD, false);
    }
}
