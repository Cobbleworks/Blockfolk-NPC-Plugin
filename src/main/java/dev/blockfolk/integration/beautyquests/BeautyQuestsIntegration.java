package dev.blockfolk.integration.beautyquests;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.gui.NpcHeadUtil;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.runtime.NpcBehaviourService;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import fr.skytasul.quests.api.QuestsAPI;
import fr.skytasul.quests.api.npcs.BqInternalNpc;
import fr.skytasul.quests.api.npcs.BqInternalNpcFactory;
import fr.skytasul.quests.api.npcs.NpcClickType;

/** Exposes persistent Blockfolk instances as native BeautyQuests NPCs. */
public final class BeautyQuestsIntegration implements BqInternalNpcFactory {

    private static final String FACTORY_KEY = "blockfolk";
    private static final Pattern NPC_ID_PATTERN = Pattern
            .compile("blockfolk#([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private final JavaPlugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final NpcBehaviourService behaviours;

    public BeautyQuestsIntegration(JavaPlugin plugin, NpcDefinitionRepository definitions,
            NpcInstanceRegistry instances, NpcBehaviourService behaviours) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
        this.behaviours = behaviours;
    }

    public void register() {
        QuestsAPI.getAPI().addNpcFactory(FACTORY_KEY, this);
        plugin.getServer().getPluginManager().registerEvents(new ClickListener(), plugin);
        plugin.getLogger().info("BeautyQuests integration enabled.");
    }

    @Override
    public int getTimeToWaitForNPCs() {
        return 0;
    }

    @Override
    public boolean isNPC(Entity entity) {
        return instances.findByEntityId(entity.getEntityId()).isPresent();
    }

    @Override
    public Collection<String> getIDs() {
        return instances.findAll().stream().map(instance -> instance.getId().toString()).toList();
    }

    @Override
    public BqInternalNpc fetchNPC(String id) {
        try {
            return instances.findById(UUID.fromString(id)).map(BlockfolkNpc::new).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void npcRemoved(NpcInstance instance) {
        npcRemoved(instance.getId().toString());
    }

    private final class ClickListener implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onRightClick(PlayerInteractEntityEvent event) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            NpcInstance instance = instances.findByEntityId(event.getRightClicked().getEntityId()).orElse(null);
            if (instance == null) {
                return;
            }
            // Shift-right-click is reserved for Blockfolk's admin editor.
            if (event.getPlayer().isSneaking() && event.getPlayer().hasPermission("blockfolk.admin")) {
                return;
            }
            npcClicked(event, instance.getId().toString(), event.getPlayer(),
                    event.getPlayer().isSneaking() ? NpcClickType.SHIFT_RIGHT : NpcClickType.RIGHT);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onLeftClick(EntityDamageByEntityEvent event) {
            if (!(event.getDamager() instanceof Player player)) {
                return;
            }
            instances.findByEntityId(event.getEntity().getEntityId())
                    .ifPresent(instance -> npcClicked(event, instance.getId().toString(), player,
                            player.isSneaking() ? NpcClickType.SHIFT_LEFT : NpcClickType.LEFT));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onInventoryOpen(InventoryOpenEvent event) {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }
            Inventory openedInventory = event.getInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                var openedGui = QuestsAPI.getAPI().getPlugin().getGuiManager().getOpenedGui(player);
                if (openedGui != null && openedGui.getInventory() == openedInventory) {
                    applyNpcIcons(openedInventory);
                }
            });
        }
    }

    private void applyNpcIcons(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            UUID instanceId = referencedInstanceId(current);
            if (instanceId == null) {
                continue;
            }
            NpcInstance instance = instances.findById(instanceId).orElse(null);
            NpcDefinition definition = instance == null
                    ? null
                    : definitions.find(instance.getDefinitionKey()).orElse(null);
            if (definition != null) {
                inventory.setItem(slot, createNpcIcon(current, definition));
            }
        }
    }

    private UUID referencedInstanceId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return referencedInstanceId(meta.getLore());
    }

    static UUID referencedInstanceId(List<String> lore) {
        if (lore == null) {
            return null;
        }
        for (String line : lore) {
            Matcher matcher = NPC_ID_PATTERN.matcher(line);
            if (matcher.find()) {
                return UUID.fromString(matcher.group(1));
            }
        }
        return null;
    }

    private ItemStack createNpcIcon(ItemStack source, NpcDefinition definition) {
        ItemMeta sourceMeta = source.getItemMeta();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, source.getAmount());
        if (head.getItemMeta() instanceof SkullMeta headMeta) {
            headMeta.displayName(sourceMeta.displayName());
            headMeta.lore(sourceMeta.lore());
            headMeta.addItemFlags(sourceMeta.getItemFlags().toArray(org.bukkit.inventory.ItemFlag[]::new));
            head.setItemMeta(headMeta);
        }
        return NpcHeadUtil.applySkin(head, definition);
    }

    private final class BlockfolkNpc implements BqInternalNpc {

        private final NpcInstance instance;

        private BlockfolkNpc(NpcInstance instance) {
            this.instance = instance;
        }

        @Override
        public String getInternalId() {
            return instance.getId().toString();
        }

        @Override
        public String getName() {
            return definitions.find(instance.getDefinitionKey()).map(NpcDefinition::getDisplayName)
                    .orElse(instance.getDefinitionKey());
        }

        @Override
        public boolean isSpawned() {
            return instances.findEntity(instance).isPresent();
        }

        @Override
        public Entity getEntity() {
            return instances.findEntity(instance).orElse(null);
        }

        @Override
        public Location getLocation() {
            return instances.currentLocation(instance);
        }

        @Override
        public boolean setNavigationPaused(boolean paused) {
            return behaviours.setExternalNavigationPaused(instance, paused);
        }
    }
}
