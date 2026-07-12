package dev.blockfolk.model;

import java.util.Objects;
import java.util.regex.Pattern;

import org.bukkit.inventory.ItemStack;

public final class CustomEvent {
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*");
    private final String name;
    private String description = "";
    private ItemStack icon;

    public CustomEvent(String name) {
        String normalized = Objects.requireNonNull(name, "name").trim();
        if (!NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Event names may contain letters, numbers, _ and -, with / between groups");
        }
        this.name = normalized;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description.trim(); }
    public ItemStack getIcon() { return icon == null ? null : icon.clone(); }
    public void setIcon(ItemStack icon) {
        this.icon = icon == null || icon.getType().isAir() ? null : icon.clone();
        if (this.icon != null) this.icon.setAmount(1);
    }
}
