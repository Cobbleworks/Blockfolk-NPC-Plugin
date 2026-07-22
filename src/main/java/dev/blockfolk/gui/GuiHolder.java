package dev.blockfolk.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

interface GuiHolder extends InventoryHolder {

    @Override
    default Inventory getInventory() {
        return null;
    }
}
