package dev.blockfolk.gui;

import static dev.blockfolk.gui.GuiItems.hint;
import static dev.blockfolk.gui.GuiItems.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.BehaviourAction;
import dev.blockfolk.model.DayPeriod;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.RegionalEvent;
import dev.blockfolk.model.RegionalEvent.Phase;
import dev.blockfolk.model.RegionalEvent.Region;
import dev.blockfolk.model.RegionalEvent.Trigger;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.RegionalEventRepository;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.runtime.RegionalEventService;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;

public final class RegionalEventGuiService implements Listener {
    private static final int PAGE_SIZE = 36;
    private static final int PREVIOUS_PAGE = 36;
    private static final int NEXT_PAGE = 44;
    private final Plugin plugin;
    private final RegionalEventRepository repository;
    private final RegionalEventService runtime;
    private final NpcInstanceRegistry instances;
    private final NpcDefinitionRepository definitions;
    private final ChatInputService input;
    private final GuiService gui;
    private final CustomEventGuiService legacyEvents;

    public RegionalEventGuiService(Plugin plugin, RegionalEventRepository repository, RegionalEventService runtime,
            NpcInstanceRegistry instances, NpcDefinitionRepository definitions, ChatInputService input, GuiService gui,
            CustomEventGuiService legacyEvents) {
        this.plugin = plugin;
        this.repository = repository;
        this.runtime = runtime;
        this.instances = instances;
        this.definitions = definitions;
        this.input = input;
        this.gui = gui;
        this.legacyEvents = legacyEvents;
    }

    public void open(Player player) {
        openRegions(player, 0);
    }

    private void openRegions(Player player, int page) {
        List<Entry> entries = repository.regions().stream()
                .map(region -> new Entry(region.id(),
                        item(Material.FILLED_MAP, region.name(),
                                List.of(LegacyText.GRAY + region.world(), hint("Click", "open events"),
                                        GuiItems.danger("Shift-right-click", "delete this region and its events")))))
                .toList();
        Inventory menu = menu(Screen.REGIONS, null, null, null, page, "Events", entries);
        if (legacyEvents.hasEvents())
            menu.setItem(47,
                    item(Material.BOOK, "Legacy Events", List.of(hint("Click", "view saved global event signals"))));
        menu.setItem(49, GuiItems.back("Blockfolk Presets"));
        menu.setItem(51, GuiItems.create("New Region", "Stand at the first corner to begin"));
        show(player, menu);
    }

    private void openRegion(Player player, Region region, int page) {
        List<Entry> entries = repository.events().stream().filter(event -> event.regionId().equals(region.id()))
                .map(event -> new Entry(event.id(),
                        item(Material.BELL, event.name(),
                                List.of(LegacyText.GRAY + (event.trigger() == Trigger.MANUAL || event.enabled()
                                        ? event.trigger().label()
                                        : "Automatic start off"), hint("Click", "edit event"),
                                        GuiItems.danger("Shift-right-click", "delete event")))))
                .toList();
        Inventory menu = menu(Screen.REGION, region.id(), null, null, page, region.name(), entries);
        if (entries.isEmpty())
            menu.setItem(22, GuiItems.empty("No Events Yet", "Create an event or set the region's corners below"));
        menu.setItem(46, cornerButton(1, region.x1(), region.z1()));
        menu.setItem(47, cornerButton(2, region.x2(), region.z2()));
        menu.setItem(49, GuiItems.back("Regions"));
        menu.setItem(51, GuiItems.create("New Event", "Choose when it starts and what NPCs do"));
        show(player, menu);
    }

    private ItemStack cornerButton(int label, int x, int z) {
        return item(Material.MAP, "Corner " + label,
                List.of(LegacyText.GRAY + "X " + x + ", Z " + z, hint("Click", "set to where you are standing")));
    }

