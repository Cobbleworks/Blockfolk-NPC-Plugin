package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.Scene;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.SceneRepository;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.runtime.SceneService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class SceneGuiServiceTest {

    @Test
    void failedStartShowsTheRespawnReasonOnTheButtonInTheOpenMenu() {
        Scene scene = new Scene("train");
        scene.addRole("luigi");
        NpcDefinition luigi = new NpcDefinition("luigi");
        luigi.setDisplayName("Luigi");
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), "luigi", new Location(null, 0, 64, 0));
        instance.setRespawnAtEpochMillis(1);
        SceneRepository scenes = mock(SceneRepository.class);
        when(scenes.find("train")).thenReturn(Optional.of(scene));
        NpcDefinitionRepository definitions = mock(NpcDefinitionRepository.class);
        when(definitions.find("luigi")).thenReturn(Optional.of(luigi));
        NpcInstanceRegistry instances = mock(NpcInstanceRegistry.class);
        when(instances.findAll()).thenReturn(List.of(instance));
        SceneGuiService gui = new SceneGuiService(null, scenes, definitions, null, null, null);
        gui.setSceneService(new SceneService(null, scenes, definitions, instances, null, false));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        List<Inventory> menus = new ArrayList<>();
        try (var bukkit = mockStatic(Bukkit.class);
                var itemStacks = mockConstruction(ItemStack.class, (stack, context) -> {
                    when(stack.clone()).thenReturn(stack);
                    when(stack.getItemMeta()).thenReturn(mock(ItemMeta.class));
                })) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(54), any(Component.class)))
                    .thenAnswer(call -> {
                        Inventory menu = mock(Inventory.class);
                        when(menu.getHolder()).thenReturn(call.getArgument(0));
                        when(menu.getSize()).thenReturn(54);
                        menus.add(menu);
                        return menu;
                    });
            gui.openScript(player, "train");
            click(gui, player, menus.getLast(), 0);
            Inventory settings = menus.getLast();

            click(gui, player, settings, 25);

            verify(player, times(2)).openInventory(any(Inventory.class));
            ArgumentCaptor<ItemStack> buttons = ArgumentCaptor.forClass(ItemStack.class);
            verify(settings, times(2)).setItem(eq(25), buttons.capture());
            ArgumentCaptor<List<Component>> lore = ArgumentCaptor.captor();
            verify(buttons.getValue().getItemMeta()).lore(lore.capture());
            String tooltip = lore.getValue().stream().map(PlainTextComponentSerializer.plainText()::serialize)
                    .collect(Collectors.joining(" "));
            assertTrue(tooltip.contains("no Luigi is loaded (waiting for respawn)"), tooltip);
        }
    }

    private static void click(SceneGuiService gui, Player player, Inventory inventory, int slot) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(view);
        when(click.getRawSlot()).thenReturn(slot);
        gui.onClick(click);
    }
}
