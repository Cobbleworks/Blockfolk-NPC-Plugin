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
 * which watches an area), ending, and test controls, and the role list. Role
 * action rows are edited by {@link GuiService#openSceneRole}, which shares the
 * preset action editor.
 */
public final class SceneGuiService implements Listener, GuiService.SceneNavigation {

    private static final int PAGE_SIZE = 45;
    private static final String DESCRIPTION_INPUT_KEY = "description";
    private static final int SUMMARY_SLOT = 4;
    private static final int NAME_SLOT = 19;
    private static final int DESCRIPTION_SLOT = 28;
    private static final int TRIGGERS_SLOT = 21;
    private static final int ENABLED_SLOT = 30;
    private static final int ROLES_SLOT = 23;
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
    public void openRoles(Player player, String sceneKey) {
        openRoles(player, sceneKey, 0);
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
            player.sendMessage(UiText.success("Created scene '" + scene.getDisplayName() + "'. Add roles next."));
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
                                LegacyText.GRAY + "Each role runs its own action row at the same",
                                LegacyText.GRAY + "time; Give Cue / Wait For Cue keep them in step.",
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
        lore.add(LegacyText.GRAY + "Roles: " + LegacyText.WHITE + scene.getRoles().size());
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
        inventory.setItem(ENABLED_SLOT, GuiItems.toggle(Material.LIME_DYE, Material.GRAY_DYE, "Triggers",
                scene.isEnabled(), "triggers start the scene", "only manual starts work", List.of()));
        inventory.setItem(TRIGGERS_SLOT, GuiItems.item(Material.CLOCK, "Starts When", triggerLore(scene)));
        inventory.setItem(ROLES_SLOT, GuiItems.item(Material.PLAYER_HEAD, "Roles", rolesLore(scene)));
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
            case ENABLED_SLOT -> {
                scene.setEnabled(!scene.isEnabled());
                scenes.save(scene);
                GuiItems.click(player);
                reopen.run();
            }
            case TRIGGERS_SLOT -> openTriggers(player, scene.getKey());
            case ROLES_SLOT -> openRoles(player, scene.getKey(), 0);
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
            case LANES_FINISHED -> GuiItems.item(Material.GRAY_DYE, "No Setting Needed",
                    List.of(LegacyText.GRAY + "Ends once every role finished its",
                            LegacyText.GRAY + "scene actions (at the latest after 30 min).",
                            LegacyText.GRAY + "While Active reactions need another",
                            LegacyText.GRAY + "ending, or the scene ends too soon."));
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

    private List<String> rolesLore(Scene scene) {
        List<String> lore = new ArrayList<>();
        if (scene.getRoles().isEmpty()) {
            lore.add(LegacyText.RED + "No roles yet: add the NPCs that take part");
        } else {
            for (SceneRole role : scene.getRoles().stream().limit(8).toList())
                lore.add(LegacyText.WHITE + "• " + presetName(role));
            if (scene.getRoles().size() > 8)
                lore.add(LegacyText.DARK_GRAY + "… and " + (scene.getRoles().size() - 8) + " more");
        }
        lore.add("");
        lore.add(GuiItems.hint("Click", "manage roles and their actions"));
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

    // --------------------------------------------------------------- roles

    private void openRoles(Player player, String sceneKey, int requestedPage) {
        Scene scene = scenes.find(sceneKey).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        List<SceneRole> roles = scene.getRoles();
        int pages = Math.max(1, (roles.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new RolesHolder(scene.getKey(), page), 54,
                UiText.title("Roles", scene.getDisplayName()));
        int from = page * PAGE_SIZE;
        for (int index = from; index < Math.min(from + PAGE_SIZE, roles.size()); index++)
            inventory.setItem(index - from, roleItem(scene, roles.get(index)));
        if (roles.isEmpty())
            inventory.setItem(22, GuiItems.empty("No Roles Yet", "Use Add Role below to cast an NPC"));
        if (page > 0)
            inventory.setItem(GuiItems.PREVIOUS_SLOT, GuiItems.previousPage(page, pages));
        inventory.setItem(GuiItems.BACK_SLOT, GuiItems.back("the scene editor"));
        if (roles.size() < Scene.MAX_ROLES)
            inventory.setItem(GuiItems.CREATE_SLOT, GuiItems.create("Add Role", "Choose the NPC preset that plays it"));
        if (page + 1 < pages)
            inventory.setItem(GuiItems.NEXT_SLOT, GuiItems.nextPage(page, pages));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private ItemStack roleItem(Scene scene, SceneRole role) {
        List<String> lore = new ArrayList<>();
        lore.add(LegacyText.GRAY + "Every free instance of this NPC plays it");
        if (scene.roleOf(role.getPresetKey(), role.getId()).isPresent()) {
            lore.add(LegacyText.RED + "This NPC has two roles: the scene cannot");
            lore.add(LegacyText.RED + "start until one of them is removed");
        }
        lore.add(LegacyText.GRAY + "Scene actions: " + LegacyText.WHITE + role.getLane().size());
        lore.add(LegacyText.GRAY + "Wrap-up actions: " + LegacyText.WHITE + role.getWrapUp().size());
        List<String> reactions = role.getReactions().keySet().stream().map(BehaviourEvent::displayName).toList();
        lore.add(LegacyText.GRAY + "While active: " + LegacyText.WHITE
                + (reactions.isEmpty() ? "normal behaviour" : String.join(", ", reactions)));
        lore.add("");
        lore.add(GuiItems.hint("Click", "edit its actions"));
        lore.add(GuiItems.danger("Shift-right-click", "remove role"));
        NpcDefinition preset = role.getPresetKey() == null ? null : definitions.find(role.getPresetKey()).orElse(null);
        ItemStack icon = GuiItems.item(preset == null ? Material.SKELETON_SKULL : Material.PLAYER_HEAD,
                presetName(role), lore);
        return preset == null ? icon : NpcHeadUtil.applySkin(icon, preset);
    }

    private void handleRolesClick(InventoryClickEvent click, Player player, RolesHolder holder) {
        Scene scene = scenes.find(holder.sceneKey()).orElse(null);
        if (scene == null) {
            openBrowser(player, "", 0);
            return;
        }
        int slot = click.getRawSlot();
        switch (slot) {
            case GuiItems.PREVIOUS_SLOT -> openRoles(player, scene.getKey(), holder.page() - 1);
            case GuiItems.NEXT_SLOT -> openRoles(player, scene.getKey(), holder.page() + 1);
            case GuiItems.BACK_SLOT -> openEditor(player, scene.getKey());
            case GuiItems.CREATE_SLOT -> {
                if (scene.getRoles().size() < Scene.MAX_ROLES)
                    openPresetPicker(player, scene.getKey(), null, 0);
            }
            default -> {
                List<SceneRole> roles = scene.getRoles();
                int index = holder.page() * PAGE_SIZE + slot;
                if (slot >= PAGE_SIZE || index < 0 || index >= roles.size())
                    return;
                SceneRole role = roles.get(index);
                if (click.getClick() == ClickType.SHIFT_RIGHT) {
                    if (sceneService != null && sceneService.isRunning(scene.getKey())) {
                        player.sendMessage(UiText.warning("Stop the scene before removing roles."));
                        return;
                    }
                    scene.removeRole(role.getId());
                    scenes.save(scene);
                    player.sendMessage(UiText.success("Removed " + presetName(role) + " from the scene."));
                    openRoles(player, scene.getKey(), holder.page());
                } else if (guiService != null) {
                    guiService.openSceneRole(player, scene.getKey(), role.getId(), 0);
                }
            }
        }
    }

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
                                        LegacyText.GRAY + "An NPC plays one role per scene.", "",
                                        LegacyText.GRAY + "For more steps, add them to its role,",
                                        LegacyText.GRAY + "or end its row with Start Scene to",
                                        LegacyText.GRAY + "continue in a follow-up scene.")));
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
                openRoles(player, scene.getKey(), 0);
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
                    + " already has a role in this scene. Add the steps to its role, or chain a follow-up scene"
                    + " with Start Scene."));
            return;
        }
        String roleId;
        if (holder.roleId() == null) {
            if (scene.getRoles().size() >= Scene.MAX_ROLES)
                return;
            roleId = scene.addRole(preset.getKey()).getId();
            player.sendMessage(UiText.success("Added " + preset.getDisplayName()
                    + " to the scene. Add its scene actions, then its wrap-up and reactions if needed."));
        } else {
            SceneRole role = scene.findRole(holder.roleId()).orElse(null);
            if (role == null) {
                openRoles(player, scene.getKey(), 0);
                return;
            }
            role.setPresetKey(preset.getKey());
            roleId = role.getId();
            player.sendMessage(UiText.success(preset.getDisplayName() + " now plays this role."));
        }
        scenes.save(scene);
        if (guiService != null)
            guiService.openSceneRole(player, scene.getKey(), roleId, 0);
        else
            openRoles(player, scene.getKey(), 0);
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
            case RolesHolder roles -> handleRolesClick(event, player, roles);
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
                || holder instanceof RolesHolder || holder instanceof PresetPickerHolder
                || holder instanceof AreaPickerHolder || holder instanceof DeleteHolder;
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
            case LANES_FINISHED -> Material.MAP;
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

    private record RolesHolder(String sceneKey, int page) implements GuiHolder {
    }

    private record PresetPickerHolder(String sceneKey, String roleId, int page) implements GuiHolder {
    }

    private record AreaPickerHolder(String sceneKey, String folder, int page) implements GuiHolder {
    }

    private record DeleteHolder(String sceneKey) implements GuiHolder {
    }
}
