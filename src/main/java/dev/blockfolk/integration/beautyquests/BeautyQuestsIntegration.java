package dev.blockfolk.integration.beautyquests;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

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

    private final JavaPlugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final NpcBehaviourService behaviours;

    public BeautyQuestsIntegration(
            JavaPlugin plugin,
            NpcDefinitionRepository definitions,
            NpcInstanceRegistry instances,
            NpcBehaviourService behaviours
    ) {
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

        @EventHandler(priority = EventPriority.MONITOR)
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
            npcClicked(null, instance.getId().toString(), event.getPlayer(),
                    event.getPlayer().isSneaking() ? NpcClickType.SHIFT_RIGHT : NpcClickType.RIGHT);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onLeftClick(EntityDamageByEntityEvent event) {
            if (!(event.getDamager() instanceof Player player)) {
                return;
            }
            instances.findByEntityId(event.getEntity().getEntityId()).ifPresent(instance ->
                    npcClicked(event, instance.getId().toString(), player,
                            player.isSneaking() ? NpcClickType.SHIFT_LEFT : NpcClickType.LEFT));
        }
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
            return definitions.find(instance.getDefinitionKey())
                    .map(NpcDefinition::getDisplayName)
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
            return instance.getLocation();
        }

        @Override
        public boolean setNavigationPaused(boolean paused) {
            return behaviours.setExternalNavigationPaused(instance, paused);
        }
    }
}
