package dev.blockfolk.gui;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import dev.blockfolk.ai.AiActionType;
import dev.blockfolk.ai.AiControlService;
import dev.blockfolk.ai.AiControlSettings;
import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.TextUtil;
import dev.blockfolk.util.UiText;
import net.kyori.adventure.text.Component;

/** Owns the AI behaviour and long-term-memory menus. */
final class AiGuiService {

    private static final int[] ACTION_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final List<AiActionType> ACTION_TYPES = Arrays.stream(AiActionType.values())
            .filter(type -> type != AiActionType.REMEMBER_FACT && type != AiActionType.DROP_ITEM)
            .toList();

    private final NpcDefinitionRepository definitions;
    private final ChatInputService chatInput;
    private final BiConsumer<Player, NpcDefinition> back;
    private AiControlService aiControl;

    AiGuiService(NpcDefinitionRepository definitions, ChatInputService chatInput,
            BiConsumer<Player, NpcDefinition> back) {
        this.definitions = definitions;
        this.chatInput = chatInput;
        this.back = back;
    }

    void setAiControlService(AiControlService aiControl) {
        this.aiControl = aiControl;
    }

    boolean handles(InventoryHolder holder) {
        return holder instanceof AiControlHolder || holder instanceof AiMemoryHolder;
    }

