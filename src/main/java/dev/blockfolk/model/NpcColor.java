package dev.blockfolk.model;

import java.util.Locale;

import org.bukkit.Material;

import net.kyori.adventure.text.format.NamedTextColor;

/** The concrete-backed colors available for an NPC's name in chat. */
public enum NpcColor {
    WHITE(Material.WHITE_CONCRETE, NamedTextColor.WHITE), ORANGE(Material.ORANGE_CONCRETE,
            NamedTextColor.GOLD), MAGENTA(Material.MAGENTA_CONCRETE, NamedTextColor.LIGHT_PURPLE), LIGHT_BLUE(
                    Material.LIGHT_BLUE_CONCRETE,
                    NamedTextColor.AQUA), YELLOW(Material.YELLOW_CONCRETE, NamedTextColor.YELLOW), LIME(
                            Material.LIME_CONCRETE,
                            NamedTextColor.GREEN), PINK(Material.PINK_CONCRETE, NamedTextColor.LIGHT_PURPLE), GRAY(
                                    Material.GRAY_CONCRETE,
                                    NamedTextColor.DARK_GRAY), LIGHT_GRAY(Material.LIGHT_GRAY_CONCRETE,
                                            NamedTextColor.GRAY), CYAN(Material.CYAN_CONCRETE,
                                                    NamedTextColor.DARK_AQUA), PURPLE(Material.PURPLE_CONCRETE,
                                                            NamedTextColor.DARK_PURPLE), BLUE(Material.BLUE_CONCRETE,
                                                                    NamedTextColor.BLUE), BROWN(Material.BROWN_CONCRETE,
                                                                            NamedTextColor.GOLD), GREEN(
                                                                                    Material.GREEN_CONCRETE,
                                                                                    NamedTextColor.DARK_GREEN), RED(
                                                                                            Material.RED_CONCRETE,
                                                                                            NamedTextColor.RED), BLACK(
                                                                                                    Material.BLACK_CONCRETE,
                                                                                                    NamedTextColor.BLACK);

    private final Material material;
    private final NamedTextColor textColor;

    NpcColor(Material material, NamedTextColor textColor) {
        this.material = material;
        this.textColor = textColor;
    }

    public Material material() {
        return material;
    }

    public NamedTextColor textColor() {
        return textColor;
    }

    public String displayName() {
        String[] words = name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty())
                result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public NpcColor next() {
        NpcColor[] colors = values();
        return colors[(ordinal() + 1) % colors.length];
    }

    public static NpcColor fromStored(String value) {
        if (value == null || value.isBlank())
            return ORANGE;
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ORANGE;
        }
    }
}
