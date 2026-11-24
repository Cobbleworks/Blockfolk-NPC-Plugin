package dev.blockfolk.model;

import java.util.Locale;

import org.bukkit.Material;

/** Whether players see a trade, and whether it takes part in the rotation. */
public enum OfferAvailability {
    /** Sold; drawn into the assortment when the shop rotates. */
    OFFERED("Offered", "sold; drawn when the shop rotates", Material.LIME_DYE),
    /** Sold in every assortment, never drawn. */
    ALWAYS("Always Offered", "sold in every assortment", Material.LIGHT_BLUE_DYE),
    /** Kept in the editor but never sold. */
    HIDDEN("Not Offered", "kept here, never sold", Material.GRAY_DYE);

    private final String displayName;
    private final String summary;
    private final Material icon;

    OfferAvailability(String displayName, String summary, Material icon) {
        this.displayName = displayName;
        this.summary = summary;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }

    public Material icon() {
        return icon;
    }

    public static OfferAvailability fromStored(String value) {
        if (value == null)
            return OFFERED;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OFFERED;
        }
    }
}
