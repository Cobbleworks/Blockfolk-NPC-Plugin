package dev.blockfolk.gui;

import java.util.ArrayList;
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
import org.bukkit.plugin.Plugin;

import dev.blockfolk.ai.AiActionType;
import dev.blockfolk.ai.AiControlService;
import dev.blockfolk.ai.AiControlSettings;
import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.AiMemory;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.StoredLocation;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.TextUtil;
import dev.blockfolk.util.UiText;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

/** Owns the AI behaviour and long-term-memory menus. */
final class AiGuiService {

    private static final String CONTEXT_INPUT_KEY = "context";
    private static final int CONTEXT_MAX_LENGTH = 8192;
    private static final int IDENTITY_SLOT = 2;
    private static final int BEHAVIOUR_SLOT = 3;
    private static final int GOAL_SLOT = 4;
    private static final int INFORMATION_SLOT = 5;
    private static final int LIKES_DISLIKES_SLOT = 6;
    private static final int STATUS_SLOT = 11;
    private static final int CONVERSATION_SLOT = 13;
    private static final int MEMORY_SLOT = 15;
    private static final int ACTIONS_HEADER_SLOT = 22;
    private static final int BACK_SLOT = 49;
    private static final int ADD_MEMORY_SLOT = 51;
    private static final int[] ACTION_SLOTS = {28, 29, 30, 31, 32, 33, 34, 38, 39, 40, 41, 42};
    private static final List<AiActionType> ACTION_TYPES = Arrays.stream(AiActionType.values())
            .filter(type -> type != AiActionType.REMEMBER_FACT && type != AiActionType.DROP_ITEM
                    && type != AiActionType.STOP_COMBAT && type != AiActionType.UNFOLLOW
                    && type != AiActionType.PAUSE_ROUTE)
            .toList();

    private final NpcDefinitionRepository definitions;
    private final Plugin plugin;
    private final ChatInputService chatInput;
    private final BiConsumer<Player, NpcDefinition> back;
    private AiControlService aiControl;

    AiGuiService(Plugin plugin, NpcDefinitionRepository definitions, ChatInputService chatInput,
            BiConsumer<Player, NpcDefinition> back) {
        this.plugin = plugin;
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
        if (holder instanceof AiControlHolder control)
            handleControlClick(event, player, control);
        else if (holder instanceof AiMemoryHolder memory)
            handleMemoryClick(event, player, memory);
    }

