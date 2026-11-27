package dev.blockfolk.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.NpcRoute;
import dev.blockfolk.model.StoredLocation;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.RouteRepository;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.util.LegacyText;
import dev.blockfolk.util.ResolvedSkin;
import dev.blockfolk.util.SkinResolver;
import dev.blockfolk.util.SkinTextureUtil;
import dev.blockfolk.util.UiText;

/**
 * Editor for one variant of a preset: a spawned instance that shares its
 * identity (name, equipment, AI, memories, combat) with its preset but can have
 * its own label, skin, spawn point, route swaps, and behaviour rows. A variant
 * that has drifted far enough can be detached into an NPC of its own.
 */
public final class VariantGuiService implements Listener {

    private static final int LABEL_SLOT = 10;
    private static final int SKIN_SLOT = 11;
    private static final int SPAWN_SLOT = 12;
    private static final int ROUTES_SLOT = 14;
    private static final int BEHAVIOUR_SLOT = 15;
    private static final int TELEPORT_SLOT = 16;
    private static final int INFO_SLOT = 4;
    private static final int EDITOR_BACK_SLOT = 22;
    private static final int DETACH_SLOT = 26;
    private static final int LIST_SIZE = 45;

    private final Plugin plugin;
    private final NpcDefinitionRepository definitions;
    private final RouteRepository routes;
    private final NpcInstanceRegistry instances;
    private final ChatInputService input;
    private final SkinResolver skinResolver;
    private final GuiService.RouteCreator routeCreator;
    private final BiConsumer<Player, NpcDefinition> openVariantList;
    private final VariantAction openBehaviour;
    private final VariantAction beginDetach;
    private final Map<UUID, String> pendingSkinUrls = new HashMap<>();

    /** Something the variant editor hands back to the main GUI. */
    @FunctionalInterface
    public interface VariantAction {
        void run(Player player, NpcDefinition definition, NpcInstance variant);
    }

    private interface Holder extends InventoryHolder {
        String definitionKey();

        UUID instanceId();

        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private record EditorHolder(String definitionKey, UUID instanceId) implements Holder {
    }

    private record RoutesHolder(String definitionKey, UUID instanceId) implements Holder {
    }

    private record PickHolder(String definitionKey, UUID instanceId, String presetRouteKey) implements Holder {
    }

    public VariantGuiService(Plugin plugin, NpcDefinitionRepository definitions, RouteRepository routes,
            NpcInstanceRegistry instances, ChatInputService input, SkinResolver skinResolver,
            GuiService.RouteCreator routeCreator, BiConsumer<Player, NpcDefinition> openVariantList,
            VariantAction openBehaviour, VariantAction beginDetach) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.routes = routes;
        this.instances = instances;
        this.input = input;
        this.skinResolver = skinResolver;
        this.routeCreator = routeCreator;
        this.openVariantList = openVariantList;
        this.openBehaviour = openBehaviour;
        this.beginDetach = beginDetach;
    }

    /** The name a variant is listed under: its label, else "Variant N". */
    public static String variantName(NpcInstance instance, int index) {
        return instance.getLabel() != null ? instance.getLabel() : "Variant " + (index + 1);
    }

    /** One-line summaries of what a variant overrides, for list lore. */
    public List<String> overrideSummary(NpcInstance instance, NpcDefinition definition) {
        List<String> lines = new ArrayList<>();
        lines.add(LegacyText.GRAY + "Skin: "
                + (instance.hasSkinOverride() ? LegacyText.AQUA + "Own skin" : LegacyText.WHITE + "From preset"));
        Map<String, String> swaps = instance.getRouteSwaps();
        if (swaps.isEmpty()) {
            lines.add(LegacyText.GRAY + "Routes: " + LegacyText.WHITE + "From preset");
        } else {
            swaps.forEach((from, to) -> lines.add(LegacyText.GRAY + "Route: " + LegacyText.WHITE + routeName(from)
                    + LegacyText.GRAY + " → " + LegacyText.AQUA + routeName(to)));
        }
        lines.addAll(behaviourSummary(instance));
        return lines;
    }

    private static List<String> behaviourSummary(NpcInstance instance) {
        java.util.Set<dev.blockfolk.model.BehaviourEvent> own = instance.getBehaviourOverrideEvents();
        if (own.isEmpty())
            return List.of(LegacyText.GRAY + "Behaviour: " + LegacyText.WHITE + "From preset");
        List<String> lines = new ArrayList<>();
        lines.add(LegacyText.GRAY + "Own behaviour:");
        own.stream().limit(5).forEach(event -> lines.add(LegacyText.AQUA + "• " + event.displayName()
                + (instance.getBehaviourOverride(event).isEmpty() ? LegacyText.DARK_GRAY + " (nothing)" : "")));
        if (own.size() > 5)
            lines.add(LegacyText.DARK_GRAY + "… and " + (own.size() - 5) + " more");
        return lines;
    }

