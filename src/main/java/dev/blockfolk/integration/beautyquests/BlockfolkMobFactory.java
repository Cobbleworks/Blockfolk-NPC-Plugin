package dev.blockfolk.integration.beautyquests;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;
import fr.skytasul.quests.api.mobs.MobFactory;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Lets BeautyQuests kill stages target Blockfolk NPCs. A target is a preset
 * (definition key), so a kill counts for every instance of that preset and
 * keeps working after combat respawns, which create new instances.
 */
public final class BlockfolkMobFactory implements MobFactory<BlockfolkMobFactory.Target>, Listener {

    public static final String ID = "blockfolkNPC";

    /** A kill target, kept even if its preset was deleted so quests still load. */
    public record Target(String definitionKey) {
    }

    private final JavaPlugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final Map<UUID, Consumer<Target>> pendingSelections = new ConcurrentHashMap<>();

    public BlockfolkMobFactory(JavaPlugin plugin, NpcDefinitionRepository definitions, NpcInstanceRegistry instances) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
    }

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public ItemStack getFactoryItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LegacyText.YELLOW + "Blockfolk NPC");
        meta.setLore(List.of(LegacyText.GRAY + "Left-click a Blockfolk NPC to target its preset."));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void itemClick(Player player, Consumer<Target> run) {
        player.closeInventory();
        pendingSelections.put(player.getUniqueId(), run);
        player.sendMessage(UiText.prompt("Left-click the Blockfolk NPC to kill, or type \"cancel\"."));
    }

    @Override
    public Target fromValue(String value) {
        return value == null || value.isBlank() ? null : new Target(value);
    }

    @Override
    public String getValue(Target target) {
        return target.definitionKey();
    }

    @Override
    public String getName(Target target) {
        return definitions.find(target.definitionKey()).map(NpcDefinition::getDisplayName)
                .orElse(target.definitionKey());
    }

    @Override
    public EntityType getEntityType(Target target) {
        return EntityType.MANNEQUIN;
    }

    @Override
    public List<String> getDescriptiveLore(Target target) {
        return List.of(LegacyText.GRAY + "Preset: " + LegacyText.WHITE + target.definitionKey());
    }

    @Override
    public boolean bukkitMobApplies(Target target, Entity entity) {
        return instances.findByEntityId(entity.getEntityId())
                .map(instance -> instance.getDefinitionKey().equalsIgnoreCase(target.definitionKey())).orElse(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        instances.findByEntityId(event.getEntity().getEntityId()).map(NpcInstance::getDefinitionKey)
                .ifPresent(key -> callEvent(event, new Target(key), event.getEntity(), killer));
    }

    // Registered before the NPC click listener so a selection click is consumed
    // instead of also selecting the NPC for BeautyQuests or damaging it.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSelect(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (pendingSelections.isEmpty() || !pendingSelections.containsKey(player.getUniqueId())) {
            return;
        }
        NpcInstance instance = instances.findByEntityId(event.getAttacked().getEntityId()).orElse(null);
        if (instance == null) {
            return;
        }
        event.setCancelled(true);
        Consumer<Target> run = pendingSelections.remove(player.getUniqueId());
        if (run != null) {
            run.accept(new Target(instance.getDefinitionKey()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCancel(AsyncChatEvent event) {
        if (pendingSelections.isEmpty()) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        if (!pendingSelections.containsKey(playerId) || !"cancel"
                .equalsIgnoreCase(PlainTextComponentSerializer.plainText().serialize(event.message()).trim())) {
            return;
        }
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Consumer<Target> run = pendingSelections.remove(playerId);
            if (run != null) {
                run.accept(null);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingSelections.remove(event.getPlayer().getUniqueId());
    }
}
