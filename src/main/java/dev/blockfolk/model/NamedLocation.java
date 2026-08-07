package dev.blockfolk.model;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

/** A globally named position that can be selected by behaviour actions. */
public record NamedLocation(String key, String displayName, ActionLocation location, ItemStack icon) {

    public NamedLocation {
        key = normalizeKey(key);
        displayName = Objects.requireNonNull(displayName, "location name").trim();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Location name is required.");
        }
        Objects.requireNonNull(location, "location");
        icon = icon == null || icon.getType().isAir() ? null : icon.clone();
        if (icon != null)
            icon.setAmount(1);
    }

    public NamedLocation(String key, String displayName, ActionLocation location) {
        this(key, displayName, location, null);
    }

    public static NamedLocation create(String displayName, ActionLocation location) {
        return new NamedLocation(normalizeKey(displayName), displayName, location);
    }

    @Override
    public ItemStack icon() {
        return icon == null ? null : icon.clone();
    }

    public NamedLocation withIcon(ItemStack icon) {
        return new NamedLocation(key, displayName, location, icon);
    }

    public static String normalizeKey(String value) {
        String name = Objects.requireNonNull(value, "location name").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Location name is required.");
        }
        return NpcDefinition.toKey(name);
    }
}