    public ItemStack variantIcon(NpcInstance instance, NpcDefinition definition, String name, List<String> lore) {
        return NpcHeadUtil.applySkin(GuiItems.item(Material.PLAYER_HEAD, name, lore), instance, definition);
    }

    public void open(Player player, NpcDefinition definition, NpcInstance instance) {
        List<NpcInstance> siblings = new ArrayList<>(instances.findByDefinition(definition));
        String name = variantName(instance, Math.max(0, siblings.indexOf(instance)));
        Inventory inventory = Bukkit.createInventory(new EditorHolder(definition.getKey(), instance.getId()), 27,
                UiText.title("Variant", definition.getDisplayName() + " · " + name));

        List<String> info = new ArrayList<>();
        info.add(LegacyText.DARK_GRAY + instance.getId().toString());
        info.add(LegacyText.GRAY + formatLocation(instance.getStoredLocation()));
        info.add("");
        info.add(LegacyText.GRAY + "Shares name, equipment, AI, memories,");
        info.add(LegacyText.GRAY + "and combat with " + LegacyText.WHITE + definition.getDisplayName());
        inventory.setItem(INFO_SLOT, variantIcon(instance, definition, name, info));

        inventory.setItem(LABEL_SLOT,
                GuiItems.item(Material.NAME_TAG, "Label",
                        List.of(LegacyText.WHITE + (instance.getLabel() == null ? "None" : instance.getLabel()),
                                LegacyText.DARK_GRAY + "Only admins see it, e.g. 'North Gate'", "",
                                GuiItems.hint("Click", "set label"), GuiItems.danger("Shift-right-click", "clear"))));

        List<String> skinLore = new ArrayList<>();
        skinLore.add(instance.hasSkinOverride()
                ? LegacyText.AQUA + "Own skin: " + LegacyText.WHITE + abbreviate(instance.getSkinUrl())
                : LegacyText.WHITE + "From preset");
        skinLore.add("");
        skinLore.add(GuiItems.hint("Click", "set this variant's skin"));
        if (instance.hasSkinOverride())
            skinLore.add(GuiItems.danger("Shift-right-click", "use the preset's skin"));
        inventory.setItem(SKIN_SLOT, variantIcon(instance, definition, "Skin", skinLore));

        inventory.setItem(SPAWN_SLOT,
                GuiItems.item(Material.RESPAWN_ANCHOR, "Spawn Point",
                        List.of(LegacyText.WHITE + formatLocation(instance.getStoredSpawnLocation()),
                                LegacyText.DARK_GRAY + "Where it stands after restarts and respawns", "",
                                GuiItems.hint("Click", "move it and its spawn point to you"))));

        List<String> routeLore = new ArrayList<>();
        Map<String, String> swaps = instance.getRouteSwaps();
        if (swaps.isEmpty())
            routeLore.add(LegacyText.GRAY + "Routes: " + LegacyText.WHITE + "From preset");
        else
            swaps.forEach((from, to) -> routeLore.add(LegacyText.GRAY + "Route: " + LegacyText.WHITE
                    + routeName(from) + LegacyText.GRAY + " → " + LegacyText.AQUA + routeName(to)));
        routeLore.add(LegacyText.DARK_GRAY + "Walk another route wherever the");
        routeLore.add(LegacyText.DARK_GRAY + "preset's actions or AI pick one");
        routeLore.add("");
        routeLore.add(GuiItems.hint("Click", "choose route swaps"));
        inventory.setItem(ROUTES_SLOT, GuiItems.item(Material.FILLED_MAP, "Routes", routeLore));

        List<String> behaviourLore = new ArrayList<>(behaviourSummary(instance));
        behaviourLore.add(LegacyText.DARK_GRAY + "Own actions for chosen events, e.g. a");
        behaviourLore.add(LegacyText.DARK_GRAY + "different right-click here; the rest");
        behaviourLore.add(LegacyText.DARK_GRAY + "stays the preset's");
        behaviourLore.add("");
        behaviourLore.add(GuiItems.hint("Click", "edit this variant's behaviour"));
        ItemStack behaviourIcon = GuiItems.item(Material.WRITABLE_BOOK, "Behaviour", behaviourLore);
        inventory.setItem(BEHAVIOUR_SLOT, instance.getBehaviourOverrideEvents().isEmpty()
                ? behaviourIcon
                : GuiItems.glint(behaviourIcon));

        inventory.setItem(DETACH_SLOT, GuiItems.item(Material.SHEARS, "Detach as New NPC",
                List.of(LegacyText.GRAY + "Turns this variant into its own NPC",
                        LegacyText.GRAY + "with its skin, spawn, routes, and",
                        LegacyText.GRAY + "behaviour built in. It keeps today's",
                        LegacyText.GRAY + "memories but stops sharing new ones", "",
                        GuiItems.danger("Click", "detach (asks for a name)"))));

        inventory.setItem(TELEPORT_SLOT, GuiItems.item(Material.ENDER_PEARL, "Teleport",
                List.of(LegacyText.GRAY + "Go to where it stands now", "", GuiItems.hint("Click", "teleport"))));
        inventory.setItem(EDITOR_BACK_SLOT, GuiItems.back("the variants"));
        player.openInventory(inventory);
    }

