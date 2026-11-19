package dev.blockfolk.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

/**
 * A scripted moment several NPCs play together: who takes part (roles), what
 * they do step by step (beats), what starts it (triggers, one of which watches
 * an area), and when it ends.
 */
public final class Scene {

    public static final int DEFAULT_RADIUS = 24;
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 256;
    public static final int DEFAULT_COOLDOWN_SECONDS = 300;
    public static final int MAX_COOLDOWN_SECONDS = 7 * 24 * 60 * 60;
    public static final int DEFAULT_DURATION_SECONDS = 300;
    public static final int MAX_DURATION_SECONDS = 24 * 60 * 60;
    public static final int MAX_ROLES = 28;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_BEATS = 64;

    private final String key;
    private String displayName;
    private String description = "";
    private ItemStack icon;
    private boolean enabled = true;
    private boolean triggersAiBehaviour = true;
    private String areaLocationKey;
    private int areaRadius = DEFAULT_RADIUS;
    private final Set<SceneTrigger> triggers = EnumSet.noneOf(SceneTrigger.class);
    private int chancePercent = 100;
    private int cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
    private SceneEnding ending = SceneEnding.BEATS_FINISHED;
    private int durationSeconds = DEFAULT_DURATION_SECONDS;
    private DayPeriod endPeriod = DayPeriod.EVENING;
    private final List<SceneRole> roles = new ArrayList<>();
    /** Never empty: a scene always has at least one beat. */
    private final List<SceneBeat> beats = new ArrayList<>(List.of(new SceneBeat()));

    public Scene(String key) {
        this.key = normalizeKey(key);
        this.displayName = this.key;
    }

    public static Scene create(String displayName) {
        Scene scene = new Scene(displayName);
        int slash = displayName.lastIndexOf('/');
        scene.setDisplayName(slash < 0 ? displayName : displayName.substring(slash + 1));
        return scene;
    }