    private void openEvent(Player player, RegionalEvent event, UUID phaseId, int page) {
        Phase phase = event.phases().stream().filter(value -> value.id().equals(phaseId)).findFirst()
                .orElse(event.phases().getFirst());
        int index = event.phases().indexOf(phase);
        List<Entry> entries = phase.actors().entrySet().stream()
                .map(row -> new Entry(row.getKey(),
                        actorIcon(row.getKey(),
                                row.getValue().stream().map(action -> action.type().displayName()).toList(), true)))
                .toList();
        Inventory menu = menu(Screen.EVENT, event.regionId(), event.id(), phase.id(), page, event.name(), entries);
        if (entries.isEmpty())
            menu.setItem(22, GuiItems.empty("No NPCs Yet", "Add an NPC to choose what it does"));
        if (index > 0)
            menu.setItem(37, item(Material.ARROW, "Previous Phase", List.of(hint("Click", "show phase " + index))));
        menu.setItem(40,
                item(Material.PAPER, "Phase " + (index + 1) + " of " + event.phases().size(),
                        List.of(LegacyText.GRAY + "NPCs in this phase start together",
                                LegacyText.GRAY + "The next phase starts when these NPCs finish",
                                GuiItems.danger("Shift-right-click", "remove this phase"))));
        if (index + 1 < event.phases().size()) {
            menu.setItem(43, item(Material.ARROW, "Next Phase", List.of(hint("Click", "show phase " + (index + 2)))));
        } else {
            menu.setItem(43, GuiItems.create("Add Then", "Start another phase after this one"));
        }
        menu.setItem(45,
                item(Material.CLOCK, "Start: " + event.trigger().label(), List.of(LegacyText.GRAY
                        + (event.trigger() == Trigger.TIME_OF_DAY ? periodLabel(event.period()) : "Choose a trigger"),
                        hint("Click", "change when the event starts"))));
        if (event.trigger() != Trigger.MANUAL)
            menu.setItem(47,
                    item(event.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                            event.enabled() ? "Automatic Start On" : "Automatic Start Off",
                            List.of(hint("Click", "toggle automatic start"))));
        menu.setItem(49, GuiItems.back("Region Events"));
        menu.setItem(51, GuiItems.create("Add NPC", "Choose a placed NPC for this phase"));
        boolean playing = runtime.isRunning(event.id());
        menu.setItem(53,
                item(playing ? Material.REDSTONE_BLOCK : Material.LIME_CONCRETE, playing ? "Stop Event" : "Test Now",
                        List.of(hint("Click", playing ? "end this run" : "play this event"))));
        show(player, menu);
    }

    private void openActors(Player player, Region region, RegionalEvent event, Phase phase, int page) {
        List<Entry> entries = instances.findAll().stream()
                .filter(npc -> region.world().equals(npc.getStoredLocation().worldName()))
                .filter(npc -> !phase.actors().containsKey(npc.getId()))
                .map(npc -> new Entry(npc.getId(), actorIcon(npc.getId(), List.of(), false))).toList();
        Inventory menu = menu(Screen.ACTORS, region.id(), event.id(), phase.id(), page, "Choose an NPC", entries);
        if (entries.isEmpty())
            menu.setItem(22, GuiItems.empty("No NPCs Available", "Place an NPC in this world first"));
        menu.setItem(49, GuiItems.back("Event"));
        show(player, menu);
    }

    private ItemStack actorIcon(UUID id, List<String> actions, boolean editing) {
        NpcInstance npc = instances.findById(id).orElse(null);
        if (npc == null)
            return item(Material.BARRIER, "Missing NPC", List.of(GuiItems.danger("Shift-right-click", "remove NPC")));
        NpcDefinition definition = definitions.find(npc.getDefinitionKey()).orElse(null);
        List<String> lore = new ArrayList<>();
        var location = npc.getStoredLocation();
        lore.add(LegacyText.GRAY + "X " + (int) Math.floor(location.x()) + ", Y " + (int) Math.floor(location.y())
                + ", Z " + (int) Math.floor(location.z()));
        if (!actions.isEmpty())
            lore.add(LegacyText.GRAY + String.join(" → ", actions));
        lore.add(hint("Click", editing ? "edit actions" : "add NPC"));
        if (editing)
            lore.add(GuiItems.danger("Shift-right-click", "remove NPC"));
        ItemStack head = item(Material.PLAYER_HEAD,
                definition == null ? npc.getDefinitionKey() : definition.getDisplayName(), lore);
        return definition == null ? head : NpcHeadUtil.applySkin(head, definition);
    }

    private void openTriggers(Player player, RegionalEvent event, UUID phaseId) {
        Inventory menu = Bukkit.createInventory(
                new View(Screen.TRIGGERS, event.regionId(), event.id(), phaseId, 0, List.of()), 27,
                UiText.title("When Does It Start?"));
        menu.setItem(10, item(Material.STONE_BUTTON, "Manual",
                List.of(hint("Click", "start with Test Now, Emit Event, or a command"))));
        menu.setItem(12, item(Material.OAK_DOOR, "Player Enters", List.of(hint("Click", "start when someone enters"))));
        menu.setItem(14, item(Material.CLOCK, "Time of Day", List.of(hint("Click", "pick a time slot"))));
        menu.setItem(22, GuiItems.back("Event"));
        show(player, menu);
    }

