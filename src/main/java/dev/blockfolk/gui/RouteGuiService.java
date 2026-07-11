package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.RoutePoint;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.RouteRepository;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import net.kyori.adventure.text.Component;

public final class RouteGuiService implements Listener {

    private static final int PAGE_SIZE = 45;

    private final JavaPlugin plugin;
    private final RouteRepository routeRepository;
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final ChatInputService chatInputService;
    private final Consumer<Player> mainGuiOpener;
    private final NamespacedKey wandRouteKey;
    private final NamespacedKey wandTokenKey;
    private final Map<UUID, EditSession> editSessions = new HashMap<>();
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
    }

    public void openRoutes(Player player) {
        openRoutes(player, 0);
    }

    public void start() {
        if (markerTask != null) {
            markerTask.cancel();
        }
        markerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::showRoutePoints, 0L, 10L);
    }

    public void openRoutes(Player player, int requestedPage) {
        finishEditing(player, false);
        List<NpcRoute> routes = new ArrayList<>(routeRepository.findAll());
        int pages = Math.max(1, (routes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        Inventory inventory = Bukkit.createInventory(new RoutesHolder(page), 54, Component.text("Blockfolk Routes"));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, routes.size());
        for (int index = from; index < to; index++) {
            NpcRoute route = routes.get(index);
            long assignments = definitionRepository.findAll().stream()
                    .filter(definition -> route.getKey().equals(definition.getMovementProfile().routeKey()))
                    .count();
            inventory.setItem(index - from, routeItem(route, List.of(
                    ChatColor.DARK_GRAY + "Key: " + route.getKey(),
                    ChatColor.GRAY + "Key points: " + ChatColor.WHITE + route.getPoints().size(),
                    ChatColor.GRAY + "Assigned presets: " + ChatColor.WHITE + assignments,
                    ChatColor.AQUA + "Middle-click: set icon from main hand",
                    ChatColor.YELLOW + "Left-click: edit points",
                    ChatColor.RED + "Shift-right-click: remove route"
            )));
        }
        inventory.setItem(45, item(Material.PLAYER_HEAD, "Manage NPCs", List.of(
                ChatColor.GRAY + "Return to the main NPC menu",
                ChatColor.YELLOW + "Click to manage NPC presets"
        )));
        if (page > 0) {
            inventory.setItem(47, item(Material.ARROW, "Previous Page", List.of()));
        }
        inventory.setItem(49, item(Material.COMPASS, "Route Overview", List.of(
                ChatColor.GRAY + "Routes: " + ChatColor.WHITE + routes.size(),
                ChatColor.GRAY + "NPCs start at their nearest point",
                ChatColor.GRAY + "then follow nearest unvisited points in a loop"
        )));
        inventory.setItem(51, item(Material.EMERALD, "Create Route", List.of(
                ChatColor.GRAY + "Creates an empty route",
                ChatColor.YELLOW + "Click, then enter its name in chat"
        )));
        if (page + 1 < pages) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", List.of()));
        }
        player.openInventory(inventory);
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
            handleRoutesClick(event, player, routesHolder.page());
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
        if (holder instanceof RoutesHolder || holder instanceof DeleteRouteHolder) {
            event.setCancelled(true);
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
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            finishEditing(player, true);
            Bukkit.getScheduler().runTask(plugin, () -> openRoutes(player));
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        NpcRoute route = routeRepository.find(session.routeKey()).orElse(null);
        if (route == null) {
            finishEditing(player, false);
            player.sendMessage(Component.text("That route no longer exists."));
            return;
        }

        RoutePoint point = RoutePoint.fromBlock(event.getClickedBlock());
        boolean changed;
        if (player.isSneaking()) {
            changed = route.removePoint(point);
            player.sendMessage(Component.text(changed ? "Removed route point." : "That block is not a route point."));
        } else {
            try {
                changed = route.addPoint(point);
                player.sendMessage(Component.text(changed ? "Added route point " + route.getPoints().size() + "." : "That block is already a route point."));
            } catch (IllegalArgumentException exception) {
                player.sendMessage(Component.text("A route cannot contain blocks from different worlds."));
                return;
            }
        }
        if (changed) {
            routeRepository.save(route);
            player.spawnParticle(Particle.END_ROD, point.x() + 0.5, point.y() + 1.1, point.z() + 0.5, 8, 0.2, 0.2, 0.2, 0.0);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.7f, player.isSneaking() ? 0.7f : 1.2f);
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
        player.sendMessage(Component.text("Finished editing the route."));
        Bukkit.getScheduler().runTask(plugin, () -> openRoutes(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finishEditing(event.getPlayer(), false);
    }

    private void handleRoutesClick(InventoryClickEvent event, Player player, int page) {
        if (event.getRawSlot() == 45) {
            mainGuiOpener.accept(player);
            return;
        }
        if (event.getRawSlot() == 47) {
            openRoutes(player, page - 1);
            return;
        }
        if (event.getRawSlot() == 51) {
            chatInputService.request(player, "Enter a new route name:", value -> {
                NpcRoute route = NpcRoute.create(value);
                if (routeRepository.find(route.getKey()).isPresent()) {
                    player.sendMessage(Component.text("A route with that key already exists."));
                    openRoutes(player, page);
                    return;
                }
                routeRepository.save(route);
                beginEditing(player, route);
            });
            return;
        }
        if (event.getRawSlot() == 53) {
            openRoutes(player, page + 1);
            return;
        }
        List<NpcRoute> routes = new ArrayList<>(routeRepository.findAll());
        int index = page * PAGE_SIZE + event.getRawSlot();
        if (event.getRawSlot() >= PAGE_SIZE || index < 0 || index >= routes.size()) {
            return;
        }
        NpcRoute route = routes.get(index);
        if (event.getClick() == ClickType.MIDDLE) {
            route.setIcon(player.getInventory().getItemInMainHand());
            routeRepository.save(route);
            player.sendMessage(Component.text(route.getIcon() == null ? "Route icon cleared." : "Route icon updated."));
            openRoutes(player, page);
        } else if (event.isRightClick() && event.isShiftClick()) {
            openDeleteConfirmation(player, route, page);
        } else {
            beginEditing(player, route);
        }
    }

    private void handleDeleteClick(InventoryClickEvent event, Player player, DeleteRouteHolder holder) {
        if (event.getRawSlot() == 15) {
            openRoutes(player, holder.page());
            return;
        }
        if (event.getRawSlot() != 11) {
            return;
        }
        NpcRoute route = routeRepository.find(holder.routeKey()).orElse(null);
        if (route == null) {
            openRoutes(player, holder.page());
            return;
        }
        int unassigned = 0;
        for (NpcDefinition definition : definitionRepository.findAll()) {
            if (!route.getKey().equals(definition.getMovementProfile().routeKey())) {
                continue;
            }
            definition.setMovementProfile(definition.getMovementProfile().withoutRoute());
            definitionRepository.save(definition);
            instanceRegistry.refreshDefinition(definition);
            unassigned++;
        }
        routeRepository.delete(route);
        player.sendMessage(Component.text("Deleted route and unassigned " + unassigned + " NPC preset(s)."));
        openRoutes(player, holder.page());
    }

    private void openDeleteConfirmation(Player player, NpcRoute route, int page) {
        Inventory inventory = Bukkit.createInventory(new DeleteRouteHolder(route.getKey(), page), 27,
                Component.text("Delete route: " + route.getDisplayName()));
        inventory.setItem(11, item(Material.LIME_CONCRETE, "Confirm", List.of(
                ChatColor.RED + "Permanently delete this route",
                ChatColor.GRAY + "NPC presets using it will be unassigned"
        )));
        inventory.setItem(15, item(Material.RED_CONCRETE, "Cancel", List.of(ChatColor.GRAY + "Nothing will be changed")));
        player.openInventory(inventory);
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
        player.sendMessage(Component.text("Editing route '" + route.getDisplayName() + "'."));
        player.sendMessage(Component.text("Right-click blocks to add, sneak-right-click to remove, and left-click or drop the shard to finish."));
        showRoutePoints(player, route);
    }

    private ItemStack createWand(NpcRoute route, UUID token) {
        ItemStack wand = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Route Editor: " + route.getDisplayName());
        meta.setLore(List.of(
                ChatColor.GRAY + "Unique editor: " + token.toString().substring(0, 8),
                ChatColor.YELLOW + "Right-click a block: add point",
                ChatColor.YELLOW + "Sneak-right-click: remove point",
                ChatColor.LIGHT_PURPLE + "Points stay highlighted while editing",
                ChatColor.GREEN + "Left-click or drop: finish"
        ));
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
        String routeKey = meta.getPersistentDataContainer().get(wandRouteKey, PersistentDataType.STRING);
        String token = meta.getPersistentDataContainer().get(wandTokenKey, PersistentDataType.STRING);
        return session.routeKey().equals(routeKey) && session.token().toString().equals(token) ? session : null;
    }

    private void finishEditing(Player player, boolean notify) {
        EditSession session = editSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (matches(item, session)) {
                player.getInventory().setItem(slot, null);
            }
        }
        if (notify) {
            player.sendMessage(Component.text("Finished editing the route."));
        }
    }

    private boolean matches(ItemStack item, EditSession session) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD || !item.hasItemMeta()) {
            return false;
        }
        String routeKey = item.getItemMeta().getPersistentDataContainer().get(wandRouteKey, PersistentDataType.STRING);
        String token = item.getItemMeta().getPersistentDataContainer().get(wandTokenKey, PersistentDataType.STRING);
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
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(190, 80, 255), 1.5f);
        for (RoutePoint point : route.getPoints()) {
            Location location = point.toWalkingLocation();
            if (location == null || location.getWorld() != player.getWorld()) {
                continue;
            }
            location.add(0.0, 0.1, 0.0);
            player.spawnParticle(Particle.DUST, location, 5, 0.22, 0.08, 0.22, 0.0, dust);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack routeItem(NpcRoute route, List<String> lore) {
        ItemStack icon = route.getIcon();
        ItemStack result = icon == null ? new ItemStack(Material.AMETHYST_CLUSTER) : icon;
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + route.getDisplayName());
        meta.setLore(lore);
        result.setItemMeta(meta);
        return result;
    }

    private record EditSession(String routeKey, UUID token) {

    }

    private record RoutesHolder(int page) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record DeleteRouteHolder(String routeKey, int page) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
