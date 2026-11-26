package dev.blockfolk.model;

import java.util.Locale;

import org.bukkit.Material;

/** Which of a shop's editor pages are on sale. */
public enum ShopPageMode {
    /** Every page is on sale at once. */
    ALL_PAGES("Show All Pages", "every page is on sale", Material.BOOKSHELF),
    /** One page per Minecraft day, in order, skipping empty pages. */
    ONE_PER_DAY("One Page per Day", "a new page every Minecraft day", Material.CLOCK);

    private final String displayName;
    private final String summary;
    private final Material icon;

    ShopPageMode(String displayName, String summary, Material icon) {
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

    public ShopPageMode toggled() {
        return this == ALL_PAGES ? ONE_PER_DAY : ALL_PAGES;
    }

    public static ShopPageMode fromStored(String value) {
        if (value == null)
            return ALL_PAGES;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return ALL_PAGES;
        }
    }
}
