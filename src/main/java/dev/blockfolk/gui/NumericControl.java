package dev.blockfolk.gui;

import java.util.List;
import org.bukkit.event.inventory.ClickType;
import dev.blockfolk.util.LegacyText;

/**
 * Shared numeric control instructions and click handling for administrator
 * menus.
 */
final class NumericControl {
    private NumericControl() {
    }

    static List<String> lore() {
        return List.of(LegacyText.YELLOW + "Left-click: " + LegacyText.GRAY + "Decrease value",
                LegacyText.YELLOW + "Right-click: " + LegacyText.GRAY + "Increase value",
                LegacyText.AQUA + "Middle-click: " + LegacyText.GRAY + "Enter exact value",
                LegacyText.DARK_GRAY + "Shift-click: " + LegacyText.GRAY + "Five steps");
    }

    static int direction(ClickType click) {
        return switch (click) {
            case LEFT, SHIFT_LEFT -> -1;
            case RIGHT, SHIFT_RIGHT -> 1;
            default -> 0;
        };
    }

}
