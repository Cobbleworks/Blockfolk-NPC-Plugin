package dev.easynpc.dialog;

import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DialogService {
    private final Plugin plugin;
    private final Map<UUID, DialogRuntime> displays = new HashMap<>();
    private BukkitTask task;

    public DialogService(Plugin plugin) {
        this.plugin = plugin;
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

    public void attach(NpcInstance instance, NpcDefinition definition) {
        detach(instance.getId());
        List<String> lines = definition.getDialogLines();
        if (lines.isEmpty() || instance.getLocation().getWorld() == null) {
            return;
        }
        Location location = instance.getLocation().add(0.0, 2.25, 0.0);
        TextDisplay display = (TextDisplay) instance.getLocation().getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.text(Component.text(lines.getFirst()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setPersistent(false);
        displays.put(instance.getId(), new DialogRuntime(display, lines, definition.getSecondsPerDialogLine()));
    }

    public void detach(UUID instanceId) {
        DialogRuntime runtime = displays.remove(instanceId);
        if (runtime != null) {
            runtime.display.remove();
        }
    }

    private void tick() {
        for (DialogRuntime runtime : displays.values()) {
            runtime.ticks++;
            if (runtime.ticks < runtime.secondsPerLine * 20) {
                continue;
            }
            runtime.ticks = 0;
            runtime.index = (runtime.index + 1) % runtime.lines.size();
            runtime.display.text(Component.text(runtime.lines.get(runtime.index)));
        }
    }

    private static final class DialogRuntime {
        private final TextDisplay display;
        private final List<String> lines;
        private final int secondsPerLine;
        private int index;
        private int ticks;

        private DialogRuntime(TextDisplay display, List<String> lines, int secondsPerLine) {
            this.display = display;
            this.lines = lines;
            this.secondsPerLine = secondsPerLine;
        }
    }
}
