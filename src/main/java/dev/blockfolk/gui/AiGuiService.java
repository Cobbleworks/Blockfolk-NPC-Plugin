package dev.blockfolk.gui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.blockfolk.ai.AiActionType;
import dev.blockfolk.ai.AiControlService;
import dev.blockfolk.ai.AiControlSettings;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.BehaviourActionType;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.util.TextUtil;
import dev.blockfolk.util.UiText;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Owns the native AI behaviour and long-term-memory dialogs. */
final class AiGuiService {

    private static final int FORM_WIDTH = 400;
    private static final int CONTEXT_MAX_LENGTH = 8_192;
    private static final int MEMORY_MAX_LENGTH = 4_096;
    private static final TextDialogInput.MultilineOptions CONTEXT_BOX = TextDialogInput.MultilineOptions.create(6, 90);
    private static final TextDialogInput.MultilineOptions MEMORY_BOX = TextDialogInput.MultilineOptions.create(5, 80);
    private static final List<AiActionType> OPTIONAL_ACTIONS = Arrays.stream(AiActionType.values())
            .filter(type -> type != AiActionType.SAY && type != AiActionType.DO_NOTHING)
            .filter(type -> type != AiActionType.REMEMBER_FACT && type != AiActionType.DROP_ITEM).toList();

    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final BiConsumer<Player, NpcDefinition> back;
    private AiControlService aiControl;

