package dev.blockfolk.dialog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import net.kyori.adventure.text.Component;

public final class DialogService {

    private static final double DIALOG_DISPLAY_Y_OFFSET = 2.4;
    private static final int MINIMUM_LINE_DURATION_SECONDS = 3;
    private static final int CHARACTERS_PER_SECOND = 12;

    private final Plugin plugin;
    private final NamespacedKey instanceKey;
    private final Map<UUID, DialogRuntime> displays = new HashMap<>();
    private BukkitTask task;

    public DialogService(Plugin plugin) {
        this.plugin = plugin;
        this.instanceKey = new NamespacedKey(plugin, "dialog-instance-id");
    }

    public static int lineDurationSeconds(String line) {
        if (line == null) {
            return MINIMUM_LINE_DURATION_SECONDS;
        }
        int characterCount = line.codePointCount(0, line.length());
        int readingSeconds = (characterCount + CHARACTERS_PER_SECOND - 1) / CHARACTERS_PER_SECOND;
        return Math.max(MINIMUM_LINE_DURATION_SECONDS, readingSeconds);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (DialogRuntime runtime : displays.values()) {
            runtime.display.remove();
        }
        displays.clear();
    }

    private void configureDisplay(TextDisplay display, NpcInstance instance) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setTeleportDuration(1);
        display.setPersistent(true);
        display.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instance.getId().toString());
    }

    public void move(NpcInstance instance) {
        Location location = instance.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        DialogRuntime runtime = displays.get(instance.getId());
        if (runtime != null && runtime.display.isValid()) {
            runtime.display.teleport(location.clone().add(0.0, DIALOG_DISPLAY_Y_OFFSET, 0.0));
        }
    }

    public void detach(UUID instanceId) {
        DialogRuntime runtime = displays.remove(instanceId);
        if (runtime != null) {
            runtime.display.remove();
        }
        removeTaggedDisplays(instanceId);
    }

    /**
     * Shows one behaviour-supplied hologram line for a duration based on its
     * text length.
     */
    public void showHologram(NpcInstance instance, NpcDefinition definition, String line) {
        if (line == null || line.isBlank() || instance.getLocation().getWorld() == null) {
            return;
        }
        DialogRuntime runtime = displays.get(instance.getId());
        if (runtime == null || !runtime.display.isValid()) {
            Location location = instance.getLocation().add(0.0, DIALOG_DISPLAY_Y_OFFSET, 0.0);
            TextDisplay display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
            configureDisplay(display, instance);
            runtime = new DialogRuntime(display);
            displays.put(instance.getId(), runtime);
        }
        runtime.display.text(Component.text(line));
        runtime.overrideSeconds = lineDurationSeconds(line);
    }

    private void removeTaggedDisplays(UUID instanceId) {
        String expectedId = instanceId.toString();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                String taggedId = display.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
                if (expectedId.equals(taggedId)) {
                    display.remove();
                }
            }
        }
    }

    private void tick() {
        Iterator<Map.Entry<UUID, DialogRuntime>> displayIterator = displays.entrySet().iterator();
        while (displayIterator.hasNext()) {
            DialogRuntime runtime = displayIterator.next().getValue();
            if (--runtime.overrideSeconds <= 0) {
                runtime.display.remove();
                displayIterator.remove();
            }
        }
    }

    private static final class DialogRuntime {

        private final TextDisplay display;
        private int overrideSeconds;

        private DialogRuntime(TextDisplay display) {
            this.display = display;
        }
    }
}
