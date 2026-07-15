package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.RoutePoint;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.RouteRepository;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.UiText;
import net.kyori.adventure.text.Component;

public final class RouteGuiService implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final double PATH_PARTICLE_SPACING = 0.6;
    private static final Color PATH_COLOR = Color.fromRGB(120, 210, 255);
    private static final Color PATH_DIRECTION_COLOR = Color.fromRGB(75, 255, 145);
    private final JavaPlugin plugin;
    private final RouteRepository routeRepository;
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final ChatInputService chatInputService;
    private final Consumer<Player> mainGuiOpener;
    private final NamespacedKey wandRouteKey;
    private final NamespacedKey wandTokenKey;
    private final NamespacedKey reorderRouteKey;
    private final Map<UUID, EditSession> editSessions = new HashMap<>();
    private WaypointActionOpener waypointActionOpener;
    private BukkitTask markerTask;

    public RouteGuiService(
            JavaPlugin plugin,
            RouteRepository routeRepository,
            NpcDefinitionRepository definitionRepository,
            NpcInstanceRegistry instanceRegistry,
            ChatInputService chatInputService,
            Consumer<Player> mainGuiOpener
    ) {
        this.plugin = plugin;
        this.routeRepository = routeRepository;
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.chatInputService = chatInputService;
        this.mainGuiOpener = mainGuiOpener;
        this.wandRouteKey = new NamespacedKey(plugin, "route-editor-route");
        this.wandTokenKey = new NamespacedKey(plugin, "route-editor-token");
        this.reorderRouteKey = new NamespacedKey(plugin, "reorder-route");
    }

    public void setWaypointActionOpener(WaypointActionOpener waypointActionOpener) {
        this.waypointActionOpener = waypointActionOpener;
    }

    public void openRoutes(Player player) {
        openRoutes(player, "", 0);
    }

    public void start() {
        if (markerTask != null) {
            markerTask.cancel();
        }
        markerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::showRoutePoints, 0L, 10L);
    }

    public void openRoutes(Player player, int requestedPage) {
        openRoutes(player, "", requestedPage);
    }

    public void createRoute(Player player, String folder, Consumer<NpcRoute> onCreated) {
        String normalizedFolder = folder == null ? "" : folder;
        chatInputService.request(player, "Enter the full route name (use / for groups):", value -> {
            try {
                String name = normalizedFolder.isEmpty() || value.contains("/")
                        ? value : normalizedFolder + "/" + value;
                NpcRoute route = NpcRoute.create(name);
                if (routeRepository.find(route.getKey()).isPresent()) {
                    player.sendMessage(UiText.error("A route with that key already exists."));
                    return;
                }
                routeRepository.save(route);
                onCreated.accept(route);
                beginEditing(player, route);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(UiText.error(exception.getMessage()));
            }
        });
    }

    private void openRoutes(Player player, String folder, int requestedPage) {
        finishEditing(player, false);
        List<RouteEntry> entries = routeEntries(folder);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        String title = folder.isEmpty() ? "Blockfolk Routes" : "Routes: " + folder;
        Inventory inventory = Bukkit.createInventory(new RoutesHolder(folder, page), 54, UiText.title(title));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        for (int index = from; index < to; index++) {
            RouteEntry entry = entries.get(index);
            if (entry.folder()) {
                inventory.setItem(index - from, item(Material.CHEST, entry.label(), List.of(
                        LegacyText.GRAY + "" + entry.childCount() + " route(s)",
                        LegacyText.DARK_GRAY + entry.path(),
                        LegacyText.YELLOW + "Click to open")));
                continue;
            }
            NpcRoute route = entry.route();
            inventory.setItem(index - from, routeItem(route, List.of(
                    LegacyText.DARK_GRAY + "Key: " + route.getKey(),
                    LegacyText.GRAY + "Key points: " + LegacyText.WHITE + route.getPoints().size(),
                    LegacyText.AQUA + "Middle-click: set icon from main hand",
                    LegacyText.YELLOW + "Left-click: edit points",
                    LegacyText.RED + "Shift-right-click: remove route"
            )));
        }
        inventory.setItem(45, item(folder.isEmpty() ? Material.PLAYER_HEAD : Material.ARROW,
                folder.isEmpty() ? "Manage NPCs" : "Up One Group", List.of()));
        if (page > 0) {
            inventory.setItem(47, item(Material.ARROW, "Previous Page", List.of()));
        }
        inventory.setItem(49, item(Material.COMPASS, "Route Overview", List.of(
                LegacyText.GRAY + "Routes: " + LegacyText.WHITE + routeRepository.findAll().size(),
                LegacyText.GRAY + "Group: " + LegacyText.WHITE + (folder.isEmpty() ? "Root" : folder),
                LegacyText.GRAY + "NPCs start at their nearest point",
                LegacyText.GRAY + "then follow nearest unvisited points in a loop",
                LegacyText.YELLOW + "Click to reorder routes"
        )));
        inventory.setItem(51, item(Material.EMERALD, "Create Route", List.of(
                LegacyText.GRAY + "Use / in the name to create groups",
                LegacyText.YELLOW + "Click, then enter the full route name"
        )));
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private void openReorder(Player player, String returnFolder, int returnPage) {
        List<String> keys = routeRepository.findAll().stream().map(NpcRoute::getKey).toList();
        openReorder(player, new ReorderRoutesHolder(new ArrayList<>(keys), returnFolder, returnPage), 0);
    }

    private void openReorder(Player player, ReorderRoutesHolder holder, int requestedPage) {
        int pages = Math.max(1, (holder.keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        holder.page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(holder, 54, UiText.title("Reorder Routes"));
        renderReorder(inventory, holder);
        player.openInventory(inventory);
        restoreReorderCursor(player, holder);
    }

    private void renderReorder(Inventory inventory, ReorderRoutesHolder holder) {
        inventory.clear();
        int pages = Math.max(1, (holder.keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = holder.page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, holder.keys.size());
        for (int index = from; index < to; index++) {
            String key = holder.keys.get(index);
            if (key.equals(holder.selectedKey)) continue;
            NpcRoute route = routeRepository.find(key).orElse(null);
            if (route != null) inventory.setItem(index - from, reorderItem(route, index));
        }
        if (holder.page > 0) inventory.setItem(45, item(Material.ARROW, "Previous Page", List.of()));
        inventory.setItem(48, item(Material.LIME_CONCRETE, "Save Order", List.of(
                LegacyText.GRAY + "Apply this order to the routes browser")));
        inventory.setItem(50, item(Material.RED_CONCRETE, "Cancel", List.of(
                LegacyText.GRAY + "Discard all ordering changes")));
        if (holder.page + 1 < pages) inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        GuiLayout.fillMainBar(inventory);
    }

    private ItemStack reorderItem(NpcRoute route, int index) {
        ItemStack icon = routeItem(route, List.of(
                LegacyText.DARK_GRAY + route.getKey(),
                LegacyText.GRAY + "Position: " + LegacyText.WHITE + (index + 1),
                LegacyText.YELLOW + "Pick up and drop to move"));
        ItemMeta meta = icon.getItemMeta();
        meta.getPersistentDataContainer().set(reorderRouteKey, PersistentDataType.STRING, route.getKey());
        icon.setItemMeta(meta);
        return icon;
    }

    public void stop() {
        if (markerTask != null) {
            markerTask.cancel();
            markerTask = null;
        }
        for (UUID playerId : List.copyOf(editSessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                finishEditing(player, false);
            }
        }
        editSessions.clear();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof RoutesHolder routesHolder) {
            event.setCancelled(true);
            if (!isTopInventoryClick(event)) {
                return;
            }
            handleRoutesClick(event, player, routesHolder);
        } else if (holder instanceof ReorderRoutesHolder reorderHolder) {
            event.setCancelled(true);
            handleReorderClick(event, player, reorderHolder);
        } else if (holder instanceof DeleteRouteHolder deleteHolder) {
            event.setCancelled(true);
            if (isTopInventoryClick(event)) {
                handleDeleteClick(event, player, deleteHolder);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof RoutesHolder || holder instanceof ReorderRoutesHolder
                || holder instanceof DeleteRouteHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ReorderRoutesHolder) {
            clearReorderCursor(event.getPlayer());
        }
    }

    @EventHandler
    public void onRoutePointClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        EditSession session = validSession(player, event.getItem());
        if (session == null) {
            return;
        }
        Action action = event.getAction();
        if (event.getClickedBlock() == null
                || (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        NpcRoute route = routeRepository.find(session.routeKey()).orElse(null);
        if (route == null) {
            finishEditing(player, false);
            player.sendMessage(UiText.error("That route no longer exists."));
            return;
        }

        RoutePoint point = RoutePoint.fromBlock(event.getClickedBlock());
        boolean changed = false;
        if (action == Action.LEFT_CLICK_BLOCK) {
            try {
                changed = route.addPoint(point);
                if (changed) {
                    player.sendMessage(UiText.success("Added route point " + route.getPoints().size() + "."));
                } else {
                    RoutePoint existing = route.findPoint(point).orElseThrow();
                    player.sendMessage(UiText.warning("That block is already a route point. Actions: "
                            + actionSummary(existing) + "."));
                }
            } catch (IllegalArgumentException exception) {
                player.sendMessage(UiText.error("A route cannot contain blocks from different worlds."));
                return;
            }
        } else if (!player.isSneaking()) {
            changed = route.removePoint(point);
            player.sendMessage(UiText.info(changed ? "Removed route point." : "That block is not a route point."));
        } else {
            RoutePoint existing = route.findPoint(point).orElse(null);
            if (existing == null) {
                player.sendMessage(UiText.warning("That block is not a route point."));
                return;
            }
            if (waypointActionOpener == null) {
                player.sendMessage(UiText.error("The waypoint action editor is not available."));
                return;
            }
            waypointActionOpener.open(player, route.getKey(), existing);
            return;
        }
        if (changed) {
            routeRepository.save(route);
            player.spawnParticle(Particle.END_ROD, point.x() + 0.5, point.y() + 1.1, point.z() + 0.5, 8, 0.2, 0.2, 0.2, 0.0);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.7f, action == Action.RIGHT_CLICK_BLOCK ? 0.7f : 1.2f);
        }
    }

    @EventHandler
    public void onWandDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (validSession(player, event.getItemDrop().getItemStack()) == null) {
            return;
        }
        event.getItemDrop().remove();
        editSessions.remove(player.getUniqueId());
        player.sendMessage(UiText.success("Finished editing the route."));
        Bukkit.getScheduler().runTask(plugin, () -> openRoutes(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finishEditing(event.getPlayer(), false);
    }

    private void handleRoutesClick(InventoryClickEvent event, Player player, RoutesHolder holder) {
        String folder = holder.folder();
        int page = holder.page();
        if (event.getRawSlot() == 45) {
            if (folder.isEmpty()) mainGuiOpener.accept(player);
            else openRoutes(player, parent(folder), 0);
            return;
        }
        if (event.getRawSlot() == 47) {
            openRoutes(player, folder, page - 1);
            return;
        }
        if (event.getRawSlot() == 49) {
            openReorder(player, folder, page);
            return;
        }
        if (event.getRawSlot() == 51) {
            chatInputService.request(player, "Enter the full route name (use / for groups):", value -> {
                try {
                    NpcRoute route = NpcRoute.create(value);
                    if (routeRepository.find(route.getKey()).isPresent()) {
                        player.sendMessage(UiText.error("A route with that key already exists."));
                        openRoutes(player, folder, page);
                        return;
                    }
                    routeRepository.save(route);
                    beginEditing(player, route);
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(UiText.error(exception.getMessage()));
                    openRoutes(player, folder, page);
                }
            });
            return;
        }
        if (event.getRawSlot() == 53) {
            openRoutes(player, folder, page + 1);
            return;
        }
        List<RouteEntry> entries = routeEntries(folder);
        int index = page * PAGE_SIZE + event.getRawSlot();
        if (event.getRawSlot() >= PAGE_SIZE || index < 0 || index >= entries.size()) {
            return;
        }
        RouteEntry entry = entries.get(index);
        if (entry.folder()) {
            openRoutes(player, entry.path(), 0);
            return;
        }
        NpcRoute route = entry.route();
        if (event.getClick() == ClickType.MIDDLE) {
            route.setIcon(player.getInventory().getItemInMainHand());
            routeRepository.save(route);
            player.sendMessage(UiText.info(route.getIcon() == null ? "Route icon cleared." : "Route icon updated."));
            openRoutes(player, folder, page);
        } else if (event.isRightClick() && event.isShiftClick()) {
            openDeleteConfirmation(player, route, folder, page);
        } else {
            beginEditing(player, route);
        }
    }

    private void handleReorderClick(InventoryClickEvent event, Player player, ReorderRoutesHolder holder) {
        event.setCancelled(true);
        if (!isTopInventoryClick(event)) return;
        int slot = event.getRawSlot();
        if (slot == 45 && holder.page > 0) {
            openReorder(player, holder, holder.page - 1);
            return;
        }
        if (slot == 53 && (holder.page + 1) * PAGE_SIZE < holder.keys.size()) {
            openReorder(player, holder, holder.page + 1);
            return;
        }
        if (slot == 48) {
            clearReorderSelection(player, holder);
            try {
                routeRepository.reorder(holder.keys);
                player.sendMessage(UiText.success("Route order saved."));
                openRoutes(player, holder.returnFolder, holder.returnPage);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(UiText.info(
                        "The route list changed while you were editing. Please reorder it again."));
                openReorder(player, holder.returnFolder, holder.returnPage);
            }
            return;
        }
        if (slot == 50) {
            clearReorderSelection(player, holder);
            openRoutes(player, holder.returnFolder, holder.returnPage);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) return;
        int targetIndex = Math.min(holder.page * PAGE_SIZE + slot, holder.keys.size() - 1);
        if (targetIndex < 0) return;
        if (holder.selectedKey == null) {
            int sourceIndex = holder.page * PAGE_SIZE + slot;
            if (sourceIndex >= holder.keys.size() || !isEmpty(player.getItemOnCursor())) return;
            holder.selectedKey = holder.keys.get(sourceIndex);
            event.getView().getTopInventory().setItem(slot, null);
            restoreReorderCursor(player, holder);
            return;
        }
        int sourceIndex = holder.keys.indexOf(holder.selectedKey);
        if (sourceIndex >= 0 && sourceIndex != targetIndex) {
            String moved = holder.keys.remove(sourceIndex);
            holder.keys.add(targetIndex, moved);
        }
        clearReorderSelection(player, holder);
        renderReorder(event.getView().getTopInventory(), holder);
    }

    private void restoreReorderCursor(Player player, ReorderRoutesHolder holder) {
        if (holder.selectedKey == null) return;
        routeRepository.find(holder.selectedKey).ifPresent(route ->
                player.setItemOnCursor(reorderItem(route, holder.keys.indexOf(holder.selectedKey))));
    }

    private void clearReorderSelection(Player player, ReorderRoutesHolder holder) {
        holder.selectedKey = null;
        clearReorderCursor(player);
    }

    private void clearReorderCursor(org.bukkit.entity.HumanEntity player) {
        ItemStack cursor = player.getItemOnCursor();
        ItemMeta meta = isEmpty(cursor) ? null : cursor.getItemMeta();
        if (meta != null
                && meta.getPersistentDataContainer()
                        .has(reorderRouteKey, PersistentDataType.STRING)) {
            player.setItemOnCursor(null);
        }
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private void handleDeleteClick(InventoryClickEvent event, Player player, DeleteRouteHolder holder) {
        if (event.getRawSlot() == 15) {
            openRoutes(player, holder.folder(), holder.page());
            return;
        }
        if (event.getRawSlot() != 11) {
            return;
        }
        NpcRoute route = routeRepository.find(holder.routeKey()).orElse(null);
        if (route == null) {
            openRoutes(player, holder.folder(), holder.page());
            return;
        }
        routeRepository.delete(route);
        player.sendMessage(UiText.success("Deleted route '" + route.getDisplayName() + "'."));
        openRoutes(player, holder.folder(), holder.page());
    }

    private void openDeleteConfirmation(Player player, NpcRoute route, String folder, int page) {
        Inventory inventory = Bukkit.createInventory(new DeleteRouteHolder(route.getKey(), folder, page), 27,
                UiText.title("Delete Route", route.getDisplayName()));
        inventory.setItem(11, item(Material.LIME_CONCRETE, "Confirm", List.of(
                LegacyText.RED + "Permanently delete this route",
                LegacyText.GRAY + "NPC presets using it will be unassigned"
        )));
        inventory.setItem(15, item(Material.RED_CONCRETE, "Cancel", List.of(LegacyText.GRAY + "Nothing will be changed")));
        GuiLayout.fillMainBar(inventory);
        player.openInventory(inventory);
    }

    private List<RouteEntry> routeEntries(String folder) {
        String prefix = folder.isEmpty() ? "" : folder + "/";
        Map<String, RouteEntry> result = new LinkedHashMap<>();
        for (NpcRoute route : routeRepository.findAll()) {
            if (!route.getKey().startsWith(prefix)) continue;
            String rest = route.getKey().substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                String label = rest.substring(0, slash);
                String path = prefix + label;
                RouteEntry old = result.get(path);
                result.put(path, new RouteEntry(true, path, label,
                        old == null ? 1 : old.childCount() + 1, null));
            } else {
                result.put(route.getKey(), new RouteEntry(false, route.getKey(), rest, 0, route));
            }
        }
        return new ArrayList<>(result.values());
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private void beginEditing(Player player, NpcRoute route) {
        finishEditing(player, false);
        UUID token = UUID.randomUUID();
        editSessions.put(player.getUniqueId(), new EditSession(route.getKey(), token));

        ItemStack held = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(createWand(route, token));
        if (!held.getType().isAir()) {
            player.getInventory().addItem(held).values().forEach(leftover
                    -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        player.closeInventory();
        player.sendMessage(UiText.info("Editing route '" + route.getDisplayName() + "'."));
        player.sendMessage(UiText.prompt("Left-click blocks to add, right-click to remove, shift-right-click to edit waypoint actions, and drop the shard to save and finish."));
        showRoutePoints(player, route);
    }

    private ItemStack createWand(NpcRoute route, UUID token) {
        ItemStack wand = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(LegacyText.component(LegacyText.LIGHT_PURPLE + "Route Editor: " + route.getDisplayName()));
        meta.lore(LegacyText.components(List.of(
                LegacyText.GRAY + "Unique editor: " + token.toString().substring(0, 8),
                LegacyText.YELLOW + "Left-click a block: add point",
                LegacyText.YELLOW + "Right-click: remove point",
                LegacyText.GOLD + "Shift-right-click: edit point actions",
                LegacyText.LIGHT_PURPLE + "Points and walking order stay highlighted",
                LegacyText.GREEN + "Drop: save and finish"
        )));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(wandRouteKey, PersistentDataType.STRING, route.getKey());
        meta.getPersistentDataContainer().set(wandTokenKey, PersistentDataType.STRING, token.toString());
        wand.setItemMeta(meta);
        return wand;
    }

    private EditSession validSession(Player player, ItemStack item) {
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null || item == null || item.getType() != Material.AMETHYST_SHARD || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String routeKey = meta.getPersistentDataContainer().get(wandRouteKey, PersistentDataType.STRING);
        String token = meta.getPersistentDataContainer().get(wandTokenKey, PersistentDataType.STRING);
        return session.routeKey().equals(routeKey) && session.token().toString().equals(token) ? session : null;
    }

    private void finishEditing(Player player, boolean notify) {
        EditSession session = editSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        chatInputService.cancel(player);
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (matches(item, session)) {
                player.getInventory().setItem(slot, null);
            }
        }
        if (notify) {
            player.sendMessage(UiText.success("Finished editing the route."));
        }
    }

    private boolean matches(ItemStack item, EditSession session) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String routeKey = meta.getPersistentDataContainer().get(wandRouteKey, PersistentDataType.STRING);
        String token = meta.getPersistentDataContainer().get(wandTokenKey, PersistentDataType.STRING);
        return session.routeKey().equals(routeKey) && session.token().toString().equals(token);
    }

    private boolean isTopInventoryClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        return slot >= 0 && slot < event.getView().getTopInventory().getSize();
    }

    private void showRoutePoints() {
        for (Map.Entry<UUID, EditSession> entry : editSessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            NpcRoute route = routeRepository.find(entry.getValue().routeKey()).orElse(null);
            if (player != null && player.isOnline() && route != null) {
                showRoutePoints(player, route);
            }
        }
    }

    private void showRoutePoints(Player player, NpcRoute route) {
        List<RoutePoint> orderedPoints = route.logicallyOrdered(player.getLocation());
        List<Location> locations = new ArrayList<>();
        for (RoutePoint point : orderedPoints) {
            Location location = point.toWalkingLocation();
            if (location == null || location.getWorld() != player.getWorld()) {
                continue;
            }
            location.add(0.0, 0.1, 0.0);
            locations.add(location);
            Color color = point.actions().isEmpty() ? Color.fromRGB(190, 80, 255) : Color.fromRGB(255, 170, 30);
            Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
            player.spawnParticle(Particle.DUST, location, 5, 0.22, 0.08, 0.22, 0.0, dust);
        }
        if (locations.size() < 2) {
            return;
        }
        for (int index = 0; index < locations.size(); index++) {
            Location from = locations.get(index);
            Location to = locations.get((index + 1) % locations.size());
            showPathSegment(player, from, to);
        }
    }

    private void showPathSegment(Player player, Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 0.01) {
            return;
        }
        Vector unit = direction.clone().multiply(1.0 / distance);
        Particle.DustOptions pathDust = new Particle.DustOptions(PATH_COLOR, 0.75f);
        int steps = Math.max(1, (int) Math.ceil(distance / PATH_PARTICLE_SPACING));
        for (int step = 1; step < steps; step++) {
            Location particle = from.clone().add(unit.clone().multiply(distance * step / steps));
            player.spawnParticle(Particle.DUST, particle, 1, 0.0, 0.0, 0.0, 0.0, pathDust);
        }

        // Put a small chevron near the end of every segment so loops and
        // crossing lines still communicate their direction clearly.
        Location arrowTip = from.clone().add(unit.clone().multiply(distance * 0.72));
        Vector side = new Vector(-unit.getZ(), 0.0, unit.getX());
        if (side.lengthSquared() < 0.01) {
            side = new Vector(1.0, 0.0, 0.0);
        } else {
            side.normalize();
        }
        Vector back = unit.clone().multiply(-0.45);
        Particle.DustOptions directionDust = new Particle.DustOptions(PATH_DIRECTION_COLOR, 1.1f);
        showShortParticleLine(player, arrowTip, arrowTip.clone().add(back).add(side.clone().multiply(0.25)), directionDust);
        showShortParticleLine(player, arrowTip, arrowTip.clone().add(back).subtract(side.clone().multiply(0.25)), directionDust);
    }

    private void showShortParticleLine(Player player, Location from, Location to, Particle.DustOptions dust) {
        Vector difference = to.toVector().subtract(from.toVector());
        for (int step = 0; step <= 3; step++) {
            Location particle = from.clone().add(difference.clone().multiply(step / 3.0));
            player.spawnParticle(Particle.DUST, particle, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private String actionSummary(RoutePoint point) {
        if (point.actions().isEmpty()) {
            return "none";
        }
        return point.actions().stream()
                .map(action -> action.value() == null
                ? action.type().displayName()
                : action.type().displayName() + " (" + action.value() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyText.component(LegacyText.GOLD + name));
        meta.lore(LegacyText.components(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack routeItem(NpcRoute route, List<String> lore) {
        ItemStack icon = route.getIcon();
        ItemStack result = icon == null ? new ItemStack(Material.RAIL) : icon;
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(LegacyText.component(LegacyText.GOLD + route.getDisplayName()));
        meta.lore(LegacyText.components(lore));
        result.setItemMeta(meta);
        return result;
    }

    private record EditSession(String routeKey, UUID token) {

    }

    @FunctionalInterface
    public interface WaypointActionOpener {

        void open(Player player, String routeKey, RoutePoint point);
    }

    private record RouteEntry(boolean folder, String path, String label, int childCount, NpcRoute route) { }

    private record RoutesHolder(String folder, int page) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class ReorderRoutesHolder implements InventoryHolder {
        private final List<String> keys;
        private final String returnFolder;
        private final int returnPage;
        private int page;
        private String selectedKey;

        private ReorderRoutesHolder(List<String> keys, String returnFolder, int returnPage) {
            this.keys = keys;
            this.returnFolder = returnFolder;
            this.returnPage = returnPage;
        }

        @Override
        public Inventory getInventory() { return null; }
    }

    private record DeleteRouteHolder(String routeKey, String folder, int page) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
