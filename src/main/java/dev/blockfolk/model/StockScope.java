package dev.blockfolk.model;

import java.util.Locale;

import org.bukkit.Material;

/** Who shares a limited trade's stock. */
public enum StockScope {
    /** One stock for all players: whoever buys first gets it. */
    SHARED("Shared", "one stock for all players", Material.CHEST),
    /** Every player can buy the limit for themselves. */
    PER_PLAYER("Per Player", "each player buys up to the limit", Material.PLAYER_HEAD);

    private final String displayName;
    private final String summary;
    private final Material icon;

    StockScope(String displayName, String summary, Material icon) {
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

    public static StockScope fromStored(String value) {
        if (value == null)
            return SHARED;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SHARED;
        }
    }
}