    /**
     * Lowercase, filesystem-safe key. Groups are separated by {@code /}, like
     * routes and locations.
     */
    public static String normalizeKey(String value) {
        String normalized = Objects.requireNonNull(value, "scene name").trim();
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.endsWith("/")
                || normalized.contains("//")) {
            throw new IllegalArgumentException(
                    "Scene names may contain letters, numbers, _ and -, with / between groups");
        }
        List<String> groups = new ArrayList<>();
        for (String group : normalized.split("/")) {
            String trimmed = group.trim();
            if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals(".."))
                throw new IllegalArgumentException(
                        "Scene names may contain letters, numbers, _ and -, with / between groups");
            groups.add(NpcDefinition.toKey(trimmed));
        }
        return String.join("/", groups);
    }

    /** The normalized key, or empty when the value cannot name a scene. */
    public static Optional<String> tryKey(String value) {
        try {
            return value == null ? Optional.empty() : Optional.of(normalizeKey(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        String trimmed = displayName == null ? "" : displayName.trim();
        this.displayName = trimmed.isEmpty() ? key : trimmed;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Several lines are allowed. AI-controlled NPCs get it as context while they
     * play the scene.
     */
    public void setDescription(String description) {
        String normalized = description == null ? "" : description.replace("\r\n", "\n").replace('\r', '\n').strip();
        this.description = normalized.length() > MAX_DESCRIPTION_LENGTH
                ? normalized.substring(0, MAX_DESCRIPTION_LENGTH)
                : normalized;
    }

    public ItemStack getIcon() {
        return icon == null ? null : icon.clone();
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon == null || icon.getType().isAir() ? null : icon.clone();
        if (this.icon != null)
            this.icon.setAmount(1);
    }

    /** Disabled scenes ignore their triggers but can still be started by hand. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The global location at the centre of the area the Player Enters trigger
     * watches, or null.
     */
    /**
     * Whether the cast's lines may start AI replies while the scene runs. When off,
     * NPCs in the scene neither start nor join NPC conversations, and their
     * preset's AI Trigger actions wait; AI Trigger actions in the scene's own rows
     * still run.
     */
    public boolean triggersAiBehaviour() {
        return triggersAiBehaviour;
    }

    public void setTriggersAiBehaviour(boolean triggersAiBehaviour) {
        this.triggersAiBehaviour = triggersAiBehaviour;
    }

    public String getAreaLocationKey() {
        return areaLocationKey;
    }

    public void setAreaLocationKey(String areaLocationKey) {
        this.areaLocationKey = areaLocationKey == null || areaLocationKey.isBlank() ? null : areaLocationKey.trim();
    }

    public boolean hasArea() {
        return areaLocationKey != null;
    }

    public int getAreaRadius() {
        return areaRadius;
    }

    public void setAreaRadius(int areaRadius) {
        this.areaRadius = Math.clamp(areaRadius, MIN_RADIUS, MAX_RADIUS);
    }

    public Set<SceneTrigger> getTriggers() {
        return triggers.isEmpty() ? EnumSet.noneOf(SceneTrigger.class) : EnumSet.copyOf(triggers);
    }

    public boolean hasTrigger(SceneTrigger trigger) {
        return triggers.contains(trigger);
    }

    public void setTrigger(SceneTrigger trigger, boolean active) {
        if (active)
            triggers.add(trigger);
        else
            triggers.remove(trigger);
    }

    public void setTriggers(Set<SceneTrigger> triggers) {
        this.triggers.clear();
        if (triggers != null)
            this.triggers.addAll(triggers);
    }

    /** Percent chance (1–100) that an automatic trigger really starts the scene. */
    public int getChancePercent() {
        return chancePercent;
    }

    public void setChancePercent(int chancePercent) {
        this.chancePercent = Math.clamp(chancePercent, 1, 100);
    }

    /** Seconds after a scene ends before triggers may start it again. */
    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.clamp(cooldownSeconds, 0, MAX_COOLDOWN_SECONDS);
    }

    public SceneEnding getEnding() {
        return ending;
    }

    public void setEnding(SceneEnding ending) {
        this.ending = ending == null ? SceneEnding.BEATS_FINISHED : ending;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = Math.clamp(durationSeconds, 10, MAX_DURATION_SECONDS);
    }

    public DayPeriod getEndPeriod() {
        return endPeriod;
    }

    public void setEndPeriod(DayPeriod endPeriod) {
        this.endPeriod = endPeriod == null ? DayPeriod.EVENING : endPeriod;
    }

    public List<SceneRole> getRoles() {
        return List.copyOf(roles);
    }

    public Optional<SceneRole> findRole(String roleId) {
        if (roleId == null)
            return Optional.empty();
        return roles.stream().filter(role -> role.getId().equals(roleId)).findFirst();
    }

    /**
     * The role, other than {@code exceptRoleId}, that the preset already plays. A
     * preset plays at most one role per scene and acts in as many beats as needed.
     */
    public Optional<SceneRole> roleOf(String presetKey, String exceptRoleId) {
        if (presetKey == null)
            return Optional.empty();
        String key = NpcDefinition.toKey(presetKey);
        return roles.stream().filter(role -> key.equals(role.getPresetKey()))
                .filter(role -> !role.getId().equals(exceptRoleId)).findFirst();
    }

    public boolean features(String presetKey) {
        return roleOf(presetKey, null).isPresent();
    }

    /** Adds a role for the preset, with an id unique in this scene. */
    public SceneRole addRole(String presetKey) {
        if (roles.size() >= MAX_ROLES)
            throw new IllegalStateException("A scene holds at most " + MAX_ROLES + " roles.");
        String base = SceneRole.normalizeId(presetKey);
        String id = base;
        for (int suffix = 2; findRole(id).isPresent(); suffix++)
            id = base + "-" + suffix;
        SceneRole role = new SceneRole(id, presetKey);
        roles.add(role);
        return role;
    }

    public void addRole(SceneRole role) {
        if (findRole(role.getId()).isPresent())
            throw new IllegalArgumentException("Role '" + role.getId() + "' already exists.");
        roles.add(role);
    }

    /** Removes the role together with its actions in every beat. */
    public boolean removeRole(String roleId) {
        if (!roles.removeIf(role -> role.getId().equals(roleId)))
            return false;
        beats.forEach(beat -> beat.forgetRole(roleId));
        return true;
    }

    // ---------------------------------------------------------------- beats

    public List<SceneBeat> getBeats() {
        return List.copyOf(beats);
    }

    public Optional<SceneBeat> findBeat(String beatId) {
        if (beatId == null)
            return Optional.empty();
        return beats.stream().filter(beat -> beat.getId().equals(beatId)).findFirst();
    }

    /** Position of the beat, or -1. */
    public int beatIndex(String beatId) {
        for (int index = 0; index < beats.size(); index++) {
            if (beats.get(index).getId().equals(beatId))
                return index;
        }
        return -1;
    }

    /** Replaces every beat; an empty list leaves one empty beat. */
    public void setBeats(List<SceneBeat> replacement) {
        beats.clear();
        if (replacement != null)
            replacement.stream().limit(MAX_BEATS).forEach(beats::add);
        if (beats.isEmpty())
            beats.add(new SceneBeat());
    }

    public boolean canAddBeat() {
        return beats.size() < MAX_BEATS;
    }

    /** Adds an empty beat at the end. */
    public SceneBeat addBeat() {
        if (!canAddBeat())
            throw new IllegalStateException("A scene holds at most " + MAX_BEATS + " beats.");
        SceneBeat beat = new SceneBeat();
        beats.add(beat);
        return beat;
    }

    /** Inserts a copy right after the beat. */
    public Optional<SceneBeat> duplicateBeat(String beatId) {
        int index = beatIndex(beatId);
        if (index < 0 || !canAddBeat())
            return Optional.empty();
        SceneBeat copy = beats.get(index).copy();
        beats.add(index + 1, copy);
        return Optional.of(copy);
    }

    /** Moves the beat one step earlier (-1) or later (+1). */
    public boolean moveBeat(String beatId, int direction) {
        int index = beatIndex(beatId);
        int target = index + Integer.signum(direction);
        if (index < 0 || direction == 0 || target < 0 || target >= beats.size())
            return false;
        java.util.Collections.swap(beats, index, target);
        return true;
    }

    /** Removes the beat; removing the only beat leaves an empty one. */
    public boolean removeBeat(String beatId) {
        if (!beats.removeIf(beat -> beat.getId().equals(beatId)))
            return false;
        if (beats.isEmpty())
            beats.add(new SceneBeat());
        return true;
    }

    /** How many beats the role acts in. */
    public int beatsActedIn(String roleId) {
        return (int) beats.stream().filter(beat -> beat.hasActions(roleId)).count();
    }

    /** Rewrites every role action, including question branches. */
    public boolean mapActions(java.util.function.UnaryOperator<BehaviourAction> mapper) {
        boolean changed = false;
        for (SceneBeat beat : beats)
            changed |= beat.mapActions(mapper);
        for (SceneRole role : roles)
            changed |= role.mapActions(mapper);
        return changed;
    }

    public boolean removeActions(java.util.function.Predicate<BehaviourAction> filter) {
        boolean changed = false;
        for (SceneBeat beat : beats)
            changed |= beat.removeActions(filter);
        for (SceneRole role : roles)
            changed |= role.removeActions(filter);
        return changed;
    }

    /**
     * Visits every beat, wrap-up, and reaction action, including question branches.
     */
    public void forEachAction(java.util.function.Consumer<BehaviourAction> visitor) {
        beats.forEach(beat -> beat.forEachAction(visitor));
        roles.forEach(role -> role.forEachAction(visitor));
    }

    /**
     * Keys of the scenes this scene's Start Scene actions start, in the order they
     * appear, without itself.
     */
    public List<String> startedScenes() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        forEachAction(action -> {
            if (action.type() == BehaviourActionType.START_SCENE)
                tryKey(action.value()).filter(started -> !started.equals(key)).ifPresent(keys::add);
        });
        return List.copyOf(keys);
    }
}
