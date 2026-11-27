package dev.blockfolk.runtime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * The name and dialog line above each NPC, drawn by text displays riding the
 * mannequin. The mannequin's own name tag takes the light level at the NPC, so
 * in dim rooms it renders nearly black; displays are forced to full brightness.
 * Riding also hides that name tag, because the client never draws one on an
 * entity that carries a passenger.
 * <p>
 * Displays cannot be targeted by the crosshair, so a name shown only on
 * mouse-over is revealed per player by ray tracing their view. A dialog line is
 * always shown to everyone, together with the name, so players notice it.
 */
final class NpcLabels {

    /**
     * Bottom of the lowest line above the head (the mannequin's passenger seat).
     */
    static final float BASE_OFFSET = 0.27f;
    /** One line of text plus its background padding. */
    static final float LINE_HEIGHT = 0.27f;
    /** Wide enough that a dialog line never wraps, like a vanilla name tag. */
    private static final int LINE_WIDTH = 10_000;
    private static final double DEFAULT_HOVER_RANGE = 3.0;

    private final Plugin plugin;
    private final NamespacedKey labelKey;
    private final Map<UUID, Label> labels = new HashMap<>();
    /** The hover-only name display each player is currently shown. */
    private final Map<UUID, UUID> hoveredByPlayer = new HashMap<>();

    NpcLabels(Plugin plugin) {
        this.plugin = plugin;
        this.labelKey = new NamespacedKey(plugin, "label-instance-id");
    }

    /**
     * Applies the definition's name, in its name color (as in chat), and name
     * visibility.
     */
    void apply(UUID instanceId, Mannequin mannequin, String name, TextColor color, boolean nameAlwaysShown) {
        Label label = labels.computeIfAbsent(instanceId, ignored -> new Label());
        label.name = Component.text(name, color);
        label.nameAlwaysShown = nameAlwaysShown;
        update(instanceId, label, mannequin);
    }

    /**
     * Sets or clears (null) the dialog line. It is kept while the NPC is unloaded
     * and appears when the NPC does.
     */
    void setLine(UUID instanceId, Mannequin mannequin, Component line) {
        Label label = labels.computeIfAbsent(instanceId, ignored -> new Label());
        label.line = line;
        if (mannequin != null)
            update(instanceId, label, mannequin);
    }

    boolean hasLine(UUID instanceId) {
        Label label = labels.get(instanceId);
        return label != null && label.line != null;
    }

    /** Whether the display is a label that is not the current one of its NPC. */
    boolean isStale(TextDisplay display) {
        String taggedId = display.getPersistentDataContainer().get(labelKey, PersistentDataType.STRING);
        if (taggedId == null)
            return false;
        Label label;
        try {
            label = labels.get(UUID.fromString(taggedId));
        } catch (IllegalArgumentException exception) {
            return true;
        }
        UUID displayId = display.getUniqueId();
        return label == null || !(displayId.equals(label.nameDisplay) || displayId.equals(label.lineDisplay));
    }

    boolean isLabel(Entity entity) {
        return entity instanceof TextDisplay
                && entity.getPersistentDataContainer().has(labelKey, PersistentDataType.STRING);
    }

    /**
     * Keeps the labels of loaded NPCs riding them, removes those left behind by
     * unloaded or dead NPCs, and updates mouse-over names.
     */
    void tick(Map<UUID, Mannequin> loaded) {
        Map<UUID, Label> hoverOnly = new HashMap<>();
        for (Map.Entry<UUID, Label> entry : labels.entrySet()) {
            Label label = entry.getValue();
            Mannequin mannequin = loaded.get(entry.getKey());
            if (mannequin == null) {
                removeDisplays(label);
                continue;
            }
            update(entry.getKey(), label, mannequin);
            if (!label.shownToAll() && label.nameDisplay != null)
                hoverOnly.put(mannequin.getUniqueId(), label);
        }
        updateHover(hoverOnly);
    }

    void remove(UUID instanceId) {
        Label label = labels.remove(instanceId);
        if (label != null)
            removeDisplays(label);
    }

    void removeAll() {
        labels.values().forEach(this::removeDisplays);
        labels.clear();
        hoveredByPlayer.clear();
    }

    private void update(UUID instanceId, Label label, Mannequin mannequin) {
        boolean shownToAll = label.shownToAll();
        float nameOffset = BASE_OFFSET + (label.line == null ? 0.0f : LINE_HEIGHT);
        TextDisplay name = riding(label.nameDisplay, mannequin);
        if (name == null) {
            clearHover(label.nameDisplay);
            name = spawn(instanceId, mannequin, shownToAll, nameOffset);
            label.nameDisplay = name == null ? null : name.getUniqueId();
        }
        if (name != null) {
            if (name.isVisibleByDefault() != shownToAll) {
                // A player's show/hide override flips meaning with the default, so it
                // must be cleared first.
                clearHover(label.nameDisplay);
                name.setVisibleByDefault(shownToAll);
            }
            setText(name, label.name);
            setOffset(name, nameOffset);
        }

        if (label.line == null) {
            removeDisplay(label.lineDisplay);
            label.lineDisplay = null;
            return;
        }
        TextDisplay line = riding(label.lineDisplay, mannequin);
        if (line == null) {
            line = spawn(instanceId, mannequin, true, BASE_OFFSET);
            label.lineDisplay = line == null ? null : line.getUniqueId();
        }
        if (line != null) {
            setText(line, label.line);
            setOffset(line, BASE_OFFSET);
        }
    }