    private void openRoutes(Player player, NpcDefinition definition, NpcInstance instance) {
        Inventory inventory = Bukkit.createInventory(new RoutesHolder(definition.getKey(), instance.getId()), 54,
                UiText.title("Variant Routes", definition.getDisplayName()));
        List<NpcRoute> owned = ownedRoutes(definition);
        for (int index = 0; index < Math.min(owned.size(), LIST_SIZE); index++) {
            NpcRoute route = owned.get(index);
            String swap = instance.getRouteSwap(route.getKey());
            List<String> lore = new ArrayList<>();
            lore.add(LegacyText.GRAY + "When told to walk this route,");
            lore.add(LegacyText.GRAY + "this variant walks: "
                    + (swap == null ? LegacyText.WHITE + "the same route" : LegacyText.AQUA + routeName(swap)));
            lore.add("");
            lore.add(GuiItems.hint("Click", "choose a replacement"));
            if (swap != null)
                lore.add(GuiItems.danger("Shift-right-click", "walk the preset's route"));
            ItemStack icon = GuiItems.item(swap == null ? Material.MAP : Material.FILLED_MAP, route.getDisplayName(),
                    lore);
            inventory.setItem(index, swap == null ? icon : GuiItems.glint(icon));
        }
        if (owned.isEmpty()) {
            inventory.setItem(22, GuiItems.empty("No Routes",
                    "Create routes with a Set Route action or in the preset's Routes menu"));
        }
        inventory.setItem(GuiItems.BACK_SLOT, GuiItems.back("the variant"));
        player.openInventory(inventory);
    }

