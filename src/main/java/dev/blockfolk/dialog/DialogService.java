package dev.blockfolk.dialog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Times the dialog lines and AI thinking indicator above NPCs. The renderer
 * draws them; a line is shown to everyone even when the NPC's name only shows
 * on mouse-over, so players notice it.
 */
public final class DialogService {

    /**
     * Draws or clears (null) an NPC's line. Returns false when it is not loaded.
     */
    @FunctionalInterface
    public interface LineDisplay {
        boolean show(NpcInstance instance, Component line);
    }

    private static final String PROCESSING_PREFIX = "Thinking";
    private static final int MINIMUM_LINE_DURATION_SECONDS = 3;
    private static final int CHARACTERS_PER_SECOND = 12;

    private final Plugin plugin;
    private final NamespacedKey instanceKey;
    private final Map<UUID, DialogRuntime> dialogs = new HashMap<>();
    private LineDisplay lineDisplay = (instance, line) -> false;
    private BukkitTask task;

    public DialogService(Plugin plugin) {
        this.plugin = plugin;
        this.instanceKey = new NamespacedKey(plugin, "dialog-instance-id");
    }

    public void setLineDisplay(LineDisplay lineDisplay) {
        this.lineDisplay = lineDisplay == null ? (instance, line) -> false : lineDisplay;
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
        removeLegacyDisplays(null);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        dialogs.values().forEach(runtime -> setLine(runtime.instance, null));
        dialogs.clear();
    }

    public void move(NpcInstance instance) {
        // Mannequin descriptions are part of the entity and move with it.
    }

    public void detach(UUID instanceId) {
        DialogRuntime runtime = dialogs.remove(instanceId);
        if (runtime != null)
            setLine(runtime.instance, null);
        removeLegacyDisplays(instanceId);
    }

    /**
     * Shows one behaviour-supplied hologram line for a duration based on its text
     * length.
     */
    public void showHologram(NpcInstance instance, NpcDefinition definition, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        DialogRuntime active = dialogs.get(instance.getId());
        if (active != null && active.processing) {
            active.pendingLine = Component.text(line);
            active.pendingLineTicks = lineDurationSeconds(line) * 20;
            return;
        }
        if (!setLine(instance, Component.text(line))) {
            return;
        }
        DialogRuntime runtime = dialogs.computeIfAbsent(instance.getId(), ignored -> new DialogRuntime(instance));
        runtime.remainingTicks = lineDurationSeconds(line) * 20;
    }

    public void showProcessing(NpcInstance instance) {
        if (!setLine(instance, processingText(1)))
            return;
        DialogRuntime runtime = dialogs.computeIfAbsent(instance.getId(), ignored -> new DialogRuntime(instance));
        runtime.processing = true;
        runtime.processingFrame = 0;
        runtime.processingFrameTicks = 0;
        runtime.pendingLine = null;
    }

    public void hideProcessing(NpcInstance instance) {
        DialogRuntime runtime = dialogs.get(instance.getId());
        if (runtime == null || !runtime.processing)
            return;
        runtime.processing = false;
        if (runtime.pendingLine != null && setLine(instance, runtime.pendingLine)) {
            runtime.remainingTicks = runtime.pendingLineTicks;
            runtime.pendingLine = null;
        } else {
            setLine(instance, null);
            dialogs.remove(instance.getId());
        }
    }

    private void removeLegacyDisplays(UUID instanceId) {
        String expectedId = instanceId == null ? null : instanceId.toString();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                String taggedId = display.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
                if (taggedId != null && (expectedId == null || expectedId.equals(taggedId))) {
                    display.remove();
                }
            }
        }
    }

    private boolean setLine(NpcInstance instance, Component line) {
        return lineDisplay.show(instance, line);
    }

    private void tick() {
        Iterator<Map.Entry<UUID, DialogRuntime>> dialogIterator = dialogs.entrySet().iterator();
        while (dialogIterator.hasNext()) {
            DialogRuntime runtime = dialogIterator.next().getValue();
            if (runtime.processing) {
                if (++runtime.processingFrameTicks >= 20) {
                    runtime.processingFrameTicks = 0;
                    runtime.processingFrame = (runtime.processingFrame + 1) % 3;
                    setLine(runtime.instance, processingText(runtime.processingFrame + 1));
                }
                continue;
            }
            // A line keeps counting down while its NPC is unloaded.
            if (--runtime.remainingTicks <= 0) {
                setLine(runtime.instance, null);
                dialogIterator.remove();
            }
        }
    }

    private Component processingText(int dots) {
        return Component.text(PROCESSING_PREFIX + ".".repeat(dots), NamedTextColor.GOLD);
    }

    private static final class DialogRuntime {

        private final NpcInstance instance;
        private int remainingTicks;
        private boolean processing;
        private int processingFrame;
        private int processingFrameTicks;
        private Component pendingLine;
        private int pendingLineTicks;

        private DialogRuntime(NpcInstance instance) {
            this.instance = instance;
        }
    }
}
