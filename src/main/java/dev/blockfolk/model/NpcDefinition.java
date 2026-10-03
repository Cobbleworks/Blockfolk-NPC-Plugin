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

import dev.blockfolk.ai.AiControlSettings;

public final class NpcDefinition {

    public static final int MAX_AI_MEMORIES = 45;

    private final String key;
    private String displayName;
    private String skinUrl;
    private String skinTextureValue;
    private String skinTextureSignature;
    private StoredLocation spawnpoint;
    private ItemStack[] inventoryContents;
    private ItemStack[] initialTemporaryInventoryContents;
    private ItemStack[] armorContents;
    private ItemStack mainHand;
    private ItemStack offHand;
    private CombatProfile combatProfile;
    private MovementProfile movementProfile;
    private boolean showName;
    private boolean lookAtPlayer;
    private boolean itemPickup;
    private boolean pushable;
    private NpcColor color;
    private NpcPose pose = NpcPose.STANDING;
    private Map<BehaviourEvent, List<BehaviourAction>> behaviours;
    private Map<String, List<BehaviourAction>> customEventBehaviours;
    private AiControlSettings aiControlSettings;
    private List<AiMemory> aiMemories;

    public NpcDefinition(String key) {
        this.key = key;
        this.displayName = key;
        this.inventoryContents = new ItemStack[36];
        this.initialTemporaryInventoryContents = new ItemStack[27];
        this.armorContents = new ItemStack[4];
        this.combatProfile = CombatProfile.disabled();
        this.movementProfile = MovementProfile.disabled();
        this.showName = true;
        this.lookAtPlayer = true;
        this.pushable = true;
        this.color = NpcColor.ORANGE;
        this.behaviours = new EnumMap<>(BehaviourEvent.class);
        this.customEventBehaviours = new java.util.LinkedHashMap<>();
        this.aiControlSettings = AiControlSettings.defaults();
        this.aiMemories = new ArrayList<>();
    }

    public static NpcDefinition create(String displayName) {
        NpcDefinition definition = new NpcDefinition(toKey(displayName));
        definition.setDisplayName(displayName);
        return definition;
    }

    public NpcDefinition copyAs(String displayName) {
        NpcDefinition copy = create(displayName);
        copy.setResolvedSkin(skinUrl, skinTextureValue, skinTextureSignature);
        copy.setStoredSpawnpoint(spawnpoint);
        copy.setInventoryContents(inventoryContents);
        copy.setInitialTemporaryInventoryContents(initialTemporaryInventoryContents);
        copy.setArmorContents(armorContents);
        copy.setMainHand(mainHand);
        copy.setOffHand(offHand);
        copy.setCombatProfile(combatProfile);
        copy.setMovementProfile(movementProfile);
        copy.setShowName(showName);
        copy.setLookAtPlayer(lookAtPlayer);
        copy.setItemPickup(itemPickup);
        copy.setPushable(pushable);
        copy.setColor(color);
        copy.setPose(pose);
        copy.setAiControlSettings(aiControlSettings);
        copy.setAiMemoryEntries(aiMemories);
        behaviours.forEach(copy::setBehaviourActions);
        customEventBehaviours.forEach(copy::setCustomEventActions);
        return copy;
    }