    /** The display if it still rides the mannequin; otherwise removes it. */
    private static TextDisplay riding(UUID displayId, Mannequin mannequin) {
        Entity existing = displayId == null ? null : Bukkit.getEntity(displayId);
        if (existing instanceof TextDisplay display && display.isValid() && mannequin.equals(display.getVehicle()))
            return display;
        if (existing != null)
            existing.remove();
        return null;
    }

    private TextDisplay spawn(UUID instanceId, Mannequin mannequin, boolean visibleByDefault, float offset) {
        // Spawned at the seat (the top of the mannequin) rather than its feet: the
        // client draws the first tick between the spawn point and the seat, so a
        // display spawned at the feet visibly flies up into place.
        Location location = mannequin.getLocation().add(0.0, mannequin.getHeight(), 0.0);
        location.setPitch(0.0f);
        TextDisplay display = mannequin.getWorld().spawn(location, TextDisplay.class, spawned -> {
            setOffset(spawned, offset);
            // Labels are rebuilt from the NPC, so they never need saving.
            spawned.setPersistent(false);
            spawned.getPersistentDataContainer().set(labelKey, PersistentDataType.STRING, instanceId.toString());
            spawned.setVisibleByDefault(visibleByDefault);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setBrightness(new Display.Brightness(15, 15));
            // The vanilla name tag background, which follows the client's text
            // background opacity setting.
            spawned.setDefaultBackground(true);
            spawned.setShadowed(false);
            spawned.setSeeThrough(false);
            spawned.setLineWidth(LINE_WIDTH);
            spawned.setSilent(true);
            spawned.setInvulnerable(true);
        });
        if (!mannequin.addPassenger(display)) {
            display.remove();
            return null;
        }
        return display;
    }

    private static void setText(TextDisplay display, Component text) {
        if (!Objects.equals(text, display.text()))
            display.text(text);
    }

    private static void setOffset(TextDisplay display, float offset) {
        Transformation current = display.getTransformation();
        if (Math.abs(current.getTranslation().y() - offset) < 1.0E-4f)
            return;
        display.setTransformation(new Transformation(new Vector3f(0.0f, offset, 0.0f), new AxisAngle4f(),
                new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
    }

    private void removeDisplays(Label label) {
        clearHover(label.nameDisplay);
        removeDisplay(label.nameDisplay);
        removeDisplay(label.lineDisplay);
        label.nameDisplay = null;
        label.lineDisplay = null;
    }

    private static void removeDisplay(UUID displayId) {
        Entity display = displayId == null ? null : Bukkit.getEntity(displayId);
        if (display != null)
            display.remove();
    }

    /** Shows each player the hover-only name of the NPC under their crosshair. */
    private void updateHover(Map<UUID, Label> hoverOnly) {
        hoveredByPlayer.keySet().removeIf(playerId -> Bukkit.getPlayer(playerId) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID target = hoverOnly.isEmpty() ? null : hoveredName(player, hoverOnly);
            UUID previous = target == null
                    ? hoveredByPlayer.remove(player.getUniqueId())
                    : hoveredByPlayer.put(player.getUniqueId(), target);
            if (Objects.equals(previous, target))
                continue;
            Entity hidden = previous == null ? null : Bukkit.getEntity(previous);
            if (hidden != null && !hidden.isVisibleByDefault())
                player.hideEntity(plugin, hidden);
            Entity shown = target == null ? null : Bukkit.getEntity(target);
            if (shown != null)
                player.showEntity(plugin, shown);
        }
    }

    /** Undoes every player's mouse-over reveal of the display. */
    private void clearHover(UUID displayId) {
        if (displayId == null)
            return;
        Entity display = Bukkit.getEntity(displayId);
        Iterator<Map.Entry<UUID, UUID>> iterator = hoveredByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            if (!displayId.equals(entry.getValue()))
                continue;
            iterator.remove();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && display != null && !display.isVisibleByDefault())
                player.hideEntity(plugin, display);
        }
    }

    /**
     * The name display of the hover-only NPC the player looks at within their
     * entity reach, like the vanilla crosshair, or null. Blocks in between hide it.
     */
    private static UUID hoveredName(Player player, Map<UUID, Label> hoverOnly) {
        AttributeInstance reach = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        double range = reach == null ? DEFAULT_HOVER_RANGE : reach.getValue();
        Location eye = player.getEyeLocation();
        // Only entities inside the short ray's bounds are tested, so this stays cheap.
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true,
                0.0, entity -> hoverOnly.containsKey(entity.getUniqueId()));
        if (hit == null || hit.getHitEntity() == null)
            return null;
        Label label = hoverOnly.get(hit.getHitEntity().getUniqueId());
        return label == null ? null : label.nameDisplay;
    }

    private static final class Label {
        private Component name = Component.empty();
        private boolean nameAlwaysShown = true;
        private Component line;
        private UUID nameDisplay;
        private UUID lineDisplay;

        /** A dialog line shows the name to everyone, so players know who speaks. */
        private boolean shownToAll() {
            return nameAlwaysShown || line != null;
        }
    }
}
