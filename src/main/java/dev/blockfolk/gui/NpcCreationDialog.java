package dev.blockfolk.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.blockfolk.model.CombatProfile;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.util.UiText;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

final class NpcCreationDialog {

    private static final String NAME = "name";
    private static final String HEALTH = "health";
    private static final String RESPAWN = "respawn";
    private static final String PICKUP = "pickup";
    private static final String SHOW_NAME = "show_name";
    private static final String LOOK_AT_PLAYER = "look_at_player";
    private static final String PUSHABLE = "pushable";
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_RESPAWN_SECONDS = 3600;

    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final NpcInstanceRegistry instances;
    private final BiConsumer<Player, NpcDefinition> editor;

    NpcCreationDialog(Plugin plugin, NpcDefinitionRepository definitions, NpcInstanceRegistry instances,
            BiConsumer<Player, NpcDefinition> editor) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.instances = instances;
        this.editor = editor;
    }

    void show(Player player, String initialName) {
        String name = initialName == null ? "" : initialName;
        show(player, new CreationValues(name.substring(0, Math.min(name.length(), MAX_NAME_LENGTH)), 0, 0, false, true,
                true, true));
    }

    private void show(Player player, CreationValues initial) {
        ActionButton create = ActionButton.builder(Component.text("Create NPC", NamedTextColor.GREEN))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player submitting)
                            || !submitting.getUniqueId().equals(player.getUniqueId())) {
                        return;
                    }
                    CreationValues values = CreationValues.from(view);
                    UUID playerId = submitting.getUniqueId();
                    Bukkit.getScheduler().runTask(plugin, () -> submit(playerId, values));
                }, ClickCallback.Options.builder().uses(1).build())).build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel", NamedTextColor.RED)).build();
        Dialog dialog = Dialog.create(builder -> builder.empty().base(DialogBase.builder(Component.text("Create NPC"))
                .canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Component.text(
                        "Set the NPC's starting properties. Health 0 makes it invulnerable; respawn 0 disables respawning."))))
                .inputs(List.of(
                        DialogInput.text(NAME, Component.text("Name")).width(300).initial(initial.name())
                                .maxLength(MAX_NAME_LENGTH).build(),
                        DialogInput.numberRange(HEALTH, Component.text("HP"), 0, CombatProfile.MAX_HEALTH)
                                .initial((float) initial.health()).step(1.0F).width(300).build(),
                        DialogInput
                                .numberRange(RESPAWN, Component.text("Respawn time (seconds)"), 0, MAX_RESPAWN_SECONDS)
                                .initial((float) initial.respawnSeconds()).step(10.0F).width(300).build(),
                        DialogInput.bool(PICKUP, Component.text("Pick up items")).initial(initial.pickup()).build(),
                        DialogInput.bool(SHOW_NAME, Component.text("Show name")).initial(initial.showName()).build(),
                        DialogInput.bool(LOOK_AT_PLAYER, Component.text("Look at players"))
                                .initial(initial.lookAtPlayer()).build(),
                        DialogInput.bool(PUSHABLE, Component.text("Can be pushed")).initial(initial.pushable())
                                .build()))
                .build()).type(DialogType.confirmation(create, cancel)));
        player.showDialog(dialog);
    }

    private void submit(UUID playerId, CreationValues values) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || !player.hasPermission("blockfolk.admin")) {
            return;
        }
        if (values == null) {
            player.sendMessage(UiText.error("Invalid NPC settings. Please open the creation dialog again."));
            return;
        }
        String name = values.name().trim();
        if (name.isEmpty()) {
            player.sendMessage(UiText.error("NPC names cannot be blank."));
            show(player, values);
            return;
        }
        NpcDefinition definition = NpcDefinition.create(name);
        if (definitions.find(definition.getKey()).isPresent()) {
            player.sendMessage(UiText.error("An NPC with that key already exists."));
            show(player, values);
            return;
        }
        definition.setCombatProfile(definition.getCombatProfile().withMaxHealth(values.health())
                .withRespawnSeconds(values.respawnSeconds()));
        definition.setItemPickup(values.pickup());
        definition.setShowName(values.showName());
        definition.setLookAtPlayer(values.lookAtPlayer());
        definition.setPushable(values.pushable());
        definition.setSpawnpoint(player.getLocation());
        definitions.save(definition);
        if (instances.spawnPersistent(definition, definition.getSpawnpoint()) == null) {
            player.sendMessage(UiText.warning("Preset created, but its NPC could not be rendered."));
        }
        editor.accept(player, definition);
    }

    record CreationValues(String name, int health, int respawnSeconds, boolean pickup, boolean showName,
            boolean lookAtPlayer, boolean pushable) {

        static CreationValues from(DialogResponseView view) {
            if (view == null) {
                return null;
            }
            String name = view.getText(NAME);
            Float health = view.getFloat(HEALTH);
            Float respawn = view.getFloat(RESPAWN);
            Boolean pickup = view.getBoolean(PICKUP);
            Boolean showName = view.getBoolean(SHOW_NAME);
            Boolean lookAtPlayer = view.getBoolean(LOOK_AT_PLAYER);
            Boolean pushable = view.getBoolean(PUSHABLE);
            if (name == null || name.length() > MAX_NAME_LENGTH || !validNumber(health, CombatProfile.MAX_HEALTH)
                    || !validNumber(respawn, MAX_RESPAWN_SECONDS) || pickup == null || showName == null
                    || lookAtPlayer == null || pushable == null) {
                return null;
            }
            return new CreationValues(name, health.intValue(), respawn.intValue(), pickup, showName, lookAtPlayer,
                    pushable);
        }

        private static boolean validNumber(Float value, int max) {
            return value != null && Float.isFinite(value) && value >= 0 && value <= max && value == Math.floor(value);
        }
    }
}
