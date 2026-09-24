package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
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
    private final List<BehaviourRow> behaviourRows;
    private final List<CustomBehaviourRow> customBehaviourRows;
    private AiControlSettings aiControlSettings;
    private List<String> aiMemories;

    public NpcDefinition(String key) {
        this.key = key;
        this.displayName = key;
        this.inventoryContents = new ItemStack[36];
        this.armorContents = new ItemStack[4];
        this.combatProfile = CombatProfile.disabled();
        this.movementProfile = MovementProfile.disabled();
        this.showName = true;
        this.lookAtPlayer = true;
        this.pushable = true;
        this.color = NpcColor.ORANGE;
        this.behaviourRows = new ArrayList<>();
        this.customBehaviourRows = new ArrayList<>();
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
        copy.setAiControlSettings(aiControlSettings);
        copy.setAiMemories(aiMemories);
        behaviourRows.forEach(row -> copy.addBehaviourRow(row.event(), row.actions()));
        customBehaviourRows.forEach(row -> copy.addCustomBehaviourRow(row.eventName(), row.actions()));
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
        List<BehaviourAction> actions = new ArrayList<>();
        for (BehaviourRow row : behaviourRows) {
            if (row.event() == event)
                actions.addAll(row.actions());
        }
        return actions;
    }

    public void setBehaviourActions(BehaviourEvent event, List<BehaviourAction> actions) {
        int first = -1;
        for (int index = 0; index < behaviourRows.size(); index++) {
            if (behaviourRows.get(index).event() == event) {
                first = index;
                break;
            }
        }
        behaviourRows.removeIf(row -> row.event() == event);
        if (actions == null || actions.isEmpty())
            return;
        int insertAt = first < 0 ? behaviourRows.size() : first;
        for (int offset = 0; offset < actions.size(); offset += BehaviourRow.MAX_ACTIONS) {
            int end = Math.min(offset + BehaviourRow.MAX_ACTIONS, actions.size());
            behaviourRows.add(insertAt++, new BehaviourRow(event, actions.subList(offset, end)));
        }
    }

    public void removeBehaviourAction(BehaviourEvent event, int index) {
        if (index < 0)
            return;
        for (int rowIndex = 0; rowIndex < behaviourRows.size(); rowIndex++) {
            BehaviourRow row = behaviourRows.get(rowIndex);
            if (row.event() != event)
                continue;
            if (index < row.actions().size()) {
                removeBehaviourRowAction(rowIndex, index);
                if (behaviourRows.get(rowIndex).actions().isEmpty())
                    behaviourRows.remove(rowIndex);
                return;
            }
            index -= row.actions().size();
        }
    }

    public List<BehaviourRow> getBehaviourRows() {
        return List.copyOf(behaviourRows);
    }

    public int addBehaviourRow(BehaviourEvent event) {
        return addBehaviourRow(event, List.of());
    }

    public int addBehaviourRow(BehaviourEvent event, List<BehaviourAction> actions) {
        behaviourRows.add(new BehaviourRow(event, actions));
        return behaviourRows.size() - 1;
    }

    public void setBehaviourRowActions(int rowIndex, List<BehaviourAction> actions) {
        BehaviourRow current = behaviourRows.get(rowIndex);
        behaviourRows.set(rowIndex, new BehaviourRow(current.event(), actions));
    }

    public void removeBehaviourRowAction(int rowIndex, int actionIndex) {
        BehaviourRow row = behaviourRows.get(rowIndex);
        if (actionIndex < 0 || actionIndex >= row.actions().size())
            return;
        List<BehaviourAction> actions = new ArrayList<>(row.actions());
        actions.remove(actionIndex);
        setBehaviourRowActions(rowIndex, actions);
    }

    public void removeBehaviourRow(int rowIndex) {
        behaviourRows.remove(rowIndex);
    }

    public List<BehaviourAction> getCustomEventActions(String eventName) {
        List<BehaviourAction> actions = new ArrayList<>();
        for (CustomBehaviourRow row : customBehaviourRows) {
            if (row.eventName().equals(eventName))
                actions.addAll(row.actions());
        }
        return actions;
    }

    public void setCustomEventActions(String eventName, List<BehaviourAction> actions) {
        int first = -1;
        for (int index = 0; index < customBehaviourRows.size(); index++) {
            if (customBehaviourRows.get(index).eventName().equals(eventName)) {
                first = index;
                break;
            }
        }
        customBehaviourRows.removeIf(row -> row.eventName().equals(eventName));
        if (actions == null || actions.isEmpty())
            return;
        int insertAt = first < 0 ? customBehaviourRows.size() : first;
        for (int offset = 0; offset < actions.size(); offset += BehaviourRow.MAX_ACTIONS) {
            int end = Math.min(offset + BehaviourRow.MAX_ACTIONS, actions.size());
            customBehaviourRows.add(insertAt++, new CustomBehaviourRow(eventName, actions.subList(offset, end)));
        }
    }

    public void removeCustomEventAction(String eventName, int index) {
        if (index < 0)
            return;
        for (int rowIndex = 0; rowIndex < customBehaviourRows.size(); rowIndex++) {
            CustomBehaviourRow row = customBehaviourRows.get(rowIndex);
            if (!row.eventName().equals(eventName))
                continue;
            if (index < row.actions().size()) {
                removeCustomBehaviourRowAction(rowIndex, index);
                if (customBehaviourRows.get(rowIndex).actions().isEmpty())
                    customBehaviourRows.remove(rowIndex);
                return;
            }
            index -= row.actions().size();
        }
    }

    public List<CustomBehaviourRow> getCustomBehaviourRows() {
        return List.copyOf(customBehaviourRows);
    }

    public int addCustomBehaviourRow(String eventName) {
        return addCustomBehaviourRow(eventName, List.of());
    }

    public int addCustomBehaviourRow(String eventName, List<BehaviourAction> actions) {
        customBehaviourRows.add(new CustomBehaviourRow(eventName, actions));
        return customBehaviourRows.size() - 1;
    }

    public void setCustomBehaviourRowActions(int rowIndex, List<BehaviourAction> actions) {
        CustomBehaviourRow current = customBehaviourRows.get(rowIndex);
        customBehaviourRows.set(rowIndex, new CustomBehaviourRow(current.eventName(), actions));
    }

    public void removeCustomBehaviourRowAction(int rowIndex, int actionIndex) {
        CustomBehaviourRow row = customBehaviourRows.get(rowIndex);
        if (actionIndex < 0 || actionIndex >= row.actions().size())
            return;
        List<BehaviourAction> actions = new ArrayList<>(row.actions());
        actions.remove(actionIndex);
        setCustomBehaviourRowActions(rowIndex, actions);
    }

    public void removeCustomBehaviourRow(int rowIndex) {
        customBehaviourRows.remove(rowIndex);
    }

    public void removeCustomEvent(String eventName) {
        customBehaviourRows.removeIf(row -> row.eventName().equals(eventName));
    }
    public int customEventActionCount() {
        return customBehaviourRows.stream().mapToInt(row -> row.actions().size()).sum();
    }
    public List<String> getCustomEventNames() {
        Set<String> names = new LinkedHashSet<>();
        customBehaviourRows.forEach(row -> names.add(row.eventName()));
        return new ArrayList<>(names);
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

    /** Removes every direct or question-branch reference to a deleted route. */
    public boolean removeRouteReferences(String routeKey) {
        String normalized = NpcRoute.normalizeKey(routeKey);
        boolean referenced = getReferencedRouteKeys().contains(normalized);
        for (int index = 0; index < behaviourRows.size(); index++)
            setBehaviourRowActions(index, withoutRoute(behaviourRows.get(index).actions(), normalized));
        for (int index = 0; index < customBehaviourRows.size(); index++)
            setCustomBehaviourRowActions(index, withoutRoute(customBehaviourRows.get(index).actions(), normalized));
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
        return List.copyOf(aiMemories);
    }

    public void setAiMemories(List<String> memories) {
        aiMemories.clear();
        if (memories == null)
            return;
        memories.stream().filter(java.util.Objects::nonNull).map(String::trim).filter(memory -> !memory.isBlank())
                .forEach(this::addAiMemory);
    }

    public void addAiMemory(String memory) {
        if (memory == null || memory.isBlank())
            return;
        aiMemories.add(memory.trim());
        while (aiMemories.size() > MAX_AI_MEMORIES)
            aiMemories.removeFirst();
    }

    public void setAiMemory(int index, String memory) {
        if (index < 0 || index >= aiMemories.size())
            return;
        if (memory == null || memory.isBlank())
            aiMemories.remove(index);
        else
            aiMemories.set(index, memory.trim());
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