    void open(Player player, NpcDefinition definition) {
        AiControlSettings settings = definition.getAiControlSettings();
        Inventory inventory = Bukkit.createInventory(new AiControlHolder(definition.getKey()), 54,
                UiText.title("AI Behaviour", definition.getDisplayName()));
        inventory.setItem(IDENTITY_SLOT, contextItem(Material.NAME_TAG, "Identity", settings.identity(),
                "Who this NPC is, its name, history, and role"));
        inventory.setItem(BEHAVIOUR_SLOT, contextItem(Material.WRITABLE_BOOK, "Personality & Behaviour",
                settings.behaviour(), "How it speaks, acts, reacts, and treats others"));
        inventory.setItem(GOAL_SLOT, contextItem(Material.COMPASS, "Goal / Role", settings.goal(),
                "What it should accomplish or prioritize"));
        inventory.setItem(INFORMATION_SLOT, contextItem(Material.KNOWLEDGE_BOOK, "Knowledge / Information",
                settings.information(), "Facts, lore, rules, and local knowledge it may use"));
        inventory.setItem(LIKES_DISLIKES_SLOT, contextItem(Material.CAKE, "Likes & Dislikes", settings.likesDislikes(),
                "Things it enjoys, avoids, values, or strongly dislikes"));
        List<String> memoryLore = new ArrayList<>();
        memoryLore.add(LegacyText.WHITE + (settings.memoryEnabled() ? GuiItems.MARKER : GuiItems.UNMARKED)
                + "On: remembers facts between conversations");
        memoryLore.add((settings.memoryEnabled()
                ? LegacyText.DARK_GRAY + GuiItems.UNMARKED
                : LegacyText.WHITE + GuiItems.MARKER) + "Off: forgets after each conversation");
        memoryLore.add("");
        memoryLore.add(LegacyText.GRAY + "Long-term facts: " + LegacyText.WHITE + definition.getAiMemories().size()
                + LegacyText.GRAY + " / " + NpcDefinition.MAX_AI_MEMORIES);
        memoryLore.add("");
        memoryLore.add(GuiItems.hint("Left-click", "view and edit memories"));
        memoryLore.add(GuiItems.hint("Right-click", settings.memoryEnabled() ? "turn off" : "turn on"));
        memoryLore.add(GuiItems.danger("Shift-right-click", "forget all memories"));
        ItemStack memory = GuiItems.item(
                new ItemStack(settings.memoryEnabled() ? Material.WRITTEN_BOOK : Material.BOOK),
                (settings.memoryEnabled() ? LegacyText.GREEN + "Memory: On" : LegacyText.RED + "Memory: Off"),
                memoryLore);
        inventory.setItem(MEMORY_SLOT, settings.memoryEnabled() ? GuiItems.glint(memory) : memory);
        inventory.setItem(CONVERSATION_SLOT, GuiItems.cycle("Conversation", List.of(
                new GuiItems.Option(Material.BELL, "Shared", "all players share one conversation", LegacyText.AQUA),
                new GuiItems.Option(Material.ENDER_CHEST, "Private", "each player talks separately",
                        LegacyText.LIGHT_PURPLE)),
                settings.sharedConversation() ? 0 : 1, List.of("Per spawned instance of this NPC")));
        inventory.setItem(ACTIONS_HEADER_SLOT,
                item(Material.KNOWLEDGE_BOOK, "Allowed AI Actions",
                        List.of(LegacyText.GRAY + "What the AI may do when it responds",
                                LegacyText.GRAY + "Toggle each capability below")));
        for (int index = 0; index < ACTION_TYPES.size(); index++) {
            AiActionType type = ACTION_TYPES.get(index);
            if (type == AiActionType.DO_NOTHING) {
                inventory.setItem(ACTION_SLOTS[index], item(capabilityIcon(type), capabilityName(type), List
                        .of(LegacyText.GRAY + capabilityDescription(type), LegacyText.DARK_GRAY + "Always available")));
                continue;
            }
            boolean chatToggle = type == AiActionType.SAY;
            boolean enabled = chatToggle ? settings.respondToChat() : settings.allowedActions().contains(type);
            inventory.setItem(ACTION_SLOTS[index],
                    GuiItems.toggle(capabilityIcon(type), Material.GRAY_DYE,
                            chatToggle ? "Respond to Nearby Chat" : capabilityName(type), enabled,
                            chatToggle ? "answers player chat within 8 blocks" : "the AI may use it",
                            chatToggle ? "only speaks when triggered" : "never used",
                            chatToggle ? List.of() : List.of(capabilityDescription(type))));
        }
        inventory.setItem(BACK_SLOT, GuiItems.back("the NPC menu"));
        boolean hasTrigger = hasTrigger(definition) || settings.respondToChat();
        String status = !settings.enabled() ? "Paused" : hasTrigger ? "Active" : "No Triggers";
        String statusColor = !settings.enabled() ? LegacyText.RED : hasTrigger ? LegacyText.GREEN : LegacyText.GOLD;
        Material statusMaterial = !settings.enabled()
                ? Material.RED_DYE
                : hasTrigger ? Material.LIME_DYE : Material.YELLOW_DYE;
        List<String> statusLore = new ArrayList<>(List.of(
                (settings.enabled() ? LegacyText.WHITE + GuiItems.MARKER : LegacyText.DARK_GRAY + GuiItems.UNMARKED)
                        + "Active: responds to triggers and chat",
                (settings.enabled() ? LegacyText.DARK_GRAY + GuiItems.UNMARKED : LegacyText.WHITE + GuiItems.MARKER)
                        + "Paused: makes no AI requests",
                "", LegacyText.GRAY + "Applies to every spawned instance"));
        if (settings.enabled() && !hasTrigger)
            statusLore.add(LegacyText.GOLD + "Add an AI Trigger action or enable chat");
        if (aiControl != null && !aiControl.configured())
            statusLore.add(LegacyText.RED + "OpenRouter is not ready: " + aiControl.configurationIssue());
        statusLore.add("");
        statusLore.add(LegacyText.YELLOW + "Click: " + (settings.enabled() ? "pause" : "activate"));
        ItemStack statusItem = GuiItems.item(new ItemStack(statusMaterial), statusColor + "AI Behaviour: " + status,
                statusLore);
        inventory.setItem(STATUS_SLOT, settings.enabled() ? GuiItems.glint(statusItem) : statusItem);
        openInventory(player, inventory);
    }