    private void openPicker(Player player, NpcDefinition definition, NpcInstance instance, String presetRouteKey) {
        Inventory inventory = Bukkit.createInventory(
                new PickHolder(definition.getKey(), instance.getId(), presetRouteKey), 54,
                UiText.title("Replace Route", routeName(presetRouteKey)));
        String current = instance.resolveRoute(presetRouteKey);
        List<NpcRoute> owned = ownedRoutes(definition);
        for (int index = 0; index < Math.min(owned.size(), LIST_SIZE); index++) {
            NpcRoute route = owned.get(index);
            boolean original = route.getKey().equals(presetRouteKey);
            List<String> lore = new ArrayList<>();
            lore.add(LegacyText.GRAY + route.getPoints().size() + " point(s)");
            if (original)
                lore.add(LegacyText.DARK_GRAY + "The preset's own route (no swap)");
            lore.add("");
            lore.add(GuiItems.hint("Click", "walk this route"));
            ItemStack icon = GuiItems.item(original ? Material.MAP : Material.FILLED_MAP, route.getDisplayName(), lore);
            inventory.setItem(index, route.getKey().equals(current) ? GuiItems.glint(icon) : icon);
        }
        inventory.setItem(GuiItems.BACK_SLOT, GuiItems.back("the variant's routes"));
        inventory.setItem(GuiItems.CREATE_SLOT,
                GuiItems.create("Create Route", "Make a new route for this preset", "and swap it in for this variant"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof Holder holder))
            return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize())
            return;
        NpcDefinition definition = definitions.find(holder.definitionKey()).orElse(null);
        NpcInstance instance = instances.findById(holder.instanceId()).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        if (instance == null) {
            player.sendMessage(UiText.warning("That variant no longer exists."));
            openVariantList.accept(player, definition);
            return;
        }
        switch (holder) {
            case EditorHolder ignored -> onEditorClick(event, player, definition, instance, slot);
            case RoutesHolder ignored -> onRoutesClick(event, player, definition, instance, slot);
            case PickHolder pick -> onPickClick(player, definition, instance, pick.presetRouteKey(), slot);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    private void onEditorClick(InventoryClickEvent event, Player player, NpcDefinition definition, NpcInstance instance,
            int slot) {
        Runnable reopen = () -> reopen(player, definition.getKey(), instance.getId());
        boolean reset = event.getClick() == ClickType.SHIFT_RIGHT;
        switch (slot) {
            case LABEL_SLOT -> {
                if (reset) {
                    instance.setLabel(null);
                    instances.save(instance);
                    GuiItems.click(player);
                    open(player, definition, instance);
                    return;
                }
                input.request(player, "Enter a label for this variant (only admins see it):", value -> {
                    instance.setLabel(value);
                    instances.save(instance);
                    player.sendMessage(UiText.success("Variant label updated."));
                    reopen.run();
                }, reopen);
            }
            case SKIN_SLOT -> {
                if (reset) {
                    pendingSkinUrls.remove(instance.getId());
                    instance.clearSkin();
                    saveAndRefresh(instance);
                    player.sendMessage(UiText.info("This variant uses the preset's skin again."));
                    open(player, definition, instance);
                    return;
                }
                input.request(player, "Enter an HTTPS skin image URL, texture hash, or 'inherit':",
                        value -> updateSkin(player, definition, instance, value), reopen);
            }
            case SPAWN_SLOT -> {
                Location destination = player.getLocation();
                if (instances.relocate(instance, destination)) {
                    GuiItems.click(player);
                    player.sendMessage(UiText.success("Moved the variant and its spawn point to you."));
                } else {
                    player.sendMessage(UiText.error("Could not move the variant."));
                }
                open(player, definition, instance);
            }
            case ROUTES_SLOT -> openRoutes(player, definition, instance);
            case BEHAVIOUR_SLOT -> openBehaviour.run(player, definition, instance);
            case DETACH_SLOT -> beginDetach.run(player, definition, instance);
            case TELEPORT_SLOT -> {
                Location destination = instances.currentLocation(instance);
                player.closeInventory();
                if (destination != null && destination.getWorld() != null && player.teleport(destination))
                    player.sendMessage(UiText.success("Teleported to the variant."));
                else
                    player.sendMessage(UiText.error("Could not teleport to the variant; its world may not be loaded."));
            }
            case EDITOR_BACK_SLOT -> openVariantList.accept(player, definition);
            default -> {
            }
        }
    }

    private void onRoutesClick(InventoryClickEvent event, Player player, NpcDefinition definition, NpcInstance instance,
            int slot) {
        if (slot == GuiItems.BACK_SLOT) {
            open(player, definition, instance);
            return;
        }
        List<NpcRoute> owned = ownedRoutes(definition);
        if (slot >= LIST_SIZE || slot >= owned.size())
            return;
        NpcRoute route = owned.get(slot);
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            setSwap(instance, route.getKey(), null);
            GuiItems.click(player);
            openRoutes(player, definition, instance);
            return;
        }
        openPicker(player, definition, instance, route.getKey());
    }

    private void onPickClick(Player player, NpcDefinition definition, NpcInstance instance, String presetRouteKey,
            int slot) {
        if (slot == GuiItems.BACK_SLOT) {
            openRoutes(player, definition, instance);
            return;
        }
        if (slot == GuiItems.CREATE_SLOT) {
            String definitionKey = definition.getKey();
            UUID instanceId = instance.getId();
            routeCreator.create(player, definitionKey, route -> instances.findById(instanceId).ifPresent(current -> {
                setSwap(current, presetRouteKey, route.getKey());
                player.sendMessage(UiText.success("Created '" + route.getDisplayName() + "' and swapped it in."));
            }), returning -> {
                Optional<NpcDefinition> currentDefinition = definitions.find(definitionKey);
                Optional<NpcInstance> currentInstance = instances.findById(instanceId);
                if (currentDefinition.isPresent() && currentInstance.isPresent())
                    openRoutes(returning, currentDefinition.get(), currentInstance.get());
                else
                    returning.closeInventory();
            });
            return;
        }
        List<NpcRoute> owned = ownedRoutes(definition);
        if (slot >= LIST_SIZE || slot >= owned.size())
            return;
        NpcRoute chosen = owned.get(slot);
        setSwap(instance, presetRouteKey, chosen.getKey());
        GuiItems.click(player);
        player.sendMessage(chosen.getKey().equals(presetRouteKey)
                ? UiText.info("This variant walks the preset's route.")
                : UiText.success("This variant now walks '" + chosen.getDisplayName() + "' instead of '"
                        + routeName(presetRouteKey) + "'."));
        openRoutes(player, definition, instance);
    }

