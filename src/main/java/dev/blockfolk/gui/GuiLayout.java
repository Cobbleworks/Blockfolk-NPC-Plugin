package dev.blockfolk.gui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.blockfolk.util.LegacyText;

final class GuiLayout {

    private GuiLayout() {
    }

    static void fillMainBar(Inventory inventory) {
        int firstSlot = inventory.getSize() - 9;
        for (int slot = firstSlot; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler());
            }
        }
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyText.component(LegacyText.BLACK + " "));
        item.setItemMeta(meta);
        return item;
    }
}
