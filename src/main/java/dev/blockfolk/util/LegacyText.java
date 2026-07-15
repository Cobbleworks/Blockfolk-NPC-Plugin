package dev.blockfolk.util;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Bridges compact, color-coded GUI strings to the component-based Paper API. */
public final class LegacyText {

    public static final String BLACK = "\u00a70";
    public static final String DARK_GRAY = "\u00a78";
    public static final String RED = "\u00a7c";
    public static final String GREEN = "\u00a7a";
    public static final String GOLD = "\u00a76";
    public static final String YELLOW = "\u00a7e";
    public static final String AQUA = "\u00a7b";
    public static final String LIGHT_PURPLE = "\u00a7d";
    public static final String GRAY = "\u00a77";
    public static final String WHITE = "\u00a7f";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private LegacyText() {
    }

    public static Component component(String text) {
        return LEGACY.deserialize(text);
    }

    public static List<Component> components(List<String> lines) {
        return lines.stream().map(LegacyText::component).toList();
    }

    public static String plainText(Component component) {
        return PLAIN.serialize(component);
    }
}