    private void openPeriods(Player player, RegionalEvent event, UUID phaseId) {
        Inventory menu = Bukkit.createInventory(
                new View(Screen.PERIODS, event.regionId(), event.id(), phaseId, 0, List.of()), 27,
                UiText.title("Choose Time of Day"));
        for (DayPeriod period : DayPeriod.values())
            menu.setItem(10 + period.ordinal(),
                    item(Material.CLOCK, periodLabel(period), List.of(hint("Click", "select"))));
        menu.setItem(22, GuiItems.back("Triggers"));
        show(player, menu);
    }

    private static String periodLabel(DayPeriod period) {
        String name = period.lowerName();
        return name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + name.substring(1);
    }

    private Inventory menu(Screen screen, UUID region, UUID event, UUID phase, int requestedPage, String title,
            List<Entry> entries) {
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        List<Entry> visible = entries.subList(page * PAGE_SIZE, Math.min((page + 1) * PAGE_SIZE, entries.size()));
        Inventory menu = Bukkit.createInventory(
                new View(screen, region, event, phase, page, visible.stream().map(Entry::id).toList()), 54,
                UiText.title(title));
        for (int i = 0; i < visible.size(); i++)
            menu.setItem(i, visible.get(i).icon());
        if (page > 0)
            menu.setItem(PREVIOUS_PAGE, GuiItems.previousPage(page, pages));
        if (page + 1 < pages)
            menu.setItem(NEXT_PAGE, GuiItems.nextPage(page, pages));
        return menu;
    }

