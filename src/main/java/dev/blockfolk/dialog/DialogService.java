package dev.blockfolk.dialog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import net.kyori.adventure.text.Component;

public final class DialogService {

    private static final String PROCESSING_PREFIX = "Thinking";
    private static final int MINIMUM_LINE_DURATION_SECONDS = 3;
    private static final int CHARACTERS_PER_SECOND = 12;

    private final Plugin plugin;
    private final NamespacedKey instanceKey;
    private final Map<UUID, DialogRuntime> dialogs = new HashMap<>();
    private Function<NpcInstance, Optional<LivingEntity>> entityProvider = ignored -> Optional.empty();
    private BukkitTask task;

    public DialogService(Plugin plugin) {
        this.plugin = plugin;
        this.instanceKey = new NamespacedKey(plugin, "dialog-instance-id");
    }

    public void setEntityProvider(Function<NpcInstance, Optional<LivingEntity>> entityProvider) {
        this.entityProvider = entityProvider == null ? ignored -> Optional.empty() : entityProvider;
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
        dialogs.values().forEach(runtime -> setDescription(runtime.instance, null));
        dialogs.clear();
    }

    public void move(NpcInstance instance) {
        // Mannequin descriptions are part of the entity and move with it.
    }

    public void detach(UUID instanceId) {
        DialogRuntime runtime = dialogs.remove(instanceId);
        if (runtime != null) {
            setDescription(runtime.instance, null);
        }
        removeLegacyDisplays(instanceId);
    }

    /**
     * Shows one behaviour-supplied hologram line for a duration based on its text
     * length.
     */
    public void showHologram(NpcInstance instance, NpcDefinition definition, String line) {
        if (line == null || line.isBlank() || !setDescription(instance, Component.text(line))) {
            return;
        }
        DialogRuntime runtime = dialogs.computeIfAbsent(instance.getId(), ignored -> new DialogRuntime(instance));
        runtime.processing = false;
        runtime.remainingTicks = lineDurationSeconds(line) * 20;
    }

    public void showProcessing(NpcInstance instance) {
        if (!setDescription(instance, Component.text(PROCESSING_PREFIX + ".")))
            return;
        DialogRuntime runtime = dialogs.computeIfAbsent(instance.getId(), ignored -> new DialogRuntime(instance));
        runtime.processing = true;
        runtime.processingFrame = 0;
        runtime.processingFrameTicks = 0;
    }

    public void hideProcessing(NpcInstance instance) {
        DialogRuntime runtime = dialogs.get(instance.getId());
        if (runtime == null || !runtime.processing)
            return;
        setDescription(instance, null);
        dialogs.remove(instance.getId());
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

    private boolean setDescription(NpcInstance instance, Component description) {
        LivingEntity entity = entityProvider.apply(instance).orElse(null);
        if (!(entity instanceof Mannequin mannequin) || !mannequin.isValid()) {
            return false;
        }
        mannequin.setDescription(description);
        return true;
    }

    private void tick() {
        Iterator<Map.Entry<UUID, DialogRuntime>> dialogIterator = dialogs.entrySet().iterator();
        while (dialogIterator.hasNext()) {
            DialogRuntime runtime = dialogIterator.next().getValue();
            if (entityProvider.apply(runtime.instance).filter(Mannequin.class::isInstance).isEmpty()) {
                dialogIterator.remove();
                continue;
            }
            if (runtime.processing) {
                if (++runtime.processingFrameTicks >= 20) {
                    runtime.processingFrameTicks = 0;
                    runtime.processingFrame = (runtime.processingFrame + 1) % 3;
                    setDescription(runtime.instance,
                            Component.text(PROCESSING_PREFIX + ".".repeat(runtime.processingFrame + 1)));
                }
                continue;
            }
            if (--runtime.remainingTicks <= 0) {
                setDescription(runtime.instance, null);
                dialogIterator.remove();
            }
        }
    }

    private static final class DialogRuntime {

        private final NpcInstance instance;
        private int remainingTicks;
        private boolean processing;
        private int processingFrame;
        private int processingFrameTicks;

        private DialogRuntime(NpcInstance instance) {
            this.instance = instance;
        }
    }
}
