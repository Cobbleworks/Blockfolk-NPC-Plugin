package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.NamedLocation;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.Scene;
import dev.blockfolk.model.SceneBeat;
import dev.blockfolk.model.SceneBeatAdvance;
import dev.blockfolk.model.SceneEnding;
import dev.blockfolk.model.SceneRole;
import dev.blockfolk.model.SceneTrigger;
import dev.blockfolk.repository.LocationRepository;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.SceneRepository;
import dev.blockfolk.runtime.SceneService;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.TextUtil;
import dev.blockfolk.util.UiText;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Menus for scenes: the scene browser, the scene editor with triggers (one of
 * which watches an area), ending, and test controls, and the script, a grid of
 * beats (rows) and roles (columns). Role action rows are edited by
 * {@link GuiService#openSceneRole}, which shares the preset action editor.
 */
public final class SceneGuiService implements Listener, GuiService.SceneNavigation {

    private static final int PAGE_SIZE = 45;
    private static final String DESCRIPTION_INPUT_KEY = "description";
    private static final int SUMMARY_SLOT = 4;
    private static final int NAME_SLOT = 19;
    private static final int DESCRIPTION_SLOT = 28;
    private static final int AI_BEHAVIOUR_SLOT = 37;
    private static final int TRIGGERS_SLOT = 21;
    private static final int ENABLED_SLOT = 30;
    private static final int SCRIPT_SLOT = 23;
    private static final int ENDING_SLOT = 32;
    private static final int END_SETTING_SLOT = 41;
    private static final int RUN_SLOT = 25;
    private static final int CHANCE_SLOT = 34;
    private static final int COOLDOWN_SLOT = 43;
    private static final int DELETE_SLOT = 53;
    /** Radius of the Player Enters area, beside its toggle in the triggers menu. */
    private static final int TRIGGER_RADIUS_SLOT = 41;
    /** Trigger toggles: time of day on row 1, weather on row 3, area on row 5. */
    private static final Map<Integer, SceneTrigger> TRIGGER_SLOTS = Map.ofEntries(Map.entry(11, SceneTrigger.NIGHT),
            Map.entry(12, SceneTrigger.MORNING), Map.entry(13, SceneTrigger.NOON),
            Map.entry(14, SceneTrigger.AFTERNOON), Map.entry(15, SceneTrigger.EVENING),
            Map.entry(29, SceneTrigger.PRECIPITATION), Map.entry(30, SceneTrigger.RAIN),
            Map.entry(31, SceneTrigger.SNOW), Map.entry(32, SceneTrigger.THUNDER), Map.entry(33, SceneTrigger.CLEAR),
            Map.entry(40, SceneTrigger.PLAYER_ENTERS));
    /** The script shows eight role columns and four beat rows per page. */
    private static final int SCRIPT_COLUMNS = 8;
    private static final int SCRIPT_ROWS = 4;
    private static final int SCRIPT_ROLES_LEFT_SLOT = 46;
    private static final int SCRIPT_ROLES_RIGHT_SLOT = 52;
    /** Beat settings: advance options on row 1, actions on the bottom row. */
    private static final int[] BEAT_ADVANCE_SLOTS = {10, 11, 12, 13};
    private static final int BEAT_SETTING_SLOT = 15;
    private static final int BEAT_COPY_SLOT = 18;
    private static final int BEAT_BACK_SLOT = 22;
    private static final int BEAT_DELETE_SLOT = 26;

    private final Plugin plugin;
    private final SceneRepository scenes;
    private final NpcDefinitionRepository definitions;
    private final LocationRepository locations;
    private final ChatInputService chatInput;
    private final Consumer<Player> mainMenuOpener;
    private GuiService guiService;
    /**
     * Players who opened the scene list from an NPC's editor see only that NPC's
     * scenes until they go back or open the full list.
     */
    private final Map<java.util.UUID, String> presetFilters = new java.util.HashMap<>();
    /** The script page each player looked at last, so returning keeps it. */
    private final Map<java.util.UUID, ScriptView> scriptViews = new java.util.HashMap<>();
    private SceneService sceneService;

    public SceneGuiService(Plugin plugin, SceneRepository scenes, NpcDefinitionRepository definitions,
            LocationRepository locations, ChatInputService chatInput, Consumer<Player> mainMenuOpener) {
        this.plugin = plugin;
        this.scenes = scenes;
        this.definitions = definitions;
        this.locations = locations;
        this.chatInput = chatInput;
        this.mainMenuOpener = mainMenuOpener;
    }

    public void setGuiService(GuiService guiService) {
        this.guiService = guiService;
    }

    public void setSceneService(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    // ------------------------------------------------------- navigation API

    @Override
    public void openScenes(Player player) {
        presetFilters.remove(player.getUniqueId());
        openBrowser(player, "", 0);
    }

    @Override
    public void openScenesFor(Player player, String presetKey) {
        presetFilters.put(player.getUniqueId(), NpcDefinition.toKey(presetKey));
        openBrowser(player, "", 0);
    }

    @Override
    public void openScript(Player player, String sceneKey) {
        ScriptView view = scriptViews.get(player.getUniqueId());
        String key = Scene.tryKey(sceneKey).orElse(sceneKey);
        if (view != null && view.sceneKey().equals(key))
            openScript(player, key, view.beatPage(), view.rolePage());
        else
            openScript(player, key, 0, 0);
    }

    @Override
    public void choosePreset(Player player, String sceneKey, String roleId) {
        openPresetPicker(player, sceneKey, roleId, 0);
    }

    @Override
    public void createScene(Player player, Consumer<Scene> onCreated, Runnable onCancel) {
        createScene(player, "", onCreated, onCancel);
    }

    private void createScene(Player player, String folder, Consumer<Scene> onCreated, Runnable onCancel) {
        chatInput.request(player, "Enter the scene name (use / for groups, e.g. western/last-train):", value -> {
            String name = folder.isEmpty() || value.contains("/") ? value : folder + "/" + value;
            Scene scene;
            try {
                scene = Scene.create(name);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(UiText.error(exception.getMessage()));
                if (onCancel != null)
                    onCancel.run();
                return;
            }
            if (scenes.find(scene.getKey()).isPresent()) {
                player.sendMessage(UiText.error("A scene with the key '" + scene.getKey() + "' already exists."));
                if (onCancel != null)
                    onCancel.run();
                return;
            }
            scenes.save(scene);
            player.sendMessage(UiText.success(
                    "Created scene '" + scene.getDisplayName() + "'. Open its Script to add roles and beats."));
            onCreated.accept(scene);
        }, onCancel);
    }

    /** Scene keys for command completion. */
    public List<String> sceneKeys() {
        return scenes.findAll().stream().map(Scene::getKey).toList();
    }

    public java.util.Optional<Scene> findScene(String keyOrName) {
        return scenes.find(keyOrName);
    }

    // ------------------------------------------------------------ browser

    private void openBrowser(Player player, String folder, int requestedPage) {
        NpcDefinition filter = presetFilter(player);
        if (filter != null)
            folder = "";
        List<BrowserEntry> entries = browserEntries(folder, filter);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new BrowserHolder(folder, page), 54,
                filter != null
                        ? UiText.title("Scenes", filter.getDisplayName())
                        : UiText.title(folder.isEmpty() ? "Scenes" : "Scenes: " + folder));
        int from = page * PAGE_SIZE;
        for (int index = from; index < Math.min(from + PAGE_SIZE, entries.size()); index++) {
            BrowserEntry entry = entries.get(index);
            if (entry.scene() == null) {
                inventory.setItem(index - from, GuiItems.item(Material.CHEST, entry.label(), List
                        .of(LegacyText.GRAY + "" + entry.count() + " scene(s)", "", GuiItems.hint("Click", "open"))));
            } else {
                inventory.setItem(index - from, browserItem(entry.scene()));
            }
        }
        if (entries.isEmpty())
            inventory.setItem(22,
                    filter == null
                            ? GuiItems.empty("No Scenes Yet", "Use Create Scene below to add one")
                            : GuiItems.empty(filter.getDisplayName() + " Plays No Role Yet",
                                    "Create Scene below makes one with this NPC cast"));
        if (page > 0)
            inventory.setItem(GuiItems.PREVIOUS_SLOT, GuiItems.previousPage(page, pages));
        inventory.setItem(47,
                GuiItems.item(Material.KNOWLEDGE_BOOK, "How Scenes Work",
                        List.of(LegacyText.GRAY + "A scene is a moment several NPCs play together.",
                                LegacyText.GRAY + "Its script runs beat by beat: in each beat every",
                                LegacyText.GRAY + "role plays its actions at the same time, then",
                                LegacyText.GRAY + "the next beat starts.",
                                LegacyText.GRAY + "Triggers start it at a time, in some weather, or",
                                LegacyText.GRAY + "when a player walks into its area.", "",
                                LegacyText.GRAY + "NPCs pause their route while in a scene and",
                                LegacyText.GRAY + "return to their routine afterwards. If a needed",
                                LegacyText.GRAY + "NPC is busy, the scene is skipped and admins",
                                LegacyText.GRAY + "get a short chat message saying why.")));
        inventory.setItem(GuiItems.BACK_SLOT,
                GuiItems.back(filter != null
                        ? filter.getDisplayName() + "'s menu"
                        : folder.isEmpty() ? "the main menu" : "the parent group"));
        inventory.setItem(GuiItems.CREATE_SLOT, filter == null
                ? GuiItems.create("Create Scene", "Use / in the name to create groups")
                : GuiItems.create("Create Scene", "Starts with " + filter.getDisplayName() + " cast in a role"));
        if (filter != null)
            inventory.setItem(48,
                    GuiItems.item(Material.BELL, "All Scenes",
                            List.of(LegacyText.GRAY + "Showing only scenes " + filter.getDisplayName() + " plays in",
                                    "", GuiItems.hint("Click", "show every scene"))));
        if (page + 1 < pages)
            inventory.setItem(GuiItems.NEXT_SLOT, GuiItems.nextPage(page, pages));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private ItemStack browserItem(Scene scene) {
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.DARK_GRAY + "Key: " + scene.getKey());
        lore.addAll(descriptionLines(scene, 3));
        lore.add(LegacyText.GRAY + "Status: " + statusText(scene));
        lore.add(LegacyText.GRAY + "Roles: " + LegacyText.WHITE + scene.getRoles().size() + LegacyText.GRAY
                + " · Beats: " + LegacyText.WHITE + scene.getBeats().size());
        lore.add(LegacyText.GRAY + "Starts: " + LegacyText.WHITE + triggerSummary(scene));
        lore.addAll(chainLore(scene));
        lore.add("");
        lore.add(GuiItems.hint("Click", "edit"));
        lore.add(GuiItems.danger("Shift-right-click", "delete"));
        return GuiItems.item(sceneIcon(scene), LegacyText.GOLD + scene.getDisplayName(), lore);
    }

    private List<BrowserEntry> browserEntries(String folder, NpcDefinition filter) {
        if (filter != null)
            return scenes.findAll().stream().filter(scene -> scene.features(filter.getKey()))
                    .map(scene -> new BrowserEntry(scene.getKey(), scene.getDisplayName(), scene, 0)).toList();
        String prefix = folder.isEmpty() ? "" : folder + "/";
        Map<String, BrowserEntry> result = new LinkedHashMap<>();
        for (Scene scene : scenes.findAll()) {
            if (!scene.getKey().startsWith(prefix))
                continue;
            String rest = scene.getKey().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String path = prefix + rest.substring(0, slash);
                BrowserEntry previous = result.get("folder:" + path);
                result.put("folder:" + path, new BrowserEntry(path, rest.substring(0, slash), null,
                        previous == null ? 1 : previous.count() + 1));
            } else {
                result.put("scene:" + scene.getKey(), new BrowserEntry(scene.getKey(), rest, scene, 0));
            }
        }
        // Groups first, then scenes, each alphabetical (scenes come sorted by key).
        List<BrowserEntry> entries = new ArrayList<>();
        result.values().stream().filter(entry -> entry.scene() == null).forEach(entries::add);
        result.values().stream().filter(entry -> entry.scene() != null).forEach(entries::add);
        return entries;
    }

    private void handleBrowserClick(InventoryClickEvent click, Player player, BrowserHolder holder) {
        int slot = click.getRawSlot();
        NpcDefinition filter = presetFilter(player);
        if (filter != null && slot == 48) {
            openScenes(player);
            return;
        }
        if (filter != null && slot == GuiItems.BACK_SLOT) {
            presetFilters.remove(player.getUniqueId());
            if (guiService != null)
                guiService.openEditor(player, filter);
            else
                mainMenuOpener.accept(player);
            return;
        }
        if (filter != null && slot == GuiItems.CREATE_SLOT) {
            createScene(player, "", scene -> {
                scene.addRole(filter.getKey());
                scenes.save(scene);
                openEditor(player, scene.getKey());
            }, () -> openBrowser(player, "", holder.page()));
            return;
        }
        if (slot == GuiItems.BACK_SLOT) {
            if (holder.folder().isEmpty())
                mainMenuOpener.accept(player);
            else
                openBrowser(player, LocationBrowserModel.parent(holder.folder()), 0);
            return;
        }
        if (slot == GuiItems.PREVIOUS_SLOT) {
            openBrowser(player, holder.folder(), holder.page() - 1);
            return;
        }
        if (slot == GuiItems.NEXT_SLOT) {
            openBrowser(player, holder.folder(), holder.page() + 1);
            return;
        }
        if (slot == GuiItems.CREATE_SLOT) {
            createScene(player, holder.folder(), scene -> openEditor(player, scene.getKey()),
                    () -> openBrowser(player, holder.folder(), holder.page()));
            return;
        }
        List<BrowserEntry> entries = browserEntries(holder.folder(), filter);
        int index = holder.page() * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index < 0 || index >= entries.size())
            return;
        BrowserEntry entry = entries.get(index);
        if (entry.scene() == null) {
            openBrowser(player, entry.path(), 0);
        } else if (click.getClick() == ClickType.SHIFT_RIGHT) {
            openDelete(player, entry.scene().getKey());
        } else {
            openEditor(player, entry.scene().getKey());
        }
    }

    // ------------------------------------------------------------- editor

    public void openEditor(Player player, String sceneKey) {
        Scene scene = scenes.find(sceneKey).orElse(null);
        if (scene == null) {
            player.sendMessage(UiText.error("That scene no longer exists."));
            openBrowser(player, "", 0);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new EditorHolder(scene.getKey()), 54,
                UiText.manageTitle(scene.getDisplayName()));
        List<String> summary = new ArrayList<>();
        summary.add(LegacyText.DARK_GRAY + "Key: " + scene.getKey());
        summary.add(LegacyText.GRAY + "Status: " + statusText(scene));
        if (sceneService != null)
            sceneService.lastSkip(scene.getKey())
                    .ifPresent(reason -> summary.add(LegacyText.GOLD + "Last skipped: " + reason));
        summary.addAll(chainLore(scene));
        summary.add("");
        summary.add(GuiItems.hint("Middle-click", "use the item in your hand as icon"));
        inventory.setItem(SUMMARY_SLOT,
                GuiItems.item(sceneIcon(scene), LegacyText.GOLD + scene.getDisplayName(), summary));
        inventory.setItem(10, GuiItems.sectionHeader(Material.LIGHT_BLUE_STAINED_GLASS_PANE, LegacyText.AQUA, "Scene",
                "Name and description"));
        inventory.setItem(12,
                GuiItems.sectionHeader(Material.LIME_STAINED_GLASS_PANE, LegacyText.GREEN, "When", "What starts it"));
        inventory.setItem(14, GuiItems.sectionHeader(Material.PURPLE_STAINED_GLASS_PANE, LegacyText.LIGHT_PURPLE,
                "Cast & Ending", "Who plays and when it ends"));
        inventory.setItem(16, GuiItems.sectionHeader(Material.RED_STAINED_GLASS_PANE, LegacyText.RED, "Playback",
                "Test it, chance, and cooldown"));
        inventory.setItem(NAME_SLOT, GuiItems.item(Material.NAME_TAG, "Name",
                List.of(LegacyText.WHITE + scene.getDisplayName(), "", GuiItems.hint("Click", "rename"))));
        List<String> descriptionLore = new ArrayList<>();
        if (scene.getDescription().isBlank())
            descriptionLore.add(LegacyText.DARK_GRAY + "No description");
        else
            descriptionLore.addAll(descriptionLines(scene, 8));
        descriptionLore.add("");
        descriptionLore.add(LegacyText.GRAY + "NPCs controlled by AI get it as");
        descriptionLore.add(LegacyText.GRAY + "context while they play this scene.");
        descriptionLore.add("");
        descriptionLore.add(GuiItems.hint("Click", "edit"));
        descriptionLore.add(LegacyText.DARK_GRAY + "Empty the field to remove it");
        inventory.setItem(DESCRIPTION_SLOT, GuiItems.item(Material.WRITABLE_BOOK, "Description", descriptionLore));
        inventory.setItem(AI_BEHAVIOUR_SLOT,
                GuiItems.toggle(Material.OXIDIZED_COPPER_GOLEM_STATUE, Material.GRAY_DYE, "Trigger AI Behaviour",
                        scene.triggersAiBehaviour(), "the NPCs' lines may start AI replies",
                        "only AI Trigger actions of the scene",
                        List.of(LegacyText.GRAY + "When off, NPCs in this scene do not",
                                LegacyText.GRAY + "answer other NPCs, and their lines start",
                                LegacyText.GRAY + "no AI replies. AI Trigger actions in",
                                LegacyText.GRAY + "beats, wrap-up, and While Active rows",
                                LegacyText.GRAY + "still run; the preset's own wait.",
                                LegacyText.GRAY + "Replies to player chat still work.")));
        inventory.setItem(ENABLED_SLOT, GuiItems.toggle(Material.LIME_DYE, Material.GRAY_DYE, "Triggers",
                scene.isEnabled(), "triggers start the scene", "only manual starts work", List.of()));
        inventory.setItem(TRIGGERS_SLOT, GuiItems.item(Material.CLOCK, "Starts When", triggerLore(scene)));
        inventory.setItem(SCRIPT_SLOT, GuiItems.item(Material.WRITABLE_BOOK, "Script", scriptLore(scene)));
        List<GuiItems.Option> endings = java.util.Arrays.stream(SceneEnding.values())
                .map(ending -> new GuiItems.Option(endingMaterial(ending), ending.displayName(), ending.summary()))
                .toList();
        inventory.setItem(ENDING_SLOT, GuiItems.cycle("Ends", endings, scene.getEnding().ordinal(),
                List.of("Then every role runs its wrap-up and", "returns to its normal routine")));
        inventory.setItem(END_SETTING_SLOT, endSettingItem(scene));
        inventory.setItem(RUN_SLOT, runItem(scene));
        inventory.setItem(CHANCE_SLOT,
                numericItem(Material.RABBIT_FOOT, "Chance", scene.getChancePercent() + "%",
                        List.of(LegacyText.GRAY + "How likely a trigger really starts it.",
                                LegacyText.GRAY + "Manual starts always run.")));
        inventory.setItem(COOLDOWN_SLOT,
                numericItem(Material.HOPPER, "Cooldown", seconds(scene.getCooldownSeconds()),
                        List.of(LegacyText.GRAY + "After it ends, triggers wait this long",
                                LegacyText.GRAY + "before they may start it again.")));
        inventory.setItem(GuiItems.BACK_SLOT, GuiItems.back("the scene list"));
        inventory.setItem(DELETE_SLOT,
                GuiItems.item(Material.LAVA_BUCKET, "Delete Scene",
                        List.of(LegacyText.GRAY + "Removes the scene and its roles", "",
                                GuiItems.danger("Click", "delete (asks first)"))));
        GuiLayout.fillRow(inventory, 0);
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void handleEditorClick(InventoryClickEvent click, Player player, EditorHolder holder) {
        Scene scene = scenes.find(holder.sceneKey()).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        Runnable reopen = () -> openEditor(player, scene.getKey());
        switch (click.getRawSlot()) {
            case SUMMARY_SLOT -> {
                if (click.getClick() != ClickType.MIDDLE && click.getClick() != ClickType.SHIFT_LEFT)
                    return;
                scene.setIcon(player.getInventory().getItemInMainHand());
                scenes.save(scene);
                player.sendMessage(UiText.info(scene.getIcon() == null ? "Scene icon cleared." : "Scene icon set."));
                reopen.run();
            }
            case NAME_SLOT -> chatInput.request(player, "Enter the scene name:", value -> {
                scene.setDisplayName(value);
                scenes.save(scene);
                reopen.run();
            }, reopen);
            case DESCRIPTION_SLOT -> openDescriptionDialog(player, scene);
            case AI_BEHAVIOUR_SLOT -> {
                scene.setTriggersAiBehaviour(!scene.triggersAiBehaviour());
                scenes.save(scene);
                GuiItems.click(player);
                reopen.run();
            }
            case ENABLED_SLOT -> {
                scene.setEnabled(!scene.isEnabled());
                scenes.save(scene);
                GuiItems.click(player);
                reopen.run();
            }
            case TRIGGERS_SLOT -> openTriggers(player, scene.getKey());
            case SCRIPT_SLOT -> openScript(player, scene.getKey());
            case ENDING_SLOT -> {
                int direction = GuiItems.cycleDirection(click.getClick());
                if (direction == 0)
                    return;
                scene.setEnding(GuiItems.step(scene.getEnding(), direction));
                scenes.save(scene);
                GuiItems.click(player);
                reopen.run();
            }
            case END_SETTING_SLOT -> {
                if (scene.getEnding() == SceneEnding.DURATION) {
                    adjust(click, player, scene, 30, value -> scene.setDurationSeconds(value),
                            scene.getDurationSeconds(), "Enter how many minutes the scene lasts:", 60, reopen);
                } else if (scene.getEnding() == SceneEnding.TIME_OF_DAY) {
                    int direction = GuiItems.cycleDirection(click.getClick());
                    if (direction == 0)
                        return;
                    scene.setEndPeriod(GuiItems.step(scene.getEndPeriod(), direction));
                    scenes.save(scene);
                    GuiItems.click(player);
                    reopen.run();
                }
            }
            case RUN_SLOT -> {
                if (sceneService == null)
                    return;
                if (sceneService.isRunning(scene.getKey()))
                    sceneService.stop(scene.getKey(), player);
                else
                    sceneService.startManually(scene.getKey(), player, player);
                reopen.run();
            }
            case CHANCE_SLOT -> adjust(click, player, scene, 5, value -> scene.setChancePercent(value),
                    scene.getChancePercent(), "Enter the chance in percent (1-100):", 1, reopen);
            case COOLDOWN_SLOT -> adjust(click, player, scene, 30, value -> scene.setCooldownSeconds(value),
                    scene.getCooldownSeconds(), "Enter the cooldown in minutes:", 60, reopen);
            case GuiItems.BACK_SLOT -> {
                int slash = scene.getKey().lastIndexOf('/');
                openBrowser(player, slash < 0 || presetFilter(player) != null ? "" : scene.getKey().substring(0, slash),
                        0);
            }
            case DELETE_SLOT -> openDelete(player, scene.getKey());
            default -> {
            }
        }
    }

    /**
     * Edits the description in a multi-line text box. Saving reopens the editor;
     * cancelling or Escape just closes the dialog.
     */
    private void openDescriptionDialog(Player player, Scene scene) {
        String sceneKey = scene.getKey();
        String value = scene.getDescription();
        ActionButton save = ActionButton.builder(Component.text("Save", NamedTextColor.GREEN))
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player submitting)
                            || !submitting.getUniqueId().equals(player.getUniqueId()))
                        return;
                    String input = view.getText(DESCRIPTION_INPUT_KEY);
                    if (input == null)
                        return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!submitting.isOnline())
                            return;
                        scenes.find(sceneKey).ifPresent(current -> {
                            current.setDescription(input);
                            scenes.save(current);
                        });
                        openEditor(submitting, sceneKey);
                    });
                }, ClickCallback.Options.builder().uses(1).build())).build();
        ActionButton cancel = ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player cancelling && cancelling.getUniqueId().equals(player.getUniqueId()))
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (cancelling.isOnline())
                                openEditor(cancelling, sceneKey);
                        });
                }, ClickCallback.Options.builder().uses(1).build())).build();
        Dialog dialog = Dialog.create(builder -> builder.empty().base(DialogBase
                .builder(Component.text("Scene Description: " + scene.getDisplayName())).canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Component.text(
                        "What happens in this scene and why. NPCs controlled by AI read it as context while"
                                + " they play the scene, so describe the situation, not instructions.",
                        NamedTextColor.GRAY), 400)))
                .inputs(List.of(DialogInput.text(DESCRIPTION_INPUT_KEY, Component.text("Description")).width(400)
                        .initial(value).maxLength(Math.max(Scene.MAX_DESCRIPTION_LENGTH, value.length()))
                        .multiline(TextDialogInput.MultilineOptions.create(null, 160)).build()))
                .build()).type(DialogType.confirmation(save, cancel)));
        player.closeInventory();
        player.showDialog(dialog);
    }

    /** The description wrapped for item lore, cut off after {@code maxLines}. */
    private static List<String> descriptionLines(Scene scene, int maxLines) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : scene.getDescription().split("\\R"))
            TextUtil.wrap(paragraph, 40).forEach(line -> lines.add(LegacyText.WHITE + line));
        if (lines.size() <= maxLines)
            return lines;
        List<String> shown = new ArrayList<>(lines.subList(0, maxLines));
        shown.add(LegacyText.DARK_GRAY + "…");
        return shown;
    }

    /**
     * Numeric control: left/right steps down/up, shift five steps, middle-click
     * types a value. {@code typedUnit} converts a typed number into stored units
     * (60 for minutes stored as seconds). {@code reopen} shows the menu again.
     */
    private void adjust(InventoryClickEvent click, Player player, Scene scene, int step, Consumer<Integer> setter,
            int current, String prompt, int typedUnit, Runnable reopen) {
        if (click.getClick() == ClickType.MIDDLE) {
            chatInput.request(player, prompt, value -> {
                try {
                    double parsed = Double.parseDouble(value.trim().replace(',', '.'));
                    if (!Double.isFinite(parsed))
                        throw new NumberFormatException();
                    setter.accept((int) Math.round(parsed * typedUnit));
                    scenes.save(scene);
                } catch (NumberFormatException exception) {
                    player.sendMessage(UiText.error("Enter a number, for example 5."));
                }
                reopen.run();
            }, reopen);
            return;
        }
        int direction = NumericControl.direction(click.getClick());
        if (direction == 0)
            return;
        int amount = step * (click.isShiftClick() ? 5 : 1);
        setter.accept(current + direction * amount);
        scenes.save(scene);
        GuiItems.click(player);
        reopen.run();
    }

    private ItemStack numericItem(Material material, String name, String value, List<String> description) {
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.WHITE + value);
        lore.addAll(description);
        lore.addAll(NumericControl.lore());
        return GuiItems.item(material, name + ": " + value, lore);
    }

    private ItemStack endSettingItem(Scene scene) {
        return switch (scene.getEnding()) {
            case DURATION -> numericItem(Material.CLOCK, "Lasts", seconds(scene.getDurationSeconds()),
                    List.of(LegacyText.GRAY + "How long the scene runs before it ends"));
            case TIME_OF_DAY -> {
                List<GuiItems.Option> periods = java.util.Arrays.stream(DayPeriod.values())
                        .map(period -> new GuiItems.Option(Material.SUNFLOWER, periodName(period),
                                String.format("%02d:00", period.startHour())))
                        .toList();
                yield GuiItems.cycle("Ends At", periods, scene.getEndPeriod().ordinal(), List.of());
            }
            case BEATS_FINISHED -> GuiItems.item(Material.GRAY_DYE, "No Setting Needed",
                    List.of(LegacyText.GRAY + "Ends once the last beat is done",
                            LegacyText.GRAY + "(at the latest after 30 min). With",
                            LegacyText.GRAY + "another ending the NPCs stay in the",
                            LegacyText.GRAY + "scene after the last beat, so While",
                            LegacyText.GRAY + "Active reactions keep working."));
            case WEATHER_CHANGES -> GuiItems.item(Material.GRAY_DYE, "No Setting Needed",
                    List.of(LegacyText.GRAY + "Ends when rain, snow, or thunder starts",
                            LegacyText.GRAY + "or stops in the world the scene plays in."));
        };
    }

    private ItemStack runItem(Scene scene) {
        boolean running = sceneService != null && sceneService.isRunning(scene.getKey());
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.GRAY + "Status: " + statusText(scene));
        lore.add(LegacyText.DARK_GRAY + "/bf scene " + (running ? "stop " : "start ") + scene.getKey());
        lore.add("");
        lore.add(running
                ? GuiItems.hint("Click", "end it (wrap-up runs; click again to end at once)")
                : GuiItems.hint("Click", "start it now"));
        return GuiItems.item(running ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                running ? "Stop Scene" : "Start Scene Now", lore);
    }

    private List<String> triggerLore(Scene scene) {
        List<String> lore = new ArrayList<>();
        if (scene.getTriggers().isEmpty()) {
            lore.add(LegacyText.GRAY + "No triggers: starts only by command");
            lore.add(LegacyText.GRAY + "or a Start Scene action");
        } else {
            scene.getTriggers().forEach(trigger -> lore.add(LegacyText.WHITE + "• " + trigger.displayName()));
            if (!scene.isEnabled())
                lore.add(LegacyText.RED + "Triggers are switched off");
        }
        lore.add("");
        lore.add(GuiItems.hint("Click", "choose triggers"));
        return lore;
    }

    private List<String> scriptLore(Scene scene) {
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.GRAY + "Who takes part and what they do, beat by beat");
        if (scene.getRoles().isEmpty()) {
            lore.add(LegacyText.RED + "No roles yet: add the NPCs that take part");
        } else {
            lore.add(LegacyText.GRAY + "Roles:");
            for (SceneRole role : scene.getRoles().stream().limit(8).toList())
                lore.add(LegacyText.WHITE + "• " + presetName(role));
            if (scene.getRoles().size() > 8)
                lore.add(LegacyText.DARK_GRAY + "… and " + (scene.getRoles().size() - 8) + " more");
        }
        lore.add(LegacyText.GRAY + "Beats: " + LegacyText.WHITE + scene.getBeats().size());
        lore.add("");
        lore.add(GuiItems.hint("Click", "edit roles and beats"));
        return lore;
    }

    // ------------------------------------------------------------ triggers

    private void openTriggers(Player player, String sceneKey) {
        Scene scene = scenes.find(sceneKey).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new TriggersHolder(scene.getKey()), 54,
                UiText.title("Starts When", scene.getDisplayName()));
        inventory.setItem(9, GuiItems.sectionHeader(Material.YELLOW_STAINED_GLASS_PANE, LegacyText.YELLOW,
                "Time of Day", "When this part of the day begins"));
        inventory.setItem(27, GuiItems.sectionHeader(Material.LIGHT_BLUE_STAINED_GLASS_PANE, LegacyText.AQUA, "Weather",
                "When the weather changes"));
        inventory.setItem(36, GuiItems.sectionHeader(Material.LIME_STAINED_GLASS_PANE, LegacyText.GREEN, "Area",
                "When someone arrives"));
        TRIGGER_SLOTS.forEach((slot, trigger) -> {
            if (trigger.kind() == SceneTrigger.Kind.AREA) {
                inventory.setItem(slot, areaTriggerItem(scene, trigger));
                return;
            }
            List<String> description = new ArrayList<>();
            if (trigger == SceneTrigger.RAIN || trigger == SceneTrigger.SNOW)
                description.add(LegacyText.GRAY + "The biome the scene's NPCs stand in decides");
            inventory.setItem(slot, GuiItems.toggle(triggerMaterial(trigger), triggerMaterial(trigger),
                    trigger.displayName(), scene.hasTrigger(trigger), trigger.summary(), "ignored", description));
        });
        if (scene.hasTrigger(SceneTrigger.PLAYER_ENTERS))
            inventory.setItem(TRIGGER_RADIUS_SLOT,
                    numericItem(Material.COMPASS, "Area Radius", scene.getAreaRadius() + " blocks",
                            List.of(LegacyText.GRAY + "How far around the area location",
                                    LegacyText.GRAY + "players count as inside")));
        inventory.setItem(4,
                GuiItems.item(Material.KNOWLEDGE_BOOK, "Triggers",
                        List.of(LegacyText.GRAY + "Turn on everything that should start",
                                LegacyText.GRAY + "this scene. Chance and cooldown apply.", "",
                                LegacyText.GRAY + "Time and weather come from the world",
                                LegacyText.GRAY + "the scene's NPCs are in.", "",
                                LegacyText.GRAY + "Manual starts always work:",
                                LegacyText.WHITE + "/bf scene start " + scene.getKey(),
                                LegacyText.GRAY + "or the Start Scene action of any NPC.")));
        inventory.setItem(GuiItems.BACK_SLOT, GuiItems.back("the scene editor"));
        GuiLayout.fillRow(inventory, 0);
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void handleTriggersClick(InventoryClickEvent click, Player player, TriggersHolder holder) {
        Scene scene = scenes.find(holder.sceneKey()).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        if (click.getRawSlot() == GuiItems.BACK_SLOT) {
            openEditor(player, scene.getKey());
            return;
        }
        if (click.getRawSlot() == TRIGGER_RADIUS_SLOT && scene.hasTrigger(SceneTrigger.PLAYER_ENTERS)) {
            adjust(click, player, scene, 4, value -> scene.setAreaRadius(value), scene.getAreaRadius(),
                    "Enter the area radius in blocks:", 1, () -> openTriggers(player, scene.getKey()));
            return;
        }
        SceneTrigger trigger = TRIGGER_SLOTS.get(click.getRawSlot());
        if (trigger == null)
            return;
        if (trigger.kind() == SceneTrigger.Kind.AREA) {
            // Turning it on (or right-clicking) asks for the area first; the
            // picker switches the trigger on once a location is chosen.
            if (!scene.hasTrigger(trigger) || click.isRightClick()) {
                openAreaPicker(player, scene.getKey(), "", 0);
                return;
            }
            scene.setTrigger(trigger, false);
            scene.setAreaLocationKey(null);
            scenes.save(scene);
            GuiItems.click(player);
            openTriggers(player, scene.getKey());
            return;
        }
        scene.setTrigger(trigger, !scene.hasTrigger(trigger));
        scenes.save(scene);
        GuiItems.click(player);
        openTriggers(player, scene.getKey());
    }

    private ItemStack areaTriggerItem(Scene scene, SceneTrigger trigger) {
        boolean active = scene.hasTrigger(trigger);
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.GRAY + trigger.summary());
        if (active) {
            NamedLocation location = scene.hasArea() ? locations.find(scene.getAreaLocationKey()).orElse(null) : null;
            lore.add(LegacyText.GRAY + "Area: "
                    + (location == null
                            ? LegacyText.RED + "missing location '" + scene.getAreaLocationKey() + "'"
                            : LegacyText.WHITE + location.displayName() + LegacyText.GRAY + " · "
                                    + location.location().display()));
            lore.add(LegacyText.GRAY + "Radius: " + LegacyText.WHITE + scene.getAreaRadius() + " blocks");
            lore.add("");
            lore.add(GuiItems.hint("Click", "turn off"));
            lore.add(GuiItems.hint("Right-click", "choose another location"));
        } else {
            lore.add("");
            lore.add(GuiItems.hint("Click", "choose the area's location to turn on"));
        }
        ItemStack item = GuiItems.item(triggerMaterial(trigger),
                (active ? LegacyText.GREEN : LegacyText.RED) + trigger.displayName() + ": " + (active ? "On" : "Off"),
                lore);
        return active ? GuiItems.glint(item) : item;
    }

    // -------------------------------------------------------------- script

    /**
     * The scene's script: one row per beat, one column per role. Row 0 holds the
     * role headers, rows 1-4 four beats; both page when there are more.
     */
    private void openScript(Player player, String sceneKey, int requestedBeatPage, int requestedRolePage) {
        Scene scene = scenes.find(sceneKey).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        List<SceneRole> roles = scene.getRoles();
        List<SceneBeat> beats = scene.getBeats();
        int columns = roles.size() + (roles.size() < Scene.MAX_ROLES ? 1 : 0);
        int rolePages = Math.max(1, (columns + SCRIPT_COLUMNS - 1) / SCRIPT_COLUMNS);
        int rolePage = Math.clamp(requestedRolePage, 0, rolePages - 1);
        int beatPages = Math.max(1, (beats.size() + SCRIPT_ROWS - 1) / SCRIPT_ROWS);
        int beatPage = Math.clamp(requestedBeatPage, 0, beatPages - 1);
        scriptViews.put(player.getUniqueId(), new ScriptView(scene.getKey(), beatPage, rolePage));
        Inventory inventory = Bukkit.createInventory(new ScriptHolder(scene.getKey(), beatPage, rolePage), 54,
                UiText.title("Script", scene.getDisplayName()));
        String playing = sceneService == null ? null : sceneService.currentBeat(scene.getKey()).orElse(null);
        inventory.setItem(0, scriptInfoItem(scene));
        for (int column = 0; column < SCRIPT_COLUMNS; column++) {
            int roleIndex = rolePage * SCRIPT_COLUMNS + column;
            if (roleIndex < roles.size())
                inventory.setItem(column + 1, roleHeaderItem(scene, roles.get(roleIndex)));
            else if (roleIndex == roles.size() && roles.size() < Scene.MAX_ROLES)
                inventory.setItem(column + 1, GuiItems.create("Add Role", "Choose the NPC preset that plays it"));
        }
        for (int row = 0; row < SCRIPT_ROWS; row++) {
            int beatIndex = beatPage * SCRIPT_ROWS + row;
            if (beatIndex >= beats.size())
                break;
            SceneBeat beat = beats.get(beatIndex);
            int first = (row + 1) * 9;
            inventory.setItem(first, beatItem(scene, beat, beatIndex, beat.getId().equals(playing), true));
            for (int column = 0; column < SCRIPT_COLUMNS; column++) {
                int roleIndex = rolePage * SCRIPT_COLUMNS + column;
                if (roleIndex < roles.size())
                    inventory.setItem(first + column + 1, cellItem(roles.get(roleIndex), beat, beatIndex));
            }
        }
        if (beatPage > 0)
            inventory.setItem(GuiItems.PREVIOUS_SLOT, GuiItems.item(Material.ARROW, "Earlier Beats",
                    List.of(LegacyText.GRAY + "Page " + beatPage + " of " + beatPages)));
        if (rolePage > 0)
            inventory.setItem(SCRIPT_ROLES_LEFT_SLOT, GuiItems.item(Material.SPECTRAL_ARROW, "Previous Roles",
                    List.of(LegacyText.GRAY + "Roles page " + rolePage + " of " + rolePages)));
        inventory.setItem(GuiItems.BACK_SLOT, GuiItems.back("the scene editor"));
        if (rolePage + 1 < rolePages)
            inventory.setItem(SCRIPT_ROLES_RIGHT_SLOT, GuiItems.item(Material.SPECTRAL_ARROW, "More Roles",
                    List.of(LegacyText.GRAY + "Roles page " + (rolePage + 2) + " of " + rolePages)));
        if (scene.canAddBeat())
            inventory.setItem(GuiItems.CREATE_SLOT, GuiItems.create("Add Beat", "Adds a step after the last beat"));
        if (beatPage + 1 < beatPages)
            inventory.setItem(GuiItems.NEXT_SLOT, GuiItems.item(Material.ARROW, "Later Beats",
                    List.of(LegacyText.GRAY + "Page " + (beatPage + 2) + " of " + beatPages)));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private ItemStack scriptInfoItem(Scene scene) {
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.GRAY + "Each row is a beat: one step of the scene.");
        lore.add(LegacyText.GRAY + "Every role plays its actions of a beat at");
        lore.add(LegacyText.GRAY + "the same time; then the next beat starts.");
        lore.add(LegacyText.GRAY + "A role without actions in a beat holds still.");
        lore.add("");
        lore.add(LegacyText.GRAY + "Columns are the roles. Click a cell to edit");
        lore.add(LegacyText.GRAY + "what that NPC does in that beat.");
        lore.add("");
        lore.add(LegacyText.GRAY + "Beats: " + LegacyText.WHITE + scene.getBeats().size() + LegacyText.GRAY
                + " · Roles: " + LegacyText.WHITE + scene.getRoles().size());
        lore.add(LegacyText.GRAY + "Status: " + statusText(scene));
        return GuiItems.item(Material.KNOWLEDGE_BOOK, "Script", lore);
    }

    private ItemStack roleHeaderItem(Scene scene, SceneRole role) {
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.GRAY + "Every free instance of this NPC plays it");
        if (scene.roleOf(role.getPresetKey(), role.getId()).isPresent()) {
            lore.add(LegacyText.RED + "This NPC has two roles: the scene cannot");
            lore.add(LegacyText.RED + "start until one of them is removed");
        }
        lore.add(LegacyText.GRAY + "Acts in: " + LegacyText.WHITE + scene.beatsActedIn(role.getId()) + " of "
                + scene.getBeats().size() + " beats");
        lore.add(LegacyText.GRAY + "Wrap-up actions: " + LegacyText.WHITE + role.getWrapUp().size());
        List<String> reactions = role.getReactions().keySet().stream().map(BehaviourEvent::displayName).toList();
        lore.add(LegacyText.GRAY + "While active: " + LegacyText.WHITE
                + (reactions.isEmpty() ? "normal behaviour" : String.join(", ", reactions)));
        lore.add("");
        lore.add(GuiItems.hint("Click", "edit its wrap-up and While Active reactions"));
        lore.add(GuiItems.danger("Shift-right-click", "remove role"));
        return roleHead(role, LegacyText.GOLD + presetName(role), lore);
    }

    /** {@code inScript} adds the click hints that only work in the script grid. */
    private ItemStack beatItem(Scene scene, SceneBeat beat, int beatIndex, boolean playing, boolean inScript) {
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.GRAY + "Next beat: " + LegacyText.WHITE
                + SceneBeatText.advance(scene, beat, this::plainPresetName));
        List<String> acting = beat.actingRoles().stream().map(scene::findRole).flatMap(java.util.Optional::stream)
                .map(this::plainPresetName).toList();
        lore.add(LegacyText.GRAY + "Acting: " + LegacyText.WHITE
                + (acting.isEmpty() ? "nobody yet" : String.join(", ", acting)));
        if (playing)
            lore.add(LegacyText.GREEN + "▶ Playing now");
        if (inScript) {
            lore.add("");
            lore.add(GuiItems.hint("Click", "when it moves on, duplicate, delete"));
            if (beatIndex > 0)
                lore.add(GuiItems.hint("Shift-left-click", "move up"));
            if (beatIndex + 1 < scene.getBeats().size())
                lore.add(GuiItems.hint("Shift-right-click", "move down"));
        }
        ItemStack item = GuiItems.item(new ItemStack(advanceMaterial(beat.getAdvance())),
                LegacyText.AQUA + "Beat " + (beatIndex + 1), lore);
        item.setAmount(Math.clamp(beatIndex + 1, 1, 64));
        return playing ? GuiItems.glint(item) : item;
    }

    private ItemStack cellItem(SceneRole role, SceneBeat beat, int beatIndex) {
        List<dev.blockfolk.model.BehaviourAction> actions = beat.getActions(role.getId());
        String name = plainPresetName(role);
        if (actions.isEmpty())
            return GuiItems.item(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE),
                    LegacyText.GRAY + name + " holds still",
                    List.of(LegacyText.DARK_GRAY + "No actions in beat " + (beatIndex + 1), "",
                            GuiItems.hint("Click", "add actions")));
        List<String> lore = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++)
            lore.add(LegacyText.GRAY + (index + 1) + ". " + LegacyText.WHITE
                    + (guiService == null
                            ? actions.get(index).type().displayName()
                            : guiService.actionSummary(actions.get(index))));
        lore.add("");
        lore.add(GuiItems.hint("Click", "edit its actions"));
        // No item count: the beat number in the first column is what the eye
        // should find; the tooltip lists the actions.
        return roleHead(role, LegacyText.YELLOW + name + LegacyText.GRAY + " · Beat " + (beatIndex + 1), lore);
    }

    private ItemStack roleHead(SceneRole role, String name, List<String> lore) {
        NpcDefinition preset = role.getPresetKey() == null ? null : definitions.find(role.getPresetKey()).orElse(null);
        ItemStack icon = GuiItems.item(new ItemStack(preset == null ? Material.SKELETON_SKULL : Material.PLAYER_HEAD),
                name, lore);
        return preset == null ? icon : NpcHeadUtil.applySkin(icon, preset);
    }

    private void handleScriptClick(InventoryClickEvent click, Player player, ScriptHolder holder) {
        Scene scene = scenes.find(holder.sceneKey()).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        int slot = click.getRawSlot();
        Runnable reopen = () -> openScript(player, scene.getKey(), holder.beatPage(), holder.rolePage());
        switch (slot) {
            case GuiItems.PREVIOUS_SLOT -> openScript(player, scene.getKey(), holder.beatPage() - 1, holder.rolePage());
            case GuiItems.NEXT_SLOT -> openScript(player, scene.getKey(), holder.beatPage() + 1, holder.rolePage());
            case SCRIPT_ROLES_LEFT_SLOT -> openScript(player, scene.getKey(), holder.beatPage(), holder.rolePage() - 1);
            case SCRIPT_ROLES_RIGHT_SLOT ->
                openScript(player, scene.getKey(), holder.beatPage(), holder.rolePage() + 1);
            case GuiItems.BACK_SLOT -> openEditor(player, scene.getKey());
            case GuiItems.CREATE_SLOT -> {
                if (!scene.canAddBeat())
                    return;
                scene.addBeat();
                scenes.save(scene);
                GuiItems.click(player);
                // Show the page with the new beat.
                openScript(player, scene.getKey(), (scene.getBeats().size() - 1) / SCRIPT_ROWS, holder.rolePage());
            }
            default -> {
                if (slot >= 45)
                    return;
                int row = slot / 9;
                int column = slot % 9;
                List<SceneRole> roles = scene.getRoles();
                int roleIndex = holder.rolePage() * SCRIPT_COLUMNS + column - 1;
                if (row == 0) {
                    if (column == 0)
                        return;
                    if (roleIndex == roles.size() && roles.size() < Scene.MAX_ROLES)
                        openPresetPicker(player, scene.getKey(), null, 0);
                    else if (roleIndex < roles.size())
                        handleRoleHeaderClick(click, player, scene, roles.get(roleIndex), reopen);
                    return;
                }
                List<SceneBeat> beats = scene.getBeats();
                int beatIndex = holder.beatPage() * SCRIPT_ROWS + row - 1;
                if (beatIndex >= beats.size())
                    return;
                if (column == 0) {
                    int direction = switch (click.getClick()) {
                        case SHIFT_LEFT -> -1;
                        case SHIFT_RIGHT -> 1;
                        default -> 0;
                    };
                    if (direction == 0)
                        openBeat(player, scene.getKey(), beats.get(beatIndex).getId());
                    else
                        moveBeat(player, scene, beats.get(beatIndex), direction, holder.rolePage());
                } else if (roleIndex < roles.size() && guiService != null) {
                    guiService.openSceneBeat(player, scene.getKey(), roles.get(roleIndex).getId(),
                            beats.get(beatIndex).getId());
                }
            }
        }
    }

    /** Moves a beat one row up or down and keeps it in view. */
    private void moveBeat(Player player, Scene scene, SceneBeat beat, int direction, int rolePage) {
        if (sceneService != null && sceneService.isRunning(scene.getKey())) {
            player.sendMessage(UiText.warning("Stop the scene before moving beats."));
            return;
        }
        if (!scene.moveBeat(beat.getId(), direction))
            return;
        scenes.save(scene);
        GuiItems.click(player);
        openScript(player, scene.getKey(), scene.beatIndex(beat.getId()) / SCRIPT_ROWS, rolePage);
    }

    private void handleRoleHeaderClick(InventoryClickEvent click, Player player, Scene scene, SceneRole role,
            Runnable reopen) {
        if (click.getClick() != ClickType.SHIFT_RIGHT) {
            if (guiService != null)
                guiService.openSceneRole(player, scene.getKey(), role.getId(), 0);
            return;
        }
        if (sceneService != null && sceneService.isRunning(scene.getKey())) {
            player.sendMessage(UiText.warning("Stop the scene before removing roles."));
            return;
        }
        scene.removeRole(role.getId());
        scenes.save(scene);
        player.sendMessage(UiText.success("Removed " + presetName(role) + " and its actions from the scene."));
        reopen.run();
    }

    // ---------------------------------------------------------------- beat

    /**
     * Settings of one beat: when it moves on, and copying or deleting it.
     */
    private void openBeat(Player player, String sceneKey, String beatId) {
        Scene scene = scenes.find(sceneKey).orElse(null);
        SceneBeat beat = scene == null ? null : scene.findBeat(beatId).orElse(null);
        if (beat == null) {
            if (scene == null)
                openBrowser(player, "", 0);
            else
                openScript(player, scene.getKey());
            return;
        }
        int index = scene.beatIndex(beatId);
        Inventory inventory = Bukkit.createInventory(new BeatHolder(scene.getKey(), beatId), 27,
                UiText.title("Beat " + (index + 1), scene.getDisplayName()));
        inventory.setItem(4, beatItem(scene, beat, index, false, false));
        inventory.setItem(9, GuiItems.sectionHeader(Material.LIGHT_BLUE_STAINED_GLASS_PANE, LegacyText.AQUA,
                "Next Beat Starts", "Choose one; running actions are cut off"));
        for (int option = 0; option < BEAT_ADVANCE_SLOTS.length; option++) {
            SceneBeatAdvance advance = SceneBeatAdvance.values()[option];
            boolean selected = beat.getAdvance() == advance;
            List<String> lore = new ArrayList<>();
            lore.add(LegacyText.GRAY + advance.summary());
            lore.add("");
            lore.add(selected ? LegacyText.GREEN + "Selected" : GuiItems.hint("Click", "select"));
            ItemStack item = GuiItems.item(new ItemStack(advanceMaterial(advance)),
                    (selected ? LegacyText.GREEN : LegacyText.GOLD) + advance.displayName(), lore);
            inventory.setItem(BEAT_ADVANCE_SLOTS[option], selected ? GuiItems.glint(item) : item);
        }
        inventory.setItem(BEAT_SETTING_SLOT, beatSettingItem(scene, beat));
        inventory.setItem(BEAT_COPY_SLOT,
                GuiItems.item(Material.PAPER, "Duplicate",
                        List.of(LegacyText.GRAY + "Inserts a copy right after this beat", "",
                                scene.canAddBeat()
                                        ? GuiItems.hint("Click", "duplicate")
                                        : LegacyText.RED + "The scene has the most beats it can hold")));
        inventory.setItem(BEAT_BACK_SLOT, GuiItems.back("the script"));
        inventory.setItem(BEAT_DELETE_SLOT,
                GuiItems.item(Material.LAVA_BUCKET, "Delete Beat",
                        List.of(LegacyText.GRAY + "Removes this beat and every role's",
                                LegacyText.GRAY + "actions in it", "", GuiItems.danger("Shift-click", "delete"))));
        GuiLayout.fillRow(inventory, 0);
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private ItemStack beatSettingItem(Scene scene, SceneBeat beat) {
        return switch (beat.getAdvance()) {
            case AFTER_TIME -> numericItem(Material.CLOCK, "Moves On After", SceneBeatText.seconds(beat.getSeconds()),
                    List.of(LegacyText.GRAY + "Counted from the start of the beat"));
            case ROLE_DONE -> {
                List<SceneRole> roles = scene.getRoles();
                if (roles.isEmpty())
                    yield GuiItems.item(Material.GRAY_DYE, "No Roles Yet",
                            List.of(LegacyText.GRAY + "Add roles in the script first"));
                List<GuiItems.Option> options = roles.stream().map(role -> new GuiItems.Option(Material.PLAYER_HEAD,
                        plainPresetName(role), beat.hasActions(role.getId()) ? "" : "no actions in this beat"))
                        .toList();
                int selected = Math.max(0, indexOfRole(roles, beat.getAdvanceRoleId()));
                yield GuiItems.cycle("Waits For", options, selected,
                        List.of("The other roles' unfinished", "actions are cut off"));
            }
            case EVERYONE_DONE -> GuiItems.item(Material.GRAY_DYE, "No Setting Needed",
                    List.of(LegacyText.GRAY + "Moves on once every role finished",
                            LegacyText.GRAY + "its actions in this beat. Dialog lines",
                            LegacyText.GRAY + "count as done once there was time to", LegacyText.GRAY + "read them."));
            case NEXT_BEAT_ONLY -> GuiItems.item(Material.GRAY_DYE, "No Setting Needed",
                    List.of(LegacyText.GRAY + "Waits for a Next Beat action, e.g. in",
                            LegacyText.GRAY + "a While Active: On Right-Click row or",
                            LegacyText.GRAY + "a question answer, so a player decides",
                            LegacyText.GRAY + "when the scene goes on."));
        };
    }

    private static int indexOfRole(List<SceneRole> roles, String roleId) {
        for (int index = 0; index < roles.size(); index++) {
            if (roles.get(index).getId().equals(roleId))
                return index;
        }
        return -1;
    }

    private void handleBeatClick(InventoryClickEvent click, Player player, BeatHolder holder) {
        Scene scene = scenes.find(holder.sceneKey()).orElse(null);
        SceneBeat beat = scene == null ? null : scene.findBeat(holder.beatId()).orElse(null);
        if (beat == null) {
            if (scene == null)
                openBrowser(player, "", 0);
            else
                openScript(player, scene.getKey());
            return;
        }
        Runnable reopen = () -> openBeat(player, scene.getKey(), beat.getId());
        int slot = click.getRawSlot();
        for (int option = 0; option < BEAT_ADVANCE_SLOTS.length; option++) {
            if (slot != BEAT_ADVANCE_SLOTS[option])
                continue;
            SceneBeatAdvance advance = SceneBeatAdvance.values()[option];
            beat.setAdvance(advance);
            if (advance == SceneBeatAdvance.ROLE_DONE && scene.findRole(beat.getAdvanceRoleId()).isEmpty()) {
                // Start with a role that acts in this beat.
                beat.setAdvanceRoleId(beat.actingRoles().isEmpty()
                        ? scene.getRoles().stream().findFirst().map(SceneRole::getId).orElse(null)
                        : beat.actingRoles().getFirst());
            }
            scenes.save(scene);
            GuiItems.click(player);
            reopen.run();
            return;
        }
        switch (slot) {
            case BEAT_SETTING_SLOT -> {
                if (beat.getAdvance() == SceneBeatAdvance.AFTER_TIME) {
                    adjust(click, player, scene, 1, beat::setSeconds, beat.getSeconds(),
                            "Enter how many seconds the beat lasts:", 1, reopen);
                } else if (beat.getAdvance() == SceneBeatAdvance.ROLE_DONE && !scene.getRoles().isEmpty()) {
                    int direction = GuiItems.cycleDirection(click.getClick());
                    if (direction == 0)
                        return;
                    List<SceneRole> roles = scene.getRoles();
                    int current = Math.max(0, indexOfRole(roles, beat.getAdvanceRoleId()));
                    beat.setAdvanceRoleId(roles.get(Math.floorMod(current + direction, roles.size())).getId());
                    scenes.save(scene);
                    GuiItems.click(player);
                    reopen.run();
                }
            }
            case BEAT_COPY_SLOT, BEAT_DELETE_SLOT -> {
                if (sceneService != null && sceneService.isRunning(scene.getKey())) {
                    player.sendMessage(UiText.warning("Stop the scene before copying or deleting beats."));
                    return;
                }
                if (slot == BEAT_DELETE_SLOT) {
                    if (!click.isShiftClick())
                        return;
                    int index = scene.beatIndex(beat.getId());
                    scene.removeBeat(beat.getId());
                    scenes.save(scene);
                    player.sendMessage(UiText.success("Deleted beat " + (index + 1) + "."));
                    openScript(player, scene.getKey());
                    return;
                }
                if (scene.duplicateBeat(beat.getId()).isEmpty())
                    return;
                scenes.save(scene);
                GuiItems.click(player);
                reopen.run();
            }
            case BEAT_BACK_SLOT -> openScript(player, scene.getKey());
            default -> {
            }
        }
    }

    private static Material advanceMaterial(SceneBeatAdvance advance) {
        return switch (advance) {
            case EVERYONE_DONE -> Material.MAP;
            case AFTER_TIME -> Material.CLOCK;
            case ROLE_DONE -> Material.NAME_TAG;
            case NEXT_BEAT_ONLY -> Material.REPEATER;
        };
    }

    // --------------------------------------------------------------- roles

    private void openPresetPicker(Player player, String sceneKey, String roleId, int requestedPage) {
        Scene scene = scenes.find(sceneKey).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        List<NpcDefinition> presets = List.copyOf(definitions.findAll());
        int pages = Math.max(1, (presets.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new PresetPickerHolder(scene.getKey(), roleId, page), 54,
                UiText.title(roleId == null ? "Add Role" : "Played By"));
        int from = page * PAGE_SIZE;
        for (int index = from; index < Math.min(from + PAGE_SIZE, presets.size()); index++) {
            NpcDefinition preset = presets.get(index);
            SceneRole taken = scene.roleOf(preset.getKey(), roleId).orElse(null);
            if (taken != null) {
                inventory.setItem(index - from,
                        GuiItems.item(Material.GRAY_DYE, preset.getDisplayName(),
                                List.of(LegacyText.DARK_GRAY + "Key: " + preset.getKey(),
                                        LegacyText.RED + "Already has a role in this scene",
                                        LegacyText.GRAY + "An NPC plays one role per scene,",
                                        LegacyText.GRAY + "and that role can act in every beat.")));
                continue;
            }
            boolean current = roleId != null
                    && scene.findRole(roleId).map(role -> preset.getKey().equals(role.getPresetKey())).orElse(false);
            ItemStack icon = GuiItems.item(Material.PLAYER_HEAD, preset.getDisplayName(),
                    List.of(LegacyText.DARK_GRAY + "Key: " + preset.getKey(), "", current
                            ? LegacyText.GREEN + "Plays this role now"
                            : GuiItems.hint("Click", roleId == null ? "cast this NPC" : "let this NPC play the role")));
            inventory.setItem(index - from, NpcHeadUtil.applySkin(icon, preset));
        }
        if (presets.isEmpty())
            inventory.setItem(22, GuiItems.empty("No NPC Presets", "Create an NPC preset first"));
        if (page > 0)
            inventory.setItem(GuiItems.PREVIOUS_SLOT, GuiItems.previousPage(page, pages));
        inventory.setItem(GuiItems.BACK_SLOT, GuiItems.back(roleId == null ? "the roles" : "the role"));
        if (page + 1 < pages)
            inventory.setItem(GuiItems.NEXT_SLOT, GuiItems.nextPage(page, pages));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void handlePresetPickerClick(InventoryClickEvent click, Player player, PresetPickerHolder holder) {
        Scene scene = scenes.find(holder.sceneKey()).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        int slot = click.getRawSlot();
        if (slot == GuiItems.BACK_SLOT) {
            if (holder.roleId() == null || guiService == null)
                openScript(player, scene.getKey());
            else
                guiService.openSceneRole(player, scene.getKey(), holder.roleId(), 0);
            return;
        }
        if (slot == GuiItems.PREVIOUS_SLOT || slot == GuiItems.NEXT_SLOT) {
            openPresetPicker(player, scene.getKey(), holder.roleId(),
                    holder.page() + (slot == GuiItems.NEXT_SLOT ? 1 : -1));
            return;
        }
        List<NpcDefinition> presets = List.copyOf(definitions.findAll());
        int index = holder.page() * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index < 0 || index >= presets.size())
            return;
        NpcDefinition preset = presets.get(index);
        SceneRole taken = scene.roleOf(preset.getKey(), holder.roleId()).orElse(null);
        if (taken != null) {
            player.sendMessage(UiText.warning(preset.getDisplayName()
                    + " already has a role in this scene. Give it the actions in the beats where it acts."));
            return;
        }
        String roleId;
        if (holder.roleId() == null) {
            if (scene.getRoles().size() >= Scene.MAX_ROLES)
                return;
            roleId = null;
            scene.addRole(preset.getKey());
            player.sendMessage(UiText.success("Added " + preset.getDisplayName()
                    + " to the scene. Click its cells in the script to give it actions."));
        } else {
            SceneRole role = scene.findRole(holder.roleId()).orElse(null);
            if (role == null) {
                openScript(player, scene.getKey());
                return;
            }
            role.setPresetKey(preset.getKey());
            roleId = role.getId();
            player.sendMessage(UiText.success(preset.getDisplayName() + " now plays this role."));
        }
        scenes.save(scene);
        if (roleId != null && guiService != null) {
            guiService.openSceneRole(player, scene.getKey(), roleId, 0);
        } else {
            // Show the roles page with the new column.
            ScriptView view = scriptViews.get(player.getUniqueId());
            int beatPage = view != null && view.sceneKey().equals(scene.getKey()) ? view.beatPage() : 0;
            openScript(player, scene.getKey(), beatPage, (scene.getRoles().size() - 1) / SCRIPT_COLUMNS);
        }
    }

    // ---------------------------------------------------------------- area

    private void openAreaPicker(Player player, String sceneKey, String folder, int requestedPage) {
        List<LocationBrowserModel.Entry> entries = LocationBrowserModel.entries(locations.findAll(), folder);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new AreaPickerHolder(sceneKey, folder, page), 54,
                UiText.title(folder.isEmpty() ? "Scene Area" : "Locations: " + folder));
        int from = page * PAGE_SIZE;
        for (int index = from; index < Math.min(from + PAGE_SIZE, entries.size()); index++) {
            LocationBrowserModel.Entry entry = entries.get(index);
            if (entry.folder()) {
                inventory.setItem(index - from, GuiItems.item(Material.CHEST, entry.label(), List.of(
                        LegacyText.GRAY + "" + entry.childCount() + " location(s)", GuiItems.hint("Click", "open"))));
                continue;
            }
            NamedLocation location = entry.location();
            ItemStack icon = location.icon();
            inventory.setItem(index - from,
                    GuiItems.item(icon == null ? new ItemStack(Material.LODESTONE) : icon,
                            LegacyText.GOLD + entry.label(), List.of(LegacyText.GRAY + location.location().display(),
                                    GuiItems.hint("Click", "start the scene when a player comes near"))));
        }
        if (entries.isEmpty())
            inventory.setItem(22,
                    GuiItems.empty("No Saved Locations", "Create global locations from the Locations menu first"));
        if (page > 0)
            inventory.setItem(GuiItems.PREVIOUS_SLOT, GuiItems.previousPage(page, pages));
        inventory.setItem(GuiItems.BACK_SLOT,
                GuiItems.back(folder.isEmpty() ? "the triggers" : "the parent location group"));
        if (page + 1 < pages)
            inventory.setItem(GuiItems.NEXT_SLOT, GuiItems.nextPage(page, pages));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void handleAreaPickerClick(InventoryClickEvent click, Player player, AreaPickerHolder holder) {
        int slot = click.getRawSlot();
        if (slot == GuiItems.BACK_SLOT) {
            if (holder.folder().isEmpty())
                openTriggers(player, holder.sceneKey());
            else
                openAreaPicker(player, holder.sceneKey(), LocationBrowserModel.parent(holder.folder()), 0);
            return;
        }
        if (slot == GuiItems.PREVIOUS_SLOT || slot == GuiItems.NEXT_SLOT) {
            openAreaPicker(player, holder.sceneKey(), holder.folder(),
                    holder.page() + (slot == GuiItems.NEXT_SLOT ? 1 : -1));
            return;
        }
        List<LocationBrowserModel.Entry> entries = LocationBrowserModel.entries(locations.findAll(), holder.folder());
        int index = holder.page() * PAGE_SIZE + slot;
        if (slot >= PAGE_SIZE || index < 0 || index >= entries.size())
            return;
        LocationBrowserModel.Entry entry = entries.get(index);
        if (entry.folder()) {
            openAreaPicker(player, holder.sceneKey(), entry.path(), 0);
            return;
        }
        scenes.find(holder.sceneKey()).ifPresent(scene -> {
            scene.setAreaLocationKey(entry.location().key());
            scene.setTrigger(SceneTrigger.PLAYER_ENTERS, true);
            scenes.save(scene);
            player.sendMessage(UiText.success("The scene now starts when a player comes within " + scene.getAreaRadius()
                    + " blocks of '" + entry.location().displayName() + "'."));
        });
        openTriggers(player, holder.sceneKey());
    }

    // -------------------------------------------------------------- delete

    private void openDelete(Player player, String sceneKey) {
        Scene scene = scenes.find(sceneKey).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new DeleteHolder(scene.getKey()), 27,
                UiText.title("Delete Scene", scene.getDisplayName()));
        inventory.setItem(11, GuiItems.confirm(List.of("Permanently deletes this scene and its roles",
                "Start/Stop Scene actions naming it stop working")));
        inventory.setItem(15, GuiItems.cancel());
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void handleDeleteClick(InventoryClickEvent click, Player player, DeleteHolder holder) {
        if (click.getRawSlot() == 15) {
            openEditor(player, holder.sceneKey());
            return;
        }
        if (click.getRawSlot() != 11)
            return;
        scenes.find(holder.sceneKey()).ifPresent(scene -> {
            if (sceneService != null)
                sceneService.forceStop(scene.getKey());
            scenes.delete(scene);
            player.sendMessage(UiText.success("Deleted scene '" + scene.getDisplayName() + "'."));
        });
        int slash = holder.sceneKey().lastIndexOf('/');
        openBrowser(player, slash < 0 ? "" : holder.sceneKey().substring(0, slash), 0);
    }

    // ------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!isOwnHolder(holder))
            return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize())
            return;
        switch (holder) {
            case BrowserHolder browser -> handleBrowserClick(event, player, browser);
            case EditorHolder editor -> handleEditorClick(event, player, editor);
            case TriggersHolder triggers -> handleTriggersClick(event, player, triggers);
            case ScriptHolder script -> handleScriptClick(event, player, script);
            case BeatHolder beat -> handleBeatClick(event, player, beat);
            case PresetPickerHolder picker -> handlePresetPickerClick(event, player, picker);
            case AreaPickerHolder area -> handleAreaPickerClick(event, player, area);
            case DeleteHolder delete -> handleDeleteClick(event, player, delete);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isOwnHolder(event.getView().getTopInventory().getHolder()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        presetFilters.remove(event.getPlayer().getUniqueId());
        scriptViews.remove(event.getPlayer().getUniqueId());
    }

    /** The NPC whose scenes the player is browsing, or null for all scenes. */
    private NpcDefinition presetFilter(Player player) {
        String key = presetFilters.get(player.getUniqueId());
        NpcDefinition definition = key == null ? null : definitions.find(key).orElse(null);
        if (key != null && definition == null)
            presetFilters.remove(player.getUniqueId());
        return definition;
    }

    private static boolean isOwnHolder(InventoryHolder holder) {
        return holder instanceof BrowserHolder || holder instanceof EditorHolder || holder instanceof TriggersHolder
                || holder instanceof ScriptHolder || holder instanceof BeatHolder
                || holder instanceof PresetPickerHolder || holder instanceof AreaPickerHolder
                || holder instanceof DeleteHolder;
    }

    // ------------------------------------------------------------- helpers

    private String statusText(Scene scene) {
        if (sceneService == null)
            return LegacyText.WHITE + "Unknown";
        String status = sceneService.status(scene.getKey());
        return (sceneService.isRunning(scene.getKey()) ? LegacyText.GREEN : LegacyText.WHITE) + status;
    }

    private String triggerSummary(Scene scene) {
        if (scene.getTriggers().isEmpty())
            return "by command or action only";
        String triggers = String.join(", ", scene.getTriggers().stream()
                .map(trigger -> trigger.displayName().toLowerCase(java.util.Locale.ROOT)).toList());
        return scene.isEnabled() ? triggers : triggers + LegacyText.RED + " (switched off)";
    }

    /**
     * Which scenes this one starts with Start Scene, and which scenes start it, so
     * chains are visible from the scene icon.
     */
    private List<String> chainLore(Scene scene) {
        List<String> lore = new ArrayList<>();
        List<String> next = scene.startedScenes().stream().map(this::sceneName).toList();
        List<String> previous = scenes.findAll().stream().filter(other -> !other.getKey().equals(scene.getKey()))
                .filter(other -> other.startedScenes().contains(scene.getKey())).map(Scene::getDisplayName).toList();
        if (!next.isEmpty())
            lore.add(LegacyText.GRAY + "Leads to: " + LegacyText.AQUA
                    + String.join(LegacyText.GRAY + ", " + LegacyText.AQUA, next));
        if (!previous.isEmpty())
            lore.add(LegacyText.GRAY + "Follows: " + LegacyText.AQUA
                    + String.join(LegacyText.GRAY + ", " + LegacyText.AQUA, previous));
        return lore;
    }

    private String sceneName(String sceneKey) {
        return scenes.find(sceneKey).map(Scene::getDisplayName).orElse(LegacyText.RED + "missing '" + sceneKey + "'");
    }

    /** The NPC's name without colour codes, for use inside other text. */
    private String plainPresetName(SceneRole role) {
        if (role.getPresetKey() == null)
            return "nobody";
        return definitions.find(role.getPresetKey()).map(NpcDefinition::getDisplayName).orElse(role.getPresetKey());
    }

    private String presetName(SceneRole role) {
        if (role.getPresetKey() == null)
            return LegacyText.RED + "nobody";
        return definitions.find(role.getPresetKey()).map(NpcDefinition::getDisplayName)
                .orElse(LegacyText.RED + "missing '" + role.getPresetKey() + "'");
    }

    private static ItemStack sceneIcon(Scene scene) {
        ItemStack icon = scene.getIcon();
        return icon == null ? new ItemStack(Material.BELL) : icon;
    }

    private static String seconds(int seconds) {
        if (seconds == 0)
            return "None";
        if (seconds < 60)
            return seconds + " s";
        int minutes = seconds / 60;
        int rest = seconds % 60;
        String text = minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
        return rest == 0 ? text : text + " " + rest + " s";
    }

    private static String periodName(DayPeriod period) {
        String lower = period.lowerName();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static Material endingMaterial(SceneEnding ending) {
        return switch (ending) {
            case BEATS_FINISHED -> Material.MAP;
            case DURATION -> Material.CLOCK;
            case TIME_OF_DAY -> Material.SUNFLOWER;
            case WEATHER_CHANGES -> Material.WATER_BUCKET;
        };
    }

    private static Material triggerMaterial(SceneTrigger trigger) {
        return switch (trigger) {
            case NIGHT -> Material.BLACK_BED;
            case MORNING -> Material.ORANGE_DYE;
            case NOON -> Material.SUNFLOWER;
            case AFTERNOON -> Material.GOLDEN_CARROT;
            case EVENING -> Material.LANTERN;
            case PRECIPITATION -> Material.WATER_BUCKET;
            case RAIN -> Material.BLUE_DYE;
            case SNOW -> Material.SNOWBALL;
            case THUNDER -> Material.LIGHTNING_ROD;
            case CLEAR -> Material.LIGHT_BLUE_DYE;
            case PLAYER_ENTERS -> Material.OAK_DOOR;
        };
    }

    private record BrowserEntry(String path, String label, Scene scene, int count) {
    }

    private record BrowserHolder(String folder, int page) implements GuiHolder {
    }

    private record EditorHolder(String sceneKey) implements GuiHolder {
    }

    private record TriggersHolder(String sceneKey) implements GuiHolder {
    }

    private record ScriptHolder(String sceneKey, int beatPage, int rolePage) implements GuiHolder {
    }

    private record ScriptView(String sceneKey, int beatPage, int rolePage) {
    }

    private record BeatHolder(String sceneKey, String beatId) implements GuiHolder {
    }

    private record PresetPickerHolder(String sceneKey, String roleId, int page) implements GuiHolder {
    }

    private record AreaPickerHolder(String sceneKey, String folder, int page) implements GuiHolder {
    }

    private record DeleteHolder(String sceneKey) implements GuiHolder {
    }
}