    AiGuiService(Plugin plugin, NpcDefinitionRepository definitions, BiConsumer<Player, NpcDefinition> back) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.back = back;
    }

    void setAiControlService(AiControlService aiControl) {
        this.aiControl = aiControl;
    }

    void open(Player player, NpcDefinition definition) {
        AiControlSettings settings = definition.getAiControlSettings();
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(contextInput("identity", "Identity", settings.identity()));
        inputs.add(contextInput("behaviour", "Personality & Behaviour", settings.behaviour()));
        inputs.add(contextInput("goal", "Goal / Role", settings.goal()));
        inputs.add(contextInput("information", "Knowledge / Information", settings.information()));
        inputs.add(contextInput("likes_dislikes", "Likes & Dislikes", settings.likesDislikes()));
        inputs.add(DialogInput.bool("enabled", Component.text("AI behaviour enabled")).initial(settings.enabled())
                .build());
        inputs.add(DialogInput.bool("respond_to_chat", Component.text("Respond to nearby chat"))
                .initial(settings.respondToChat()).build());
        inputs.add(DialogInput.bool("memory_enabled", Component.text("Use long-term memories"))
                .initial(settings.memoryEnabled()).build());
        inputs.add(DialogInput.bool("inventory_enabled", Component.text("Use temporary inventory"))
                .initial(settings.inventoryEnabled()).build());
        inputs.add(DialogInput.bool("shared_conversation", Component.text("Share conversation between players"))
                .initial(settings.sharedConversation()).build());
        for (AiActionType type : OPTIONAL_ACTIONS) {
            Component label = Component.text("Allow: " + type.displayName())
                    .hoverEvent(HoverEvent.showText(Component.text(capabilityDescription(type), NamedTextColor.GRAY)));
            inputs.add(
                    DialogInput.bool(actionKey(type), label).initial(settings.allowedActions().contains(type)).build());
        }

        ActionButton save = ActionButton.builder(Component.text("Save & Back", NamedTextColor.GREEN))
                .tooltip(Component.text("Apply this configuration", NamedTextColor.GRAY))
                .action(dialogAction(player, (response, clicked) -> applySettings(clicked, definition.getKey(),
                        response, current -> back.accept(clicked, current))))
                .build();
        ActionButton memories = ActionButton.builder(Component.text("Save & Manage Memories", NamedTextColor.AQUA))
                .tooltip(Component.text(definition.getAiMemories().size() + " saved long-term memories",
                        NamedTextColor.GRAY))
                .action(dialogAction(player, (response, clicked) -> applySettings(clicked, definition.getKey(),
                        response, current -> openMemories(clicked, current))))
                .build();
        ActionButton cancel = ActionButton.builder(Component.text("Discard", NamedTextColor.RED))
                .tooltip(Component.text("Close without saving", NamedTextColor.GRAY)).build();

        boolean hasTrigger = hasTrigger(definition) || settings.respondToChat();
        String status = !settings.enabled() ? "Paused" : hasTrigger ? "Active" : "Enabled, but has no trigger";
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Status: " + status,
                settings.enabled() && hasTrigger ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
        body.add(DialogBody.plainMessage(
                Component.text("Configure the NPC context, runtime options, and actions the model may request.",
                        NamedTextColor.GRAY)));
        if (aiControl != null && !aiControl.configured()) {
            body.add(DialogBody.plainMessage(
                    Component.text("OpenRouter is not ready: " + aiControl.configurationIssue(), NamedTextColor.RED)));
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("AI Behaviour", NamedTextColor.DARK_AQUA))
                        .externalTitle(Component.text("AI: " + definition.getDisplayName(), NamedTextColor.AQUA))
                        .afterAction(DialogBase.DialogAfterAction.CLOSE).body(body).inputs(inputs).build())
                .type(DialogType.multiAction(List.of(save, memories, cancel)).columns(3).build()));
        player.showDialog(dialog);
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

    private DialogInput contextInput(String key, String label, String value) {
        return DialogInput.text(key, Component.text(label, NamedTextColor.YELLOW)).width(FORM_WIDTH).initial(value)
                .maxLength(Math.max(CONTEXT_MAX_LENGTH, value.length())).multiline(CONTEXT_BOX).build();
    }

    private void applySettings(Player player, String definitionKey, DialogResponseView response,
            java.util.function.Consumer<NpcDefinition> continuation) {
        NpcDefinition definition = definitions.find(definitionKey).orElse(null);
        if (definition == null) {
            player.sendMessage(UiText.error("That NPC preset no longer exists."));
            player.closeDialog();
            return;
        }
        String identity = text(response, "identity");
        String behaviour = text(response, "behaviour");
        String likesDislikes = text(response, "likes_dislikes");
        String goal = text(response, "goal");
        String information = text(response, "information");
        boolean enabled = bool(response, "enabled");
        boolean hasContext = !identity.isBlank() || !behaviour.isBlank() || !likesDislikes.isBlank() || !goal.isBlank()
                || !information.isBlank();
        if (enabled && !hasContext) {
            enabled = false;
            player.sendMessage(
                    UiText.warning("Configure at least one AI context section before activating AI behaviour."));
        }

        EnumSet<AiActionType> actions = EnumSet.of(AiActionType.SAY, AiActionType.DO_NOTHING);
        for (AiActionType type : OPTIONAL_ACTIONS) {
            if (bool(response, actionKey(type)))
                actions.add(type);
        }
        definition.setAiControlSettings(new AiControlSettings(identity, behaviour, likesDislikes, goal, information,
                actions, enabled, bool(response, "respond_to_chat"), bool(response, "memory_enabled"),
                bool(response, "inventory_enabled"), bool(response, "shared_conversation")));
        definitions.save(definition);
        if (enabled && aiControl != null && !aiControl.configured()) {
            player.sendMessage(
                    UiText.warning("AI behaviour is active, but OpenRouter " + aiControl.configurationIssue() + "."));
        } else {
            player.sendMessage(UiText.success("AI behaviour settings saved."));
        }
        continuation.accept(definition);
    }

    private void openMemories(Player player, NpcDefinition definition) {
        List<Dialog> entries = new ArrayList<>();
        List<String> memories = definition.getAiMemories();
        for (int index = 0; index < memories.size(); index++) {
            entries.add(memoryDialog(player, definition.getKey(), index, memories.get(index)));
        }
        entries.add(addMemoryDialog(player, definition.getKey()));
        if (!memories.isEmpty())
            entries.add(clearMemoriesDialog(player, definition.getKey(), memories.size()));

        ActionButton backButton = ActionButton.builder(Component.text("Back to AI Behaviour", NamedTextColor.RED))
                .tooltip(Component.text("Return to the AI configuration form", NamedTextColor.GRAY))
                .action(dialogAction(player, (response, clicked) -> reopen(clicked, definition.getKey(), false)))
                .build();
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Long-Term Memory", NamedTextColor.DARK_AQUA))
                        .externalTitle(Component.text("Memories: " + definition.getDisplayName(), NamedTextColor.AQUA))
                        .body(List
                                .of(DialogBody.plainMessage(Component.text(
                                        memories.size() + " / " + NpcDefinition.MAX_AI_MEMORIES
                                                + " facts saved. The oldest is discarded when full.",
                                        NamedTextColor.GRAY))))
                        .build())
                .type(DialogType.dialogList(RegistrySet.valueSet(RegistryKey.DIALOG, entries)).columns(1)
                        .buttonWidth(FORM_WIDTH).exitAction(backButton).build()));
        player.showDialog(dialog);
    }

    private Dialog memoryDialog(Player player, String definitionKey, int index, String memory) {
        ActionButton save = ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                .action(dialogAction(player,
                        (response, clicked) -> updateMemory(clicked, definitionKey, index, text(response, "memory"))))
                .build();
        ActionButton delete = ActionButton.builder(Component.text("Delete", NamedTextColor.RED))
                .tooltip(Component.text("Remove this memory", NamedTextColor.RED))
                .action(dialogAction(player, (response, clicked) -> updateMemory(clicked, definitionKey, index, "")))
                .build();
        return Dialog
                .create(builder -> builder
                        .empty().base(
                                DialogBase.builder(Component.text("Memory " + (index + 1), NamedTextColor.GOLD))
                                        .externalTitle(Component
                                                .text((index + 1) + ". " + TextUtil.abbreviateSingleLine(memory, 72),
                                                        NamedTextColor.GOLD))
                                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                                        .inputs(List.of(DialogInput.text("memory", Component.text("Remembered fact"))
                                                .width(FORM_WIDTH).initial(memory)
                                                .maxLength(Math.max(MEMORY_MAX_LENGTH, memory.length()))
                                                .multiline(MEMORY_BOX).build()))
                                        .build())
                        .type(DialogType.multiAction(List.of(save, delete)).columns(2).build()));
    }

    private Dialog addMemoryDialog(Player player, String definitionKey) {
        ActionButton add = ActionButton.builder(Component.text("Add Memory", NamedTextColor.GREEN))
                .action(dialogAction(player,
                        (response, clicked) -> addMemory(clicked, definitionKey, text(response, "memory"))))
                .build();
        return Dialog
                .create(builder -> builder
                        .empty().base(
                                DialogBase
                                        .builder(Component.text("Add Memory",
                                                NamedTextColor.GREEN))
                                        .externalTitle(Component.text("+ Add Memory", NamedTextColor.GREEN))
                                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                                        .inputs(List.of(DialogInput.text("memory", Component.text("Fact to remember"))
                                                .width(FORM_WIDTH).maxLength(MEMORY_MAX_LENGTH).multiline(MEMORY_BOX)
                                                .build()))
                                        .build())
                        .type(DialogType.notice(add)));
    }

    private Dialog clearMemoriesDialog(Player player, String definitionKey, int count) {
        ActionButton clear = ActionButton.builder(Component.text("Clear All", NamedTextColor.RED))
                .tooltip(Component.text("Permanently remove every saved memory", NamedTextColor.RED))
                .action(dialogAction(player, (response, clicked) -> clearMemories(clicked, definitionKey))).build();
        ActionButton cancel = ActionButton.builder(Component.text("Keep Memories", NamedTextColor.GREEN))
                .action(dialogAction(player, (response, clicked) -> reopen(clicked, definitionKey, true))).build();
        return Dialog.create(builder -> builder.empty().base(DialogBase
                .builder(Component.text("Clear All Memories?", NamedTextColor.RED))
                .externalTitle(Component.text("Clear All Memories", NamedTextColor.RED))
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(
                        Component.text("Permanently remove all " + count + " saved memories?", NamedTextColor.GRAY))))
                .build()).type(DialogType.confirmation(clear, cancel)));
    }

    private void addMemory(Player player, String definitionKey, String value) {
        NpcDefinition definition = definitions.find(definitionKey).orElse(null);
        if (definition == null) {
            player.sendMessage(UiText.error("That NPC preset no longer exists."));
            return;
        }
        if (value.isBlank()) {
            player.sendMessage(UiText.warning("A memory cannot be blank."));
        } else {
            definition.addAiMemory(value);
            definitions.save(definition);
            player.sendMessage(UiText.success("Memory added."));
        }
        openMemories(player, definition);
    }

    private void updateMemory(Player player, String definitionKey, int index, String value) {
        NpcDefinition definition = definitions.find(definitionKey).orElse(null);
        if (definition == null) {
            player.sendMessage(UiText.error("That NPC preset no longer exists."));
            return;
        }
        if (index < 0 || index >= definition.getAiMemories().size()) {
            player.sendMessage(UiText.warning("That memory has already changed."));
        } else {
            definition.setAiMemory(index, value);
            definitions.save(definition);
            player.sendMessage(value.isBlank() ? UiText.success("Memory deleted.") : UiText.success("Memory updated."));
        }
        openMemories(player, definition);
    }

    private void clearMemories(Player player, String definitionKey) {
        NpcDefinition definition = definitions.find(definitionKey).orElse(null);
        if (definition == null) {
            player.sendMessage(UiText.error("That NPC preset no longer exists."));
            return;
        }
        definition.clearAiMemories();
        definitions.save(definition);
        player.sendMessage(UiText.success("All long-term memories cleared."));
        openMemories(player, definition);
    }

    private void reopen(Player player, String definitionKey, boolean memories) {
        NpcDefinition definition = definitions.find(definitionKey).orElse(null);
        if (definition == null) {
            player.sendMessage(UiText.error("That NPC preset no longer exists."));
            return;
        }
        if (memories)
            openMemories(player, definition);
        else
            open(player, definition);
    }

    private DialogAction dialogAction(Player player, BiConsumer<DialogResponseView, Player> action) {
        UUID playerId = player.getUniqueId();
        return DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player clicked && clicked.getUniqueId().equals(playerId))
                Bukkit.getScheduler().runTask(plugin, () -> action.accept(response, clicked));
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(15)).build());
    }

    private String actionKey(AiActionType type) {
        return "action_" + type.name().toLowerCase(Locale.ROOT);
    }

    private String text(DialogResponseView response, String key) {
        String value = response.getText(key);
        return value == null ? "" : value.trim();
    }

    private boolean bool(DialogResponseView response, String key) {
        return Boolean.TRUE.equals(response.getBoolean(key));
    }

    private String capabilityDescription(AiActionType type) {
        return switch (type) {
            case SAY -> "Speaks a short response in the NPC's character";
            case PLAY_ANIMATION -> "Performs a wave, jump, sneak, or stand animation";
            case START_COMBAT -> "Attacks a nearby target or the nearest safe target";
            case STOP_COMBAT -> "Ends the NPC's current combat encounter";
            case FLEE_FROM -> "Moves away from a selected nearby entity";
            case FOLLOW -> "Follows a selected nearby player";
            case UNFOLLOW -> "Stops following the player it is currently following";
            case INTERACT -> "Uses nearby buttons, levers, or containers";
            case MOVE_TO -> "Walks to a known location, player, NPC, or entity";
            case MINE_BLOCKS -> "Mines nearby resources; inventory controls where drops go";
            case RETURN_HOME -> "Walks back to this instance's respawn location";
            case START_ROUTE -> "Resumes this instance's configured route";
            case PAUSE_ROUTE -> "Pauses this instance's configured route";
            case REMEMBER_FACT -> "Saves a durable fact for future conversations";
            case DROP_ITEM -> "Drops a carried item from Temporary Inventory";
            case DO_NOTHING -> "Takes no action when a response is not needed";
        };
    }
}