    private void show(Player player, Inventory menu) {
        GuiLayout.fillMainBar(menu);
        player.openInventory(menu);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory menu = event.getView().getTopInventory();
        if (!(menu.getHolder() instanceof View view) || !(event.getWhoClicked() instanceof Player player))
            return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (!player.hasPermission("blockfolk.admin") || slot < 0 || slot >= menu.getSize()
                || menu.getItem(slot) == null)
            return;
        boolean remove = event.getClick() == ClickType.SHIFT_RIGHT;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == view)
                handleClick(player, view, slot, remove);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View)
            event.setCancelled(true);
    }

    private void handleClick(Player player, View view, int slot, boolean remove) {
        int page = view.page() + (slot == PREVIOUS_PAGE ? -1 : slot == NEXT_PAGE ? 1 : 0);
        UUID entry = slot < view.entries().size() ? view.entries().get(slot) : null;
        if (view.screen() == Screen.REGIONS) {
            if (entry != null && repository.region(entry) != null) {
                if (remove)
                    confirmRegionDeletion(player, repository.region(entry));
                else
                    openRegion(player, repository.region(entry), 0);
            } else if (slot == 47 && legacyEvents.hasEvents()) {
                legacyEvents.open(player);
            } else if (slot == 49) {
                gui.openMain(player);
            } else if (slot == 51) {
                createRegion(player);
            } else if (page != view.page()) {
                openRegions(player, page);
            }
            return;
        }
        Region region = repository.region(view.region());
        if (region == null) {
            open(player);
            return;
        }
        if (view.screen() == Screen.REGION) {
            handleRegionClick(player, region, slot, entry, remove, page);
            return;
        }
        if (view.screen() == Screen.CONFIRM_REGION_DELETE) {
            if (slot == 15) {
                repository.events().stream().filter(item -> item.regionId().equals(region.id()))
                        .forEach(item -> runtime.stop(item.id()));
                repository.deleteRegion(region.id());
            }
            open(player);
            return;
        }
        RegionalEvent event = repository.event(view.event());
        if (event == null) {
            openRegion(player, region, 0);
            return;
        }
        Phase phase = event.phases().stream().filter(candidate -> candidate.id().equals(view.phase())).findFirst()
                .orElse(null);
        if (phase == null) {
            openEvent(player, event, null, 0);
            return;
        }
        switch (view.screen()) {
            case EVENT -> handleEventClick(player, region, event, phase, slot, entry, remove, page);
            case ACTORS -> {
                if (entry != null && instances.findById(entry).isPresent()) {
                    saveActor(event.id(), phase.id(), entry, List.of(), false);
                    openActions(player, event, phase, entry);
                } else if (slot == 49) {
                    openEvent(player, event, phase.id(), 0);
                } else if (page != view.page()) {
                    openActors(player, region, event, phase, page);
                }
            }
            case TRIGGERS -> {
                switch (slot) {
                    case 10 -> saveTrigger(player, event, phase, Trigger.MANUAL, event.period());
                    case 12 -> saveTrigger(player, event, phase, Trigger.PLAYER_ENTER, event.period());
                    case 14 -> openPeriods(player, event, phase.id());
                    case 22 -> openEvent(player, event, phase.id(), 0);
                    default -> {
                    }
                }
            }
            case PERIODS -> {
                if (slot == 22)
                    openTriggers(player, event, phase.id());
                else if (slot >= 10 && slot < 10 + DayPeriod.values().length)
                    saveTrigger(player, event, phase, Trigger.TIME_OF_DAY, DayPeriod.values()[slot - 10]);
            }
            default -> {
            }
        }
    }

    private void confirmRegionDeletion(Player player, Region region) {
        Inventory menu = Bukkit.createInventory(
                new View(Screen.CONFIRM_REGION_DELETE, region.id(), null, null, 0, List.of()), 27,
                UiText.title("Delete " + region.name() + "?"));
        menu.setItem(11, GuiItems.back("Regions"));
        menu.setItem(15,
                item(Material.RED_CONCRETE, "Delete Region and Events", List.of(hint("Click", "confirm deletion"))));
        show(player, menu);
    }

    private void handleRegionClick(Player player, Region region, int slot, UUID entry, boolean remove, int page) {
        if (entry != null && repository.event(entry) != null) {
            if (remove) {
                runtime.stop(entry);
                repository.deleteEvent(entry);
                openRegion(player, region, page);
            } else {
                openEvent(player, repository.event(entry), null, 0);
            }
            return;
        }
        switch (slot) {
            case 46, 47 -> setCorner(player, region, slot == 46, page);
            case 49 -> open(player);
            case 51 -> createEvent(player, region);
            case PREVIOUS_PAGE, NEXT_PAGE -> openRegion(player, region, page);
            default -> {
            }
        }
    }

    private void handleEventClick(Player player, Region region, RegionalEvent event, Phase phase, int slot, UUID actor,
            boolean remove, int page) {
        if (actor != null && phase.actors().containsKey(actor)) {
            if (remove) {
                saveActor(event.id(), phase.id(), actor, null, false);
                openEvent(player, repository.event(event.id()), phase.id(), page);
            } else {
                openActions(player, event, phase, actor);
            }
            return;
        }
        int index = event.phases().indexOf(phase);
        switch (slot) {
            case PREVIOUS_PAGE, NEXT_PAGE -> openEvent(player, event, phase.id(), page);
            case 37 -> {
                if (index > 0)
                    openEvent(player, event, event.phases().get(index - 1).id(), 0);
            }
            case 40 -> {
                if (remove && event.phases().size() > 1) {
                    List<Phase> phases = new ArrayList<>(event.phases());
                    phases.remove(index);
                    RegionalEvent updated = event.withPhases(phases);
                    repository.save(updated);
                    openEvent(player, updated, phases.get(Math.max(0, index - 1)).id(), 0);
                }
            }
            case 43 -> {
                if (index + 1 < event.phases().size()) {
                    openEvent(player, event, event.phases().get(index + 1).id(), 0);
                } else {
                    Phase next = new Phase(UUID.randomUUID(), Map.of());
                    List<Phase> phases = new ArrayList<>(event.phases());
                    phases.add(next);
                    RegionalEvent updated = event.withPhases(phases);
                    repository.save(updated);
                    openEvent(player, updated, next.id(), 0);
                }
            }
            case 45 -> openTriggers(player, event, phase.id());
            case 47 -> {
                RegionalEvent updated = event.withSettings(event.trigger(), event.period(), !event.enabled());
                repository.save(updated);
                openEvent(player, updated, phase.id(), page);
            }
            case 49 -> openRegion(player, region, 0);
            case 51 -> openActors(player, region, event, phase, 0);
            case 53 -> {
                if (runtime.isRunning(event.id())) {
                    runtime.stop(event.id());
                    player.sendMessage(UiText.success("Stopped '" + event.name() + "'."));
                } else {
                    String error = runtime.play(event, player);
                    player.sendMessage(
                            error == null ? UiText.success("Started '" + event.name() + "'.") : UiText.error(error));
                    if (error == null) {
                        player.closeInventory();
                        return;
                    }
                }
                openEvent(player, event, phase.id(), page);
            }
            default -> {
            }
        }
    }

    private void createRegion(Player player) {
        Location first = player.getLocation();
        requestName(player, "Enter the region name:", name -> {
            Region region = new Region(UUID.randomUUID(), name, first.getWorld().getName(), first.getBlockX(),
                    first.getBlockZ(), first.getBlockX(), first.getBlockZ());
            repository.save(region);
            player.sendMessage(UiText.success("First corner set. Stand at the opposite corner and click Corner 2."));
            openRegion(player, region, 0);
        }, () -> open(player));
    }

    private void setCorner(Player player, Region region, boolean first, int page) {
        Location position = player.getLocation();
        if (!region.world().equals(position.getWorld().getName())) {
            player.sendMessage(UiText.error("Stand in the region's world to set a corner."));
            return;
        }
        Region updated = new Region(region.id(), region.name(), region.world(),
                first ? position.getBlockX() : region.x1(), first ? position.getBlockZ() : region.z1(),
                first ? region.x2() : position.getBlockX(), first ? region.z2() : position.getBlockZ());
        repository.save(updated);
        openRegion(player, updated, page);
    }

    private void createEvent(Player player, Region region) {
        requestName(player, "Enter the event name:", name -> {
            if (repository.region(region.id()) == null) {
                open(player);
                return;
            }
            RegionalEvent event = new RegionalEvent(UUID.randomUUID(), region.id(), name, Trigger.MANUAL,
                    DayPeriod.EVENING, false, List.of(new Phase(UUID.randomUUID(), Map.of())));
            repository.save(event);
            openEvent(player, event, null, 0);
        }, () -> openRegion(player, region, 0));
    }

    private void requestName(Player player, String prompt, Consumer<String> save, Runnable back) {
        input.request(player, prompt, name -> {
            try {
                save.accept(name);
            } catch (IllegalArgumentException error) {
                player.sendMessage(UiText.error(error.getMessage()));
                requestName(player, prompt, save, back);
            }
        }, back);
    }

    private void saveTrigger(Player player, RegionalEvent event, Phase phase, Trigger trigger, DayPeriod period) {
        RegionalEvent updated = event.withSettings(trigger, period, trigger != Trigger.MANUAL && event.enabled());
        repository.save(updated);
        openEvent(player, updated, phase.id(), 0);
    }

    private void openActions(Player player, RegionalEvent event, Phase phase, UUID actor) {
        gui.openActionRow(player, new GuiService.ActionRow("Event Actions",
                () -> instances.findById(actor).map(NpcInstance::getDefinitionKey).orElse(null), () -> {
                    RegionalEvent current = repository.event(event.id());
                    return current == null
                            ? null
                            : current.phases().stream().filter(candidate -> candidate.id().equals(phase.id()))
                                    .findFirst().map(candidate -> candidate.actors().get(actor)).orElse(null);
                }, actions -> saveActor(event.id(), phase.id(), actor, actions, true), p -> {
                    RegionalEvent current = repository.event(event.id());
                    if (current == null)
                        open(p);
                    else
                        openEvent(p, current, phase.id(), 0);
                }, "Event"));
    }

    private void saveActor(UUID eventId, UUID phaseId, UUID actor, List<BehaviourAction> actions, boolean edit) {
        RegionalEvent event = repository.event(eventId);
        if (event == null)
            return;
        List<Phase> phases = new ArrayList<>(event.phases());
        for (int index = 0; index < phases.size(); index++) {
            Phase phase = phases.get(index);
            if (!phase.id().equals(phaseId))
                continue;
            Map<UUID, List<BehaviourAction>> rows = new LinkedHashMap<>(phase.actors());
            if (actions == null)
                rows.remove(actor);
            else if (edit && rows.containsKey(actor))
                rows.put(actor, actions);
            else if (!edit)
                rows.putIfAbsent(actor, actions);
            phases.set(index, new Phase(phaseId, rows));
            repository.save(event.withPhases(phases));
            return;
        }
    }

    private enum Screen {
        REGIONS, REGION, EVENT, ACTORS, TRIGGERS, PERIODS, CONFIRM_REGION_DELETE
    }

    private record Entry(UUID id, ItemStack icon) {
    }

    private record View(Screen screen, UUID region, UUID event, UUID phase, int page,
            List<UUID> entries) implements GuiHolder {
    }
}