    void handleClick(InventoryClickEvent event, Player player) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof AiControlHolder control) handleControlClick(event, player, control);
        else if (holder instanceof AiMemoryHolder memory) handleMemoryClick(event, player, memory);
    }

    void open(Player player, NpcDefinition definition) {
        AiControlSettings settings = definition.getAiControlSettings();
        Inventory inventory = Bukkit.createInventory(new AiControlHolder(definition.getKey()), 54,
                Component.text("AI Behaviour"));
        inventory.setItem(10, contextItem(Material.NAME_TAG, "Identity", settings.identity(),
                "Who this NPC is, its name, history, and role"));
        inventory.setItem(11, contextItem(Material.WRITABLE_BOOK, "Personality & Behaviour", settings.behaviour(),
                "How it speaks, acts, reacts, and treats others"));
        inventory.setItem(12, contextItem(Material.COMPASS, "Goal / Role", settings.goal(),
                "What it should accomplish or prioritize"));
        inventory.setItem(13, contextItem(Material.KNOWLEDGE_BOOK, "Knowledge / Information", settings.information(),
                "Facts, lore, rules, and local knowledge it may use"));
        inventory.setItem(14, contextItem(Material.CAKE, "Likes & Dislikes", settings.likesDislikes(),
                "Things it enjoys, avoids, values, or strongly dislikes"));
        inventory.setItem(15, item(settings.memoryEnabled() ? Material.ENDER_CHEST : Material.CHEST,
                "Memory: " + (settings.memoryEnabled() ? "Enabled" : "Disabled"), List.of(
                        LegacyText.GRAY + "Long-term facts: " + LegacyText.WHITE + definition.getAiMemories().size()
                                + LegacyText.GRAY + " / " + NpcDefinition.MAX_AI_MEMORIES,
                        LegacyText.GRAY + "Enabled memories provide context and let the AI remember facts",
                        LegacyText.YELLOW + "Left-click to view and edit",
                        LegacyText.YELLOW + "Right-click to " + (settings.memoryEnabled() ? "disable" : "enable"),
                        LegacyText.RED + "Shift-right-click to clear all memories")));
        inventory.setItem(20, toggleItem(Material.ENDER_EYE,
                "Conversation: " + (settings.sharedConversation() ? "Shared" : "Private"),
                settings.sharedConversation(), settings.sharedConversation()
                        ? "All players share this NPC instance's conversation"
                        : "Each player has a separate conversation with this NPC instance"));
        inventory.setItem(21, toggleItem(Material.SKELETON_SKULL, "React To Nearby Deaths",
                settings.reactToNearbyDeaths(), "Lets the NPC comment when someone dies within 12 blocks"));
        inventory.setItem(22, toggleItem(Material.CHEST, "Temporary Inventory",
                settings.inventoryEnabled(), "Lets the AI see, mine into, and drop items carried by each instance"));
        for (int index = 0; index < ACTION_TYPES.size(); index++) {
            AiActionType type = ACTION_TYPES.get(index);
            boolean chatToggle = type == AiActionType.SAY;
            boolean intrinsic = type == AiActionType.DO_NOTHING;
            boolean enabled = chatToggle ? settings.respondToChat()
                    : intrinsic || settings.allowedActions().contains(type);
            String displayName = chatToggle ? "Respond to Nearby Chat" : type.displayName();
            inventory.setItem(ACTION_SLOTS[index], item(enabled ? Material.REDSTONE_TORCH : Material.LEVER,
                    displayName + ": " + (enabled ? "Enabled" : "Disabled"), List.of(
                            chatToggle ? LegacyText.GRAY + "Reads and answers player chat within 8 blocks"
                                    : LegacyText.GRAY + "Available when this capability is enabled",
                            intrinsic ? LegacyText.DARK_GRAY + "Always available"
                                    : LegacyText.YELLOW + "Click to toggle")));
        }
        inventory.setItem(45, item(Material.ARROW, "Back", List.of()));
        boolean hasTrigger = hasTrigger(definition) || settings.respondToChat() || settings.reactToNearbyDeaths();
        String status = !settings.enabled() ? "Paused" : hasTrigger ? "Active" : "No Triggers";
        Material statusMaterial = !settings.enabled() ? Material.RED_DYE
                : hasTrigger ? Material.LIME_DYE : Material.YELLOW_DYE;
        inventory.setItem(49, item(statusMaterial, "AI Behaviour: " + status, List.of(
                LegacyText.GRAY + "Applies to every spawned instance of this preset",
                hasTrigger ? LegacyText.GRAY + "Automatic triggers are configured"
                        : LegacyText.RED + "No requests are made and nearby chat is not read",
                LegacyText.YELLOW + "Click to " + (settings.enabled() ? "pause" : "resume"))));
        openInventory(player, inventory);
    }

    boolean hasTrigger(NpcDefinition definition) {
        for (BehaviourEvent event : BehaviourEvent.values()) {
            if (containsTrigger(definition.getBehaviourActions(event))) return true;
        }
        for (String eventName : definition.getCustomEventNames()) {
            if (containsTrigger(definition.getCustomEventActions(eventName))) return true;
        }
        return false;
    }

    String providerStatusLore() {
        return aiControl != null && aiControl.configured()
                ? LegacyText.GREEN + "OpenRouter is ready"
                : LegacyText.RED + "OpenRouter: " + providerConfigurationIssue();
    }

    private boolean containsTrigger(List<BehaviourAction> actions) {
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.AI_TRIGGER) return true;
            if (action.type() != BehaviourActionType.ASK_QUESTION || action.question() == null) continue;
            for (var option : action.question().options()) {
                if (containsTrigger(option.actions())) return true;
            }
            if (containsTrigger(action.question().cancelActions())) return true;
        }
        return false;
    }

    private void openMemories(Player player, NpcDefinition definition) {
        Inventory inventory = Bukkit.createInventory(new AiMemoryHolder(definition.getKey()), 54,
                UiText.title("Memory", definition.getDisplayName()));
        List<String> memories = definition.getAiMemories();
        for (int index = 0; index < memories.size(); index++) {
            inventory.setItem(index, item(Material.PAPER, "Memory " + (index + 1), List.of(
                    LegacyText.WHITE + TextUtil.abbreviateSingleLine(memories.get(index), 96),
                    LegacyText.YELLOW + "Left-click to edit",
                    LegacyText.RED + "Right-click to delete")));
        }
        inventory.setItem(45, item(Material.ARROW, "Back", List.of()));
        inventory.setItem(49, item(Material.LIME_DYE, "Add Memory", List.of(
                LegacyText.GRAY + "The oldest memory is discarded when all 45 slots are full",
                LegacyText.YELLOW + "Click to add a fact")));
        openInventory(player, inventory);
    }

    private void handleControlClick(InventoryClickEvent event, Player player, AiControlHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) return;
        NpcDefinition definition = definitions.find(holder.key()).orElse(null);
        if (definition == null) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot >= 10 && slot <= 14) { requestContext(player, definition, slot); return; }
        if (slot == 15) {
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                definition.clearAiMemories();
                definitions.save(definition);
                player.sendMessage(UiText.info("Cleared all memories for " + definition.getDisplayName() + "."));
                open(player, definition);
            } else if (event.isRightClick()) {
                AiControlSettings settings = definition.getAiControlSettings();
                definition.setAiControlSettings(settings.withMemoryEnabled(!settings.memoryEnabled()));
                definitions.save(definition);
                open(player, definition);
            } else openMemories(player, definition);
            return;
        }
        if (slot == 20) {
            AiControlSettings settings = definition.getAiControlSettings();
            definition.setAiControlSettings(settings.withSharedConversation(!settings.sharedConversation()));
        } else if (slot == 21) {
            AiControlSettings settings = definition.getAiControlSettings();
            definition.setAiControlSettings(settings.withReactToNearbyDeaths(!settings.reactToNearbyDeaths()));
        } else if (slot == 22) {
            AiControlSettings settings = definition.getAiControlSettings();
            definition.setAiControlSettings(settings.withInventoryEnabled(!settings.inventoryEnabled()));
        } else if (slot == 45) {
            back.accept(player, definition);
            return;
        } else if (slot == 49) {
            AiControlSettings settings = definition.getAiControlSettings();
            if (!settings.enabled() && !settings.hasContext()) {
                player.sendMessage(Component.text("Configure at least one AI context section before activating AI behaviour."));
                return;
            }
            definition.setAiControlSettings(settings.withEnabled(!settings.enabled()));
            definitions.save(definition);
            if (!settings.enabled() && aiControl != null && !aiControl.configured()) {
                player.sendMessage(Component.text("AI behaviour is active, but OpenRouter "
                        + aiControl.configurationIssue() + ". Check config.yml and restart the server."));
            }
            open(player, definition);
            return;
        } else {
            for (int index = 0; index < ACTION_TYPES.size(); index++) {
                if (slot != ACTION_SLOTS[index]) continue;
                AiActionType type = ACTION_TYPES.get(index);
                if (type == AiActionType.SAY) {
                    definition.setAiControlSettings(definition.getAiControlSettings().withRespondToChat(
                            !definition.getAiControlSettings().respondToChat()));
                } else if (type != AiActionType.DO_NOTHING) {
                    definition.setAiControlSettings(definition.getAiControlSettings().toggle(type));
                }
                definitions.save(definition);
                open(player, definition);
                return;
            }
            return;
        }
        definitions.save(definition);
        open(player, definition);
    }

    private void handleMemoryClick(InventoryClickEvent event, Player player, AiMemoryHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) return;
        NpcDefinition definition = definitions.find(holder.key()).orElse(null);
        if (definition == null) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot == 45) { open(player, definition); return; }
        if (slot == 49) { requestMemory(player, definition, -1); return; }
        if (slot < 0 || slot >= definition.getAiMemories().size()) return;
        if (event.isRightClick()) {
            definition.removeAiMemory(slot);
            definitions.save(definition);
            openMemories(player, definition);
        } else requestMemory(player, definition, slot);
    }

    private void requestMemory(Player player, NpcDefinition definition, int index) {
        String prompt = index < 0 ? "Enter a fact for the NPC to remember:"
                : "Edit this memory, or enter 'clear' to delete it:";
        chatInput.request(player, prompt, value -> {
            if (index < 0) definition.addAiMemory(value);
            else definition.setAiMemory(index, value.equalsIgnoreCase("clear") ? "" : value);
            definitions.save(definition);
            openMemories(player, definition);
        });
    }

    private void requestContext(Player player, NpcDefinition definition, int slot) {
        String section = switch (slot) {
            case 10 -> "identity";
            case 11 -> "personality and behaviour";
            case 12 -> "goal or role";
            case 13 -> "knowledge and information";
            case 14 -> "likes and dislikes";
            default -> throw new IllegalArgumentException("Unknown AI context slot: " + slot);
        };
        chatInput.request(player, "Enter the NPC's " + section + ", or 'clear':", value -> {
            String normalized = value.equalsIgnoreCase("clear") ? "" : value;
            AiControlSettings current = definition.getAiControlSettings();
            definition.setAiControlSettings(switch (slot) {
                case 10 -> current.withIdentity(normalized);
                case 11 -> current.withBehaviour(normalized);
                case 12 -> current.withGoal(normalized);
                case 13 -> current.withInformation(normalized);
                case 14 -> current.withLikesDislikes(normalized);
                default -> current;
            });
            definitions.save(definition);
            open(player, definition);
        });
    }

    private ItemStack contextItem(Material material, String name, String value, String description) {
        return item(material, name, List.of(
                LegacyText.GRAY + description,
                value.isBlank() ? LegacyText.DARK_GRAY + "Not configured"
                        : LegacyText.WHITE + TextUtil.abbreviateSingleLine(value, 48),
                LegacyText.YELLOW + "Click to edit; enter 'clear' to remove"));
    }

    private ItemStack toggleItem(Material material, String name, boolean enabled, String description) {
        return item(material, name, List.of(LegacyText.GRAY + description,
                enabled ? LegacyText.GREEN + "On" : LegacyText.RED + "Off",
                LegacyText.YELLOW + "Click to toggle"));
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyText.component(LegacyText.GOLD + name));
        meta.lore(LegacyText.components(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void openInventory(Player player, Inventory inventory) {
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private boolean isTopInventoryClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        return slot >= 0 && slot < event.getView().getTopInventory().getSize();
    }

    private String providerConfigurationIssue() {
        return aiControl == null ? "service unavailable" : aiControl.configurationIssue();
    }

    private record AiControlHolder(String key) implements GuiHolder { }

    private record AiMemoryHolder(String key) implements GuiHolder { }
}