    private Material capabilityIcon(AiActionType type) {
        return switch (type) {
            case SAY -> Material.BELL;
            case PLAY_ANIMATION -> Material.ARMOR_STAND;
            case START_COMBAT, STOP_COMBAT -> Material.IRON_SWORD;
            case FLEE_FROM -> Material.RABBIT_FOOT;
            case FOLLOW, UNFOLLOW -> Material.LEAD;
            case INTERACT -> Material.LEVER;
            case MOVE_TO -> Material.LEATHER_BOOTS;
            case MINE_BLOCKS -> Material.IRON_PICKAXE;
            case RETURN_HOME -> Material.RED_BED;
            case START_ROUTE, PAUSE_ROUTE -> Material.POWERED_RAIL;
            case REMEMBER_FACT -> Material.WRITABLE_BOOK;
            case REMEMBER_LOCATION -> Material.FILLED_MAP;
            case DROP_ITEM -> Material.DROPPER;
            case DO_NOTHING -> Material.CLOCK;
        };
    }

    boolean hasTrigger(NpcDefinition definition) {
        for (BehaviourEvent event : BehaviourEvent.values()) {
            if (containsTrigger(definition.getBehaviourActions(event)))
                return true;
        }
        for (String eventName : definition.getCustomEventNames()) {
            if (containsTrigger(definition.getCustomEventActions(eventName)))
                return true;
        }
        return false;
    }