    private void setSwap(NpcInstance instance, String presetRouteKey, String replacementKey) {
        String before = instance.getRouteSwap(presetRouteKey);
        instance.setRouteSwap(presetRouteKey, replacementKey);
        if (java.util.Objects.equals(before, instance.getRouteSwap(presetRouteKey)))
            return;
        instances.save(instance);
        // Restart the route worker so the variant heads for its new route now.
        instances.stopNavigating(instance);
    }

    private void updateSkin(Player player, NpcDefinition definition, NpcInstance instance, String value) {
        if (value.isBlank() || value.trim().equalsIgnoreCase("inherit")) {
            pendingSkinUrls.remove(instance.getId());
            instance.clearSkin();
            saveAndRefresh(instance);
            player.sendMessage(UiText.info("This variant uses the preset's skin."));
            reopen(player, definition.getKey(), instance.getId());
            return;
        }
        String normalized;
        try {
            normalized = SkinTextureUtil.normalizeTextureUrl(value);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(UiText.error(exception.getMessage()));
            reopen(player, definition.getKey(), instance.getId());
            return;
        }
        if (normalized == null) {
            player.sendMessage(UiText.error("Enter a skin URL or texture hash, or 'inherit' for the preset's skin."));
            reopen(player, definition.getKey(), instance.getId());
            return;
        }
        if (SkinTextureUtil.isMinecraftTextureUrl(normalized)) {
            pendingSkinUrls.remove(instance.getId());
            instance.setSkinUrl(normalized);
            saveAndRefresh(instance);
            player.sendMessage(UiText.success("Variant skin updated."));
            reopen(player, definition.getKey(), instance.getId());
            return;
        }
        player.sendMessage(UiText.info("Processing the skin image. This can take a few seconds..."));
        pendingSkinUrls.put(instance.getId(), normalized);
        String definitionKey = definition.getKey();
        UUID instanceId = instance.getId();
        skinResolver.resolve(normalized).whenComplete((resolved, error) -> Bukkit.getScheduler().runTask(plugin,
                () -> finishSkinUpdate(player, definitionKey, instanceId, normalized, resolved, error)));
    }

    private void finishSkinUpdate(Player player, String definitionKey, UUID instanceId, String requestedUrl,
            ResolvedSkin resolved, Throwable error) {
        if (!requestedUrl.equals(pendingSkinUrls.get(instanceId)))
            return;
        pendingSkinUrls.remove(instanceId);
        NpcInstance instance = instances.findById(instanceId).orElse(null);
        if (error != null) {
            if (player.isOnline()) {
                player.sendMessage(UiText.error(rootMessage(error)));
                reopen(player, definitionKey, instanceId);
            }
            return;
        }
        if (instance == null)
            return;
        instance.setResolvedSkin(requestedUrl, resolved.textureValue(), resolved.textureSignature());
        saveAndRefresh(instance);
        if (player.isOnline()) {
            player.sendMessage(UiText.success("Variant skin processed and updated."));
            reopen(player, definitionKey, instanceId);
        }
    }

    private void saveAndRefresh(NpcInstance instance) {
        instances.save(instance);
        instances.refreshInstance(instance);
    }

    private void reopen(Player player, String definitionKey, UUID instanceId) {
        if (!player.isOnline())
            return;
        Optional<NpcDefinition> definition = definitions.find(definitionKey);
        Optional<NpcInstance> instance = instances.findById(instanceId);
        if (definition.isPresent() && instance.isPresent())
            open(player, definition.get(), instance.get());
        else
            definition.ifPresentOrElse(current -> openVariantList.accept(player, current), player::closeInventory);
    }

    private List<NpcRoute> ownedRoutes(NpcDefinition definition) {
        return routes.findAll().stream().filter(route -> route.isOwnedBy(definition.getKey()))
                .sorted(java.util.Comparator.comparing(NpcRoute::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String routeName(String routeKey) {
        return routes.find(routeKey).map(NpcRoute::getDisplayName).orElse(routeKey + " (missing)");
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null)
            current = current.getCause();
        return current.getMessage() == null ? "Could not process that skin image." : current.getMessage();
    }

    private static String abbreviate(String value) {
        if (value == null)
            return "None";
        return value.length() <= 36 ? value : value.substring(0, 33) + "...";
    }

    private static String formatLocation(StoredLocation location) {
        if (location == null)
            return "Not set";
        return location.worldName() + " " + (int) Math.floor(location.x()) + ", " + (int) Math.floor(location.y())
                + ", " + (int) Math.floor(location.z());
    }
}
