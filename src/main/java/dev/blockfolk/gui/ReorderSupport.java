package dev.blockfolk.gui;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class ReorderSupport {

    private ReorderSupport() { }

    static void restoreCursor(Player player, ReorderState state,
            BiFunction<String, Integer, ItemStack> iconFactory) {
        if (state.selectedKey == null) return;
        int index = state.keys.indexOf(state.selectedKey);
        if (index >= 0) player.setItemOnCursor(iconFactory.apply(state.selectedKey, index));
    }

    static void clearSelection(Player player, ReorderState state, NamespacedKey markerKey) {
        state.clearSelection();
        clearCursor(player, markerKey);
    }

    static void clearCursor(HumanEntity player, NamespacedKey markerKey) {
        ItemStack cursor = player.getItemOnCursor();
        ItemMeta meta = cursor == null || cursor.getType().isAir() ? null : cursor.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)) {
            player.setItemOnCursor(null);
        }
    }

    static void selectOrMove(InventoryClickEvent event, Player player, ReorderState state,
            int pageSize, NamespacedKey markerKey, BiFunction<String, Integer, ItemStack> iconFactory,
            Consumer<Inventory> renderer) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= pageSize || state.keys.isEmpty()) return;
        int targetIndex = Math.min(state.page * pageSize + slot, state.keys.size() - 1);
        if (state.selectedKey == null) {
            int sourceIndex = state.page * pageSize + slot;
            ItemStack cursor = player.getItemOnCursor();
            if (sourceIndex >= state.keys.size() || cursor != null && !cursor.getType().isAir()) return;
            state.select(sourceIndex);
            event.getView().getTopInventory().setItem(slot, null);
            restoreCursor(player, state, iconFactory);
            return;
        }
        state.moveSelectedTo(targetIndex);
        clearSelection(player, state, markerKey);
        renderer.accept(event.getView().getTopInventory());
    }

    static class ReorderState implements GuiHolder {
        final List<String> keys;
        int page;
        String selectedKey;

        ReorderState(List<String> keys) {
            this.keys = keys;
        }

        boolean select(int index) {
            if (selectedKey != null || index < 0 || index >= keys.size()) return false;
            selectedKey = keys.get(index);
            return true;
        }

        void moveSelectedTo(int targetIndex) {
            if (selectedKey == null || keys.isEmpty()) return;
            int sourceIndex = keys.indexOf(selectedKey);
            if (sourceIndex < 0) return;
            int boundedTarget = Math.max(0, Math.min(targetIndex, keys.size() - 1));
            if (sourceIndex == boundedTarget) return;
            String moved = keys.remove(sourceIndex);
            keys.add(boundedTarget, moved);
        }

        void clearSelection() {
            selectedKey = null;
        }

    }
}