    private boolean containsTrigger(List<BehaviourAction> actions) {
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.AI_TRIGGER)
                return true;
            if (action.type() != BehaviourActionType.ASK_QUESTION || action.question() == null)
                continue;
            for (var option : action.question().options()) {
                if (containsTrigger(option.actions()))
                    return true;
            }
            if (containsTrigger(action.question().cancelActions()))
                return true;
        }
        return false;
    }

    void openMemories(Player player, NpcDefinition definition) {
        Inventory inventory = Bukkit.createInventory(new AiMemoryHolder(definition.getKey()), 54,
                UiText.title("Memory", definition.getDisplayName()));
        List<AiMemory> memories = definition.getAiMemoryEntries();
        for (int index = 0; index < memories.size(); index++) {
            AiMemory memory = memories.get(index);
            List<String> lore = new ArrayList<>();
            TextUtil.wrap(memory.fact(), 44).stream().map(line -> LegacyText.WHITE + line).forEach(lore::add);
            lore.add("");
            for (AiMemory.Category category : AiMemory.Category.values()) {
                lore.add((category == memory.category()
                        ? LegacyText.WHITE + GuiItems.MARKER
                        : LegacyText.DARK_GRAY + GuiItems.UNMARKED) + categoryLabel(category));
            }
            if (memory.category() == AiMemory.Category.REGIONAL && memory.origin() != null) {
                lore.add(LegacyText.DARK_GRAY + "Origin: " + memory.origin().worldName() + " ("
                        + (int) memory.origin().x() + ", " + (int) memory.origin().y() + ", "
                        + (int) memory.origin().z() + ")");
            }
            lore.add("");
            lore.add(GuiItems.hint("Left-click", "edit"));
            lore.add(GuiItems.hint("Shift-left-click", "next category"));
            lore.add(GuiItems.danger("Right-click", "delete"));
            inventory.setItem(index, item(categoryIcon(memory.category()), "Memory " + (index + 1), lore));
        }
        if (memories.isEmpty())
            inventory.setItem(22, GuiItems.empty("No Memories", "Add facts below, or let the AI remember them"));
        inventory.setItem(BACK_SLOT, GuiItems.back("AI Behaviour"));
        inventory.setItem(ADD_MEMORY_SLOT, GuiItems.create("Add Memory", "At capacity, the oldest Temporal memory",
                "(or else the oldest memory) is replaced"));
        openInventory(player, inventory);
    }

    private void handleControlClick(InventoryClickEvent event, Player player, AiControlHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event))
            return;
        NpcDefinition definition = definitions.find(holder.key()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= IDENTITY_SLOT && slot <= LIKES_DISLIKES_SLOT) {
            requestContext(player, definition, slot);
            return;
        }
        if (slot == MEMORY_SLOT) {
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                definition.clearAiMemories();
                definitions.save(definition);
                player.sendMessage(UiText.info("Cleared all memories for " + definition.getDisplayName() + "."));
                open(player, definition);
            } else if (event.isLeftClick()) {
                openMemories(player, definition);
            } else if (event.isRightClick()) {
                AiControlSettings settings = definition.getAiControlSettings();
                definition.setAiControlSettings(settings.withMemoryEnabled(!settings.memoryEnabled()));
                definitions.save(definition);
                GuiItems.click(player);
                open(player, definition);
            }
            return;
        }
        if (slot == CONVERSATION_SLOT) {
            if (GuiItems.cycleDirection(event.getClick()) == 0)
                return;
            AiControlSettings settings = definition.getAiControlSettings();
            definition.setAiControlSettings(settings.withSharedConversation(!settings.sharedConversation()));
            GuiItems.click(player);
        } else if (slot == BACK_SLOT) {
            back.accept(player, definition);
            return;
        } else if (slot == STATUS_SLOT) {
            AiControlSettings settings = definition.getAiControlSettings();
            if (!settings.enabled() && !settings.hasContext()) {
                player.sendMessage(
                        UiText.warning("Fill in at least one context section (top row) before activating AI."));
                return;
            }
            definition.setAiControlSettings(settings.withEnabled(!settings.enabled()));
            definitions.save(definition);
            GuiItems.click(player);
            if (!settings.enabled() && aiControl != null && !aiControl.configured()) {
                player.sendMessage(UiText.warning("AI behaviour is active, but OpenRouter "
                        + aiControl.configurationIssue() + ". Check config.yml and restart the server."));
            }
            open(player, definition);
            return;
        } else {
            for (int index = 0; index < ACTION_TYPES.size(); index++) {
                if (slot != ACTION_SLOTS[index])
                    continue;
                AiActionType type = ACTION_TYPES.get(index);
                if (type == AiActionType.SAY) {
                    definition.setAiControlSettings(definition.getAiControlSettings()
                            .withRespondToChat(!definition.getAiControlSettings().respondToChat()));
                } else if (type != AiActionType.DO_NOTHING) {
                    definition.setAiControlSettings(definition.getAiControlSettings().toggle(type));
                } else {
                    return;
                }
                GuiItems.click(player);
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
        if (!isTopInventoryClick(event))
            return;
        NpcDefinition definition = definitions.find(holder.key()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            open(player, definition);
            return;
        }
        if (slot == ADD_MEMORY_SLOT) {
            requestMemory(player, definition, -1);
            return;
        }
        if (slot < 0 || slot >= definition.getAiMemories().size())
            return;
        if (event.isShiftClick() && event.isLeftClick()) {
            AiMemory previous = definition.getAiMemoryEntries().get(slot);
            AiMemory.Category next = switch (previous.category()) {
                case PERSONAL -> AiMemory.Category.REGIONAL;
                case REGIONAL -> AiMemory.Category.TEMPORAL;
                case TEMPORAL -> AiMemory.Category.PERSONAL;
            };
            StoredLocation origin = next == AiMemory.Category.REGIONAL
                    ? StoredLocation.from(player.getLocation())
                    : null;
            definition.setAiMemoryEntry(slot, new AiMemory(previous.fact(), next, origin, System.currentTimeMillis()));
            definitions.save(definition);
            openMemories(player, definition);
        } else if (event.isRightClick()) {
            definition.removeAiMemory(slot);
            definitions.save(definition);
            openMemories(player, definition);
        } else
            requestMemory(player, definition, slot);
    }

    private void requestMemory(Player player, NpcDefinition definition, int index) {
        String prompt = index < 0
                ? "Enter a fact for the NPC to remember:"
                : "Edit this memory, or enter 'clear' to delete it:";
        chatInput.request(player, prompt, value -> {
            if (index < 0) {
                if (!definition.addAiMemory(value, AiMemory.Category.PERSONAL)) {
                    player.sendMessage(UiText.info("Enter a non-empty fact to remember."));
                    openMemories(player, definition);
                    return;
                }
            } else
                definition.setAiMemory(index, value.equalsIgnoreCase("clear") ? "" : value);
            definitions.save(definition);
            openMemories(player, definition);
        }, () -> definitions.find(definition.getKey()).ifPresent(current -> openMemories(player, current)));
    }

    private void requestContext(Player player, NpcDefinition definition, int slot) {
        AiControlSettings settings = definition.getAiControlSettings();
        String title = switch (slot) {
            case IDENTITY_SLOT -> "Identity";
            case BEHAVIOUR_SLOT -> "Personality & Behaviour";
            case GOAL_SLOT -> "Goal / Role";
            case INFORMATION_SLOT -> "Knowledge / Information";
            case LIKES_DISLIKES_SLOT -> "Likes & Dislikes";
            default -> throw new IllegalArgumentException("Unknown AI context slot: " + slot);
        };
        String value = switch (slot) {
            case IDENTITY_SLOT -> settings.identity();
            case BEHAVIOUR_SLOT -> settings.behaviour();
            case GOAL_SLOT -> settings.goal();
            case INFORMATION_SLOT -> settings.information();
            case LIKES_DISLIKES_SLOT -> settings.likesDislikes();
            default -> throw new IllegalArgumentException("Unknown AI context slot: " + slot);
        };
        ActionButton save = ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player submitting)
                            || !submitting.getUniqueId().equals(player.getUniqueId()))
                        return;
                    String input = view.getText(CONTEXT_INPUT_KEY);
                    if (input == null)
                        return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        NpcDefinition currentDefinition = definitions.find(definition.getKey()).orElse(null);
                        if (currentDefinition == null || !submitting.isOnline())
                            return;
                        AiControlSettings current = currentDefinition.getAiControlSettings();
                        currentDefinition.setAiControlSettings(switch (slot) {
                            case IDENTITY_SLOT -> current.withIdentity(input);
                            case BEHAVIOUR_SLOT -> current.withBehaviour(input);
                            case GOAL_SLOT -> current.withGoal(input);
                            case INFORMATION_SLOT -> current.withInformation(input);
                            case LIKES_DISLIKES_SLOT -> current.withLikesDislikes(input);
                            default -> current;
                        });
                        definitions.save(currentDefinition);
                        open(submitting, currentDefinition);
                    });
                }, ClickCallback.Options.builder().uses(1).build())).build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel", NamedTextColor.RED)).build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title)).canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .inputs(List.of(DialogInput.text(CONTEXT_INPUT_KEY, Component.text("AI prompt")).width(400)
                                .initial(value).maxLength(Math.max(CONTEXT_MAX_LENGTH, value.length()))
                                .multiline(TextDialogInput.MultilineOptions.create(null, 160)).build()))
                        .build())
                .type(DialogType.confirmation(save, cancel)));
        player.showDialog(dialog);
    }

    private static String categoryLabel(AiMemory.Category category) {
        return switch (category) {
            case PERSONAL -> "Personal: known only to this NPC";
            case REGIONAL -> "Regional: shared within 50 blocks";
            case TEMPORAL -> "Temporal: expires after 24 hours";
        };
    }

    private static Material categoryIcon(AiMemory.Category category) {
        return switch (category) {
            case PERSONAL -> Material.PAPER;
            case REGIONAL -> Material.MAP;
            case TEMPORAL -> Material.CLOCK;
        };
    }

    private ItemStack contextItem(Material material, String name, String value, String description) {
        return item(material, name,
                List.of(LegacyText.GRAY + description,
                        value.isBlank()
                                ? LegacyText.RED + "Not configured"
                                : LegacyText.WHITE + TextUtil.abbreviateSingleLine(value, 48),
                        "", GuiItems.hint("Click", "edit"), LegacyText.DARK_GRAY + "Empty the field to remove it"));
    }

    private String capabilityDescription(AiActionType type) {
        return switch (type) {
            case SAY -> "Speaks a short response in the NPC's character";
            case PLAY_ANIMATION -> "Performs a wave, jump, sneak, or stand animation";
            case START_COMBAT -> "Needs max health above 0 and a living, attackable target";
            case STOP_COMBAT -> "Ends the NPC's current combat encounter";
            case FLEE_FROM -> "Moves away from a selected nearby entity";
            case FOLLOW -> "Follows a nearby player or stops following";
            case UNFOLLOW -> "Stops following the player it is currently following";
            case INTERACT -> "Uses nearby buttons, levers, or containers";
            case MOVE_TO -> "Walks to a known location, player, NPC, or entity";
            case MINE_BLOCKS -> "Mines nearby resources; collects drops when item pickup is enabled";
            case RETURN_HOME -> "Walks back to this instance's respawn location";
            case START_ROUTE -> "Resumes or pauses this instance's configured route";
            case PAUSE_ROUTE -> "Pauses this instance's configured route";
            case REMEMBER_FACT -> "Saves a durable fact for future conversations";
            case REMEMBER_LOCATION -> "Saves this NPC's position with a label for all NPCs to use";
            case DROP_ITEM -> "Drops a carried item from Temporary Inventory";
            case DO_NOTHING -> "Takes no action when a response is not needed";
        };
    }

    private String capabilityName(AiActionType type) {
        return switch (type) {
            case START_COMBAT -> "Start/Stop Combat";
            case FOLLOW -> "Follow/Unfollow";
            case START_ROUTE -> "Start/Pause Route";
            default -> type.displayName();
        };
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        return GuiItems.item(material, name, lore);
    }

    private void openInventory(Player player, Inventory inventory) {
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private boolean isTopInventoryClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        return slot >= 0 && slot < event.getView().getTopInventory().getSize();
    }

    private record AiControlHolder(String key) implements GuiHolder {
    }

    private record AiMemoryHolder(String key) implements GuiHolder {
    }
}
