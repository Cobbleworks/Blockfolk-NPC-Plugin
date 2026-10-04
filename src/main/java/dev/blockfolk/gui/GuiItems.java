package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.blockfolk.util.LegacyText;

/**
 * Shared visual language for menu items: plain buttons, on/off toggles, option
 * cycles, navigation, and confirmation buttons.
 *
 * <p>
 * Toggles and cycles show their state in the item name and list every option in
 * the tooltip, marking the current one:
 *
 * <pre>
 * Aggression: Hunting
 *    Ignore: does not react
 *  ▶ Hunting: hunts enabled targets
 *
 * Description...
 *
 * Left-click: next · Right-click: previous
 * </pre>
 */
final class GuiItems {

    static final String MARKER = "▶ ";
    static final String UNMARKED = "   ";
    /** Standard footer slots of 54-slot menus. */
    static final int PREVIOUS_SLOT = 45;
    static final int BACK_SLOT = 49;
    static final int CREATE_SLOT = 51;
    static final int NEXT_SLOT = 53;
    /** Largest option list shown in full; longer cycles show a window. */
    private static final int MAX_LISTED_OPTIONS = 7;

    private GuiItems() {
    }

    static ItemStack item(Material material, String name, List<String> lore) {
        return item(new ItemStack(material), LegacyText.GOLD + name, lore);
    }

    /** Copies {@code template} and names it; {@code name} carries its own color. */
    static ItemStack item(ItemStack template, String name, List<String> lore) {
        ItemStack result = template.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(LegacyText.component(name));
        meta.lore(LegacyText.components(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        result.setItemMeta(meta);
        return result;
    }

    static ItemStack glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    /** {@code Left-click: open} in the standard hint colors. */
    static String hint(String click, String text) {
        return LegacyText.YELLOW + click + ": " + LegacyText.GRAY + text;
    }

    /** A hint for a destructive click. */
    static String danger(String click, String text) {
        return LegacyText.RED + click + ": " + LegacyText.GRAY + text;
    }

    /**
     * An on/off switch. The item changes between {@code onIcon} and
     * {@code offIcon}, the name shows the state, and the tooltip explains both
     * states.
     */
    static ItemStack toggle(Material onIcon, Material offIcon, String label, boolean enabled, String onText,
            String offText, List<String> description) {
        List<String> lore = new ArrayList<>();
        lore.add(optionLine("On", onText, enabled));
        lore.add(optionLine("Off", offText, !enabled));
        appendDescription(lore, description);
        lore.add("");
        lore.add(LegacyText.YELLOW + "Click: " + (enabled ? "turn off" : "turn on"));
        ItemStack item = item(new ItemStack(enabled ? onIcon : offIcon),
                (enabled ? LegacyText.GREEN : LegacyText.RED) + label + ": " + (enabled ? "On" : "Off"), lore);
        return enabled ? glint(item) : item;
    }

    /**
     * A pick-one setting switched by clicking through its options. The item takes
     * the icon of the selected option.
     */
    static ItemStack cycle(String label, List<Option> options, int selected, List<String> description) {
        Option current = options.get(selected);
        List<String> lore = new ArrayList<>();
        int from = 0;
        int to = options.size();
        if (options.size() > MAX_LISTED_OPTIONS) {
            from = Math.clamp(selected - MAX_LISTED_OPTIONS / 2, 0, options.size() - MAX_LISTED_OPTIONS);
            to = from + MAX_LISTED_OPTIONS;
            if (from > 0)
                lore.add(LegacyText.DARK_GRAY + UNMARKED + "…");
        }
        for (int index = from; index < to; index++) {
            Option option = options.get(index);
            lore.add(optionLine(option.name(), option.summary(), index == selected));
        }
        if (to < options.size())
            lore.add(LegacyText.DARK_GRAY + UNMARKED + "…");
        appendDescription(lore, description);
        lore.add("");
        lore.add(LegacyText.YELLOW + "Left-click: next · Right-click: previous");
        return item(new ItemStack(current.icon()), current.color() + label + ": " + current.name(), lore);
    }

    /** +1 for left clicks, -1 for right clicks, 0 otherwise. */
    static int cycleDirection(ClickType click) {
        return switch (click) {
            case LEFT, SHIFT_LEFT -> 1;
            case RIGHT, SHIFT_RIGHT -> -1;
            default -> 0;
        };
    }

    /** The value {@code direction} steps away from {@code current}, wrapping. */
    static <T extends Enum<T>> T step(T current, int direction) {
        T[] values = current.getDeclaringClass().getEnumConstants();
        return values[Math.floorMod(current.ordinal() + direction, values.length)];
    }

    static ItemStack back(String destination) {
        return item(Material.BARRIER, "Back",
                destination == null || destination.isBlank()
                        ? List.of()
                        : List.of(LegacyText.GRAY + "Return to " + destination));
    }

    static ItemStack previousPage(int page, int pages) {
        return item(Material.ARROW, "Previous Page", List.of(LegacyText.GRAY + "Page " + page + " of " + pages));
    }

    static ItemStack nextPage(int page, int pages) {
        return item(Material.ARROW, "Next Page", List.of(LegacyText.GRAY + "Page " + (page + 2) + " of " + pages));
    }

    static ItemStack create(String name, String... lore) {
        List<String> lines = new ArrayList<>();
        for (String line : lore)
            lines.add(LegacyText.GRAY + line);
        lines.add(LegacyText.YELLOW + "Click to create");
        return item(Material.EMERALD, name, lines);
    }

    static ItemStack empty(String name, String explanation) {
        return item(Material.GRAY_DYE, name, List.of(LegacyText.GRAY + explanation));
    }

    static ItemStack confirm(List<String> consequences) {
        List<String> lore = new ArrayList<>();
        consequences.forEach(line -> lore.add(LegacyText.RED + line));
        lore.add("");
        lore.add(LegacyText.YELLOW + "Click to confirm");
        return item(new ItemStack(Material.LIME_CONCRETE), LegacyText.GREEN + "Confirm", lore);
    }

    static ItemStack cancel() {
        return item(new ItemStack(Material.RED_CONCRETE), LegacyText.RED + "Cancel",
                List.of(LegacyText.GRAY + "Nothing will be changed"));
    }

    /** Short audible feedback after a setting changes. */
    static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.6f);
    }

    private static String optionLine(String name, String summary, boolean selected) {
        String text = name + (summary == null || summary.isBlank() ? "" : ": " + summary);
        return selected ? LegacyText.WHITE + MARKER + text : LegacyText.DARK_GRAY + UNMARKED + text;
    }

    private static void appendDescription(List<String> lore, List<String> description) {
        if (description.isEmpty())
            return;
        lore.add("");
        description.forEach(line -> lore.add(line.startsWith("§") ? line : LegacyText.GRAY + line));
    }

    /** One choice of a {@link #cycle}; {@code color} is a legacy color code. */
    record Option(Material icon, String name, String summary, String color) {
        Option(Material icon, String name, String summary) {
            this(icon, name, summary, LegacyText.GOLD);
        }
    }
}
