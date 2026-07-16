package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public final class NpcDefinition {

    private final String key;
    private String displayName;
    private String skinUrl;
    private String skinTextureValue;
    private String skinTextureSignature;
    private Location spawnpoint;
    private ItemStack[] inventoryContents;
    private ItemStack[] armorContents;
    private ItemStack mainHand;
    private ItemStack offHand;
    private CombatProfile combatProfile;
    private MovementProfile movementProfile;
    private boolean showName;
    private boolean lookAtPlayer;
    private boolean itemPickup;
    private Map<BehaviourEvent, List<BehaviourAction>> behaviours;
    private Map<String, List<BehaviourAction>> customEventBehaviours;

    public NpcDefinition(String key) {
        this.key = key;
        this.displayName = key;
        this.inventoryContents = new ItemStack[36];
        this.armorContents = new ItemStack[4];
        this.combatProfile = CombatProfile.disabled();
        this.movementProfile = MovementProfile.disabled();
        this.showName = true;
        this.lookAtPlayer = true;
        this.behaviours = new EnumMap<>(BehaviourEvent.class);
        this.customEventBehaviours = new java.util.LinkedHashMap<>();
    }

    public static NpcDefinition create(String displayName) {
        NpcDefinition definition = new NpcDefinition(toKey(displayName));
        definition.setDisplayName(displayName);
        return definition;
    }

    public static String toKey(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        sanitized = sanitized.replaceAll("^-+|-+$", "");
        return sanitized.isBlank() ? "npc" : sanitized;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = Objects.requireNonNullElse(displayName, key);
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public void setSkinUrl(String skinUrl) {
        this.skinUrl = skinUrl == null || skinUrl.isBlank() ? null : skinUrl.trim();
        this.skinTextureValue = null;
        this.skinTextureSignature = null;
    }

    public String getSkinTextureValue() {
        return skinTextureValue;
    }

    public String getSkinTextureSignature() {
        return skinTextureSignature;
    }

    public void setResolvedSkin(String skinUrl, String textureValue, String textureSignature) {
        this.skinUrl = skinUrl == null || skinUrl.isBlank() ? null : skinUrl.trim();
        this.skinTextureValue = textureValue == null || textureValue.isBlank() ? null : textureValue.trim();
        this.skinTextureSignature = textureSignature == null || textureSignature.isBlank()
                ? null
                : textureSignature.trim();
    }

    public Location getSpawnpoint() {
        return spawnpoint == null ? null : spawnpoint.clone();
    }

    public void setSpawnpoint(Location spawnpoint) {
        this.spawnpoint = spawnpoint == null ? null : spawnpoint.clone();
    }

    public ItemStack[] getInventoryContents() {
        return cloneArray(inventoryContents, 36);
    }

    public void setInventoryContents(ItemStack[] inventoryContents) {
        this.inventoryContents = cloneArray(inventoryContents, 36);
    }

    public ItemStack[] getArmorContents() {
        return cloneArray(armorContents, 4);
    }

    public void setArmorContents(ItemStack[] armorContents) {
        this.armorContents = cloneArray(armorContents, 4);
    }

    public ItemStack getMainHand() {
        return mainHand == null ? null : mainHand.clone();
    }

    public void setMainHand(ItemStack mainHand) {
        this.mainHand = mainHand == null ? null : mainHand.clone();
    }

    public ItemStack getOffHand() {
        return offHand == null ? null : offHand.clone();
    }

    public void setOffHand(ItemStack offHand) {
        this.offHand = offHand == null ? null : offHand.clone();
    }

    public CombatProfile getCombatProfile() {
        return combatProfile;
    }

    public void setCombatProfile(CombatProfile combatProfile) {
        this.combatProfile = combatProfile == null ? CombatProfile.disabled() : combatProfile;
    }

    public MovementProfile getMovementProfile() {
        return movementProfile;
    }

    public void setMovementProfile(MovementProfile movementProfile) {
        this.movementProfile = movementProfile == null ? MovementProfile.disabled() : movementProfile;
    }

    public boolean isShowName() {
        return showName;
    }

    public void setShowName(boolean showName) {
        this.showName = showName;
    }

    public boolean isLookAtPlayer() {
        return lookAtPlayer;
    }

    public void setLookAtPlayer(boolean lookAtPlayer) {
        this.lookAtPlayer = lookAtPlayer;
    }

    public boolean isItemPickup() {
        return itemPickup;
    }

    public void setItemPickup(boolean itemPickup) {
        this.itemPickup = itemPickup;
    }

    public List<BehaviourAction> getBehaviourActions(BehaviourEvent event) {
        return new ArrayList<>(behaviours.getOrDefault(event, List.of()));
    }

    public void setBehaviourActions(BehaviourEvent event, List<BehaviourAction> actions) {
        if (actions == null || actions.isEmpty()) {
            behaviours.remove(event); 
        }else {
            behaviours.put(event, new ArrayList<>(actions));
        }
    }

    public void addBehaviourAction(BehaviourEvent event, BehaviourAction action) {
        behaviours.computeIfAbsent(event, ignored -> new ArrayList<>()).add(action);
    }

    public void removeBehaviourAction(BehaviourEvent event, int index) {
        List<BehaviourAction> actions = behaviours.get(event);
        if (actions == null || index < 0 || index >= actions.size()) {
            return;
        }
        actions.remove(index);
        if (actions.isEmpty()) {
            behaviours.remove(event);
        }
    }

    public List<BehaviourAction> getCustomEventActions(String eventName) {
        return new ArrayList<>(customEventBehaviours.getOrDefault(eventName, List.of()));
    }

    public void setCustomEventActions(String eventName, List<BehaviourAction> actions) {
        if (actions == null || actions.isEmpty()) customEventBehaviours.remove(eventName);
        else customEventBehaviours.put(eventName, new ArrayList<>(actions));
    }

    public void removeCustomEventAction(String eventName, int index) {
        List<BehaviourAction> actions = customEventBehaviours.get(eventName);
        if (actions == null || index < 0 || index >= actions.size()) return;
        actions.remove(index);
        if (actions.isEmpty()) customEventBehaviours.remove(eventName);
    }

    public void removeCustomEvent(String eventName) { customEventBehaviours.remove(eventName); }
    public int customEventActionCount() { return customEventBehaviours.values().stream().mapToInt(List::size).sum(); }
    public List<String> getCustomEventNames() { return new ArrayList<>(customEventBehaviours.keySet()); }

    public Set<String> getReferencedRouteKeys() {
        Set<String> routeKeys = new LinkedHashSet<>();
        if (movementProfile.routeKey() != null) {
            routeKeys.add(movementProfile.routeKey());
        }
        for (BehaviourEvent event : BehaviourEvent.values()) {
            collectRouteKeys(getBehaviourActions(event), routeKeys);
        }
        for (String eventName : getCustomEventNames()) {
            collectRouteKeys(getCustomEventActions(eventName), routeKeys);
        }
        return Set.copyOf(routeKeys);
    }

    private static void collectRouteKeys(List<BehaviourAction> actions, Set<String> routeKeys) {
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.SET_ROUTE && action.value() != null) {
                try {
                    routeKeys.add(NpcRoute.normalizeKey(action.value()));
                } catch (IllegalArgumentException ignored) {
                    // A malformed stored action cannot refer to a route in the repository.
                }
            }
            if (action.type() != BehaviourActionType.ASK_QUESTION || action.question() == null) {
                continue;
            }
            for (QuestionOption option : action.question().options()) {
                collectRouteKeys(option.actions(), routeKeys);
            }
            collectRouteKeys(action.question().cancelActions(), routeKeys);
        }
    }

    private static ItemStack[] cloneArray(ItemStack[] source, int length) {
        ItemStack[] copy = new ItemStack[length];
        if (source == null) {
            return copy;
        }
        for (int index = 0; index < Math.min(source.length, length); index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }
}