    public static String toKey(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT).replace('ö', 'o').replace('ä', 'a').replace('ü', 'u')
                .replaceAll("[^a-z0-9_-]+", "-");
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
        this.displayName = Objects.requireNonNullElse(displayName, key).trim();
        if (this.displayName.isBlank())
            this.displayName = key;
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
        return spawnpoint == null ? null : spawnpoint.toLocation();
    }

    public void setSpawnpoint(Location spawnpoint) {
        this.spawnpoint = spawnpoint == null ? null : StoredLocation.from(spawnpoint);
    }

    public StoredLocation getStoredSpawnpoint() {
        return spawnpoint;
    }

    public void setStoredSpawnpoint(StoredLocation spawnpoint) {
        this.spawnpoint = spawnpoint;
    }

    public ItemStack[] getInventoryContents() {
        return cloneArray(inventoryContents, 36);
    }

    public void setInventoryContents(ItemStack[] inventoryContents) {
        this.inventoryContents = cloneArray(inventoryContents, 36);
    }

    public ItemStack[] getInitialTemporaryInventoryContents() {
        return cloneArray(initialTemporaryInventoryContents, 27);
    }

    public void setInitialTemporaryInventoryContents(ItemStack[] contents) {
        this.initialTemporaryInventoryContents = cloneArray(contents, 27);
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

    public NpcPose getPose() {
        return pose;
    }

    public void setPose(NpcPose pose) {
        this.pose = Objects.requireNonNullElse(pose, NpcPose.STANDING);
    }

    public boolean isPushable() {
        return pushable;
    }

    public void setPushable(boolean pushable) {
        this.pushable = pushable;
    }

    public NpcColor getColor() {
        return color;
    }

    public void setColor(NpcColor color) {
        this.color = color == null ? NpcColor.ORANGE : color;
    }

    public List<BehaviourAction> getBehaviourActions(BehaviourEvent event) {
        return new ArrayList<>(behaviours.getOrDefault(event, List.of()));
    }

    public void setBehaviourActions(BehaviourEvent event, List<BehaviourAction> actions) {
        if (actions == null || actions.isEmpty()) {
            behaviours.remove(event);
        } else {
            behaviours.put(event, new ArrayList<>(actions));
        }
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
        if (actions == null)
            customEventBehaviours.remove(eventName);
        else
            customEventBehaviours.put(eventName, new ArrayList<>(actions));
    }

    public void removeCustomEventAction(String eventName, int index) {
        List<BehaviourAction> actions = customEventBehaviours.get(eventName);
        if (actions == null || index < 0 || index >= actions.size())
            return;
        actions.remove(index);
    }

    public void removeCustomEvent(String eventName) {
        customEventBehaviours.remove(eventName);
    }
    public int customEventActionCount() {
        return customEventBehaviours.values().stream().mapToInt(List::size).sum();
    }
    public List<String> getCustomEventNames() {
        return new ArrayList<>(customEventBehaviours.keySet());
    }

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

    /**
     * Rewrites every behaviour and custom event action, including question
     * branches. Returns whether anything changed.
     */
    public boolean mapActions(java.util.function.UnaryOperator<BehaviourAction> mapper) {
        boolean changed = false;
        for (BehaviourEvent event : BehaviourEvent.values()) {
            List<BehaviourAction> actions = getBehaviourActions(event);
            List<BehaviourAction> mapped = BehaviourActions.map(actions, mapper);
            if (!mapped.equals(actions)) {
                setBehaviourActions(event, mapped);
                changed = true;
            }
        }
        for (String eventName : getCustomEventNames()) {
            List<BehaviourAction> actions = getCustomEventActions(eventName);
            List<BehaviourAction> mapped = BehaviourActions.map(actions, mapper);
            if (!mapped.equals(actions)) {
                setCustomEventActions(eventName, mapped);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Visits every behaviour and custom event action, including question branches.
     */
    public void forEachAction(java.util.function.Consumer<BehaviourAction> visitor) {
        for (BehaviourEvent event : BehaviourEvent.values())
            BehaviourActions.forEach(getBehaviourActions(event), visitor);
        for (String eventName : getCustomEventNames())
            BehaviourActions.forEach(getCustomEventActions(eventName), visitor);
    }

    /** Repoints direct, movement, and question-branch route references. */
    public boolean replaceRouteReferences(String oldKey, String newKey) {
        String oldRoute = NpcRoute.normalizeKey(oldKey);
        String newRoute = NpcRoute.normalizeKey(newKey);
        if (!getReferencedRouteKeys().contains(oldRoute))
            return false;
        for (BehaviourEvent event : BehaviourEvent.values())
            setBehaviourActions(event, replaceRoute(getBehaviourActions(event), oldRoute, newRoute));
        for (String eventName : getCustomEventNames())
            setCustomEventActions(eventName, replaceRoute(getCustomEventActions(eventName), oldRoute, newRoute));
        if (oldRoute.equals(movementProfile.routeKey()))
            movementProfile = new MovementProfile(movementProfile.enabled(), newRoute, movementProfile.walkingSpeed());
        return true;
    }

    private static boolean matchesRoute(String value, String key) {
        try {
            return NpcRoute.normalizeKey(value).equals(key);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static List<BehaviourAction> replaceRoute(List<BehaviourAction> actions, String oldKey, String newKey) {
        List<BehaviourAction> result = new ArrayList<>();
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.SET_ROUTE && action.value() != null
                    && matchesRoute(action.value(), oldKey)) {
                result.add(new BehaviourAction(action.type(), newKey));
            } else if (action.type() == BehaviourActionType.ASK_QUESTION && action.question() != null) {
                NpcQuestion question = action.question();
                List<QuestionOption> options = question.options().stream()
                        .map(option -> option.withActions(replaceRoute(option.actions(), oldKey, newKey))).toList();
                result.add(BehaviourAction.ask(new NpcQuestion(question.id(), question.prompt(), options,
                        replaceRoute(question.cancelActions(), oldKey, newKey))));
            } else {
                result.add(action);
            }
        }
        return result;
    }

    /** Removes every direct or question-branch reference to a deleted route. */
    public boolean removeRouteReferences(String routeKey) {
        String normalized = NpcRoute.normalizeKey(routeKey);
        boolean referenced = getReferencedRouteKeys().contains(normalized);
        for (BehaviourEvent event : BehaviourEvent.values()) {
            setBehaviourActions(event, withoutRoute(getBehaviourActions(event), normalized));
        }
        for (String eventName : getCustomEventNames()) {
            setCustomEventActions(eventName, withoutRoute(getCustomEventActions(eventName), normalized));
        }
        if (movementProfile.routeKey() != null && movementProfile.routeKey().equals(normalized)) {
            movementProfile = MovementProfile.disabled().withWalkingSpeed(movementProfile.walkingSpeed());
            referenced = true;
        }
        return referenced;
    }

    private static List<BehaviourAction> withoutRoute(List<BehaviourAction> actions, String routeKey) {
        List<BehaviourAction> result = new ArrayList<>();
        for (BehaviourAction action : actions) {
            if (action.type() == BehaviourActionType.SET_ROUTE) {
                try {
                    if (action.value() != null && NpcRoute.normalizeKey(action.value()).equals(routeKey))
                        continue;
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (action.type() == BehaviourActionType.ASK_QUESTION && action.question() != null) {
                NpcQuestion question = action.question();
                List<QuestionOption> options = question.options().stream()
                        .map(option -> option.withActions(withoutRoute(option.actions(), routeKey))).toList();
                result.add(BehaviourAction.ask(new NpcQuestion(question.id(), question.prompt(), options,
                        withoutRoute(question.cancelActions(), routeKey))));
            } else {
                result.add(action);
            }
        }
        return result;
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

    public AiControlSettings getAiControlSettings() {
        return aiControlSettings;
    }

    public void setAiControlSettings(AiControlSettings settings) {
        aiControlSettings = settings == null ? AiControlSettings.defaults() : settings;
    }

    public List<String> getAiMemories() {
        return getAiMemoryEntries().stream().map(AiMemory::fact).toList();
    }

    public List<AiMemory> getAiMemoryEntries() {
        aiMemories.removeIf(memory -> memory.expired(System.currentTimeMillis()));
        return List.copyOf(aiMemories);
    }

    public void setAiMemories(List<String> memories) {
        aiMemories.clear();
        if (memories == null)
            return;
        memories.stream().filter(java.util.Objects::nonNull).map(String::trim).filter(memory -> !memory.isBlank())
                .forEach(memory -> addAiMemory(memory, AiMemory.Category.PERSONAL));
    }

    public void setAiMemoryEntries(List<AiMemory> memories) {
        aiMemories.clear();
        if (memories != null)
            memories.forEach(this::addAiMemory);
    }

    public void addAiMemory(String memory) {
        addAiMemory(memory, AiMemory.Category.PERSONAL);
    }

    public boolean addAiMemory(String memory, AiMemory.Category category) {
        return addAiMemory(new AiMemory(memory, category));
    }

    public boolean addAiMemory(AiMemory memory) {
        if (memory == null)
            return false;
        String fact = memory.fact();
        if (fact == null || fact.isBlank())
            return false;
        aiMemories.removeIf(entry -> entry.expired(System.currentTimeMillis()));
        if (aiMemories.size() >= MAX_AI_MEMORIES) {
            int oldest = 0;
            int oldestTemporal = -1;
            for (int index = 1; index < aiMemories.size(); index++) {
                if (aiMemories.get(index).recordedAt() < aiMemories.get(oldest).recordedAt())
                    oldest = index;
            }
            for (int index = 0; index < aiMemories.size(); index++) {
                AiMemory entry = aiMemories.get(index);
                if (entry.category() == AiMemory.Category.TEMPORAL
                        && (oldestTemporal < 0 || entry.recordedAt() < aiMemories.get(oldestTemporal).recordedAt()))
                    oldestTemporal = index;
            }
            aiMemories.remove(oldestTemporal >= 0 ? oldestTemporal : oldest);
        }
        aiMemories.add(new AiMemory(fact.trim(), memory.category(), memory.origin(), memory.recordedAt()));
        return true;
    }

    public void setAiMemory(int index, String memory) {
        if (index < 0 || index >= aiMemories.size())
            return;
        if (memory == null || memory.isBlank())
            aiMemories.remove(index);
        else
            aiMemories.set(index, new AiMemory(memory.trim(), aiMemories.get(index).category(),
                    aiMemories.get(index).origin(), aiMemories.get(index).recordedAt()));
    }

    public void setAiMemoryEntry(int index, AiMemory memory) {
        if (index >= 0 && index < aiMemories.size() && memory != null && memory.fact() != null
                && !memory.fact().isBlank())
            aiMemories.set(index, memory);
    }

    public void removeAiMemory(int index) {
        if (index >= 0 && index < aiMemories.size())
            aiMemories.remove(index);
    }

    public void clearAiMemories() {
        aiMemories.clear();
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
