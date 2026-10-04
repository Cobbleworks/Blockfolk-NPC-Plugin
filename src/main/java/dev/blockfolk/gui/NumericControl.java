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
        return List.of("", LegacyText.YELLOW + "Left-click: decrease · Right-click: increase",
                LegacyText.YELLOW + "Shift-click: five steps · Middle-click: type a value");
    }

    static int direction(ClickType click) {
        return switch (click) {
            case LEFT, SHIFT_LEFT -> -1;
            case RIGHT, SHIFT_RIGHT -> 1;
            default -> 0;
        };
    }

}
