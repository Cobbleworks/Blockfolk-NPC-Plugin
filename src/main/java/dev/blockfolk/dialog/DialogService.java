package dev.blockfolk.dialog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import net.kyori.adventure.text.Component;

public final class DialogService {

    private static final double MAX_CHAT_DISTANCE_SQUARED = 12.0 * 12.0;
    private static final double DIALOG_DISPLAY_Y_OFFSET = 2.4;

    private final Plugin plugin;
    private final NamespacedKey instanceKey;
    private final Map<UUID, DialogRuntime> displays = new HashMap<>();
    private final Map<UUID, ChatRuntime> chats = new HashMap<>();
    private BukkitTask task;

    public DialogService(Plugin plugin) {
        this.plugin = plugin;
        this.instanceKey = new NamespacedKey(plugin, "dialog-instance-id");
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
        chats.clear();
    }

    public void attach(NpcInstance instance, NpcDefinition definition) {
        detach(instance.getId());
        List<String> lines = definition.getDialogLines();
        if (lines.isEmpty() || instance.getLocation().getWorld() == null) {
            return;
        }
        Location location = instance.getLocation().add(0.0, DIALOG_DISPLAY_Y_OFFSET, 0.0);
        TextDisplay display = (TextDisplay) instance.getLocation().getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.text(Component.text(lines.getFirst()));
        configureDisplay(display, instance);
        displays.put(instance.getId(), new DialogRuntime(display, lines, definition.getSecondsPerDialogLine()));
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
        for (ChatRuntime chat : chats.values()) {
            if (chat.instanceId.equals(instance.getId())) {
                chat.location = location.clone();
            }
        }
    }

    public void detach(UUID instanceId) {
        DialogRuntime runtime = displays.remove(instanceId);
        if (runtime != null) {
            runtime.display.remove();
        }
        chats.values().removeIf(chat -> chat.instanceId.equals(instanceId));
        removeTaggedDisplays(instanceId);
    }

    public void startChat(Player player, NpcInstance instance, NpcDefinition definition) {
        List<String> lines = definition.getDialogLines();
        if (lines.isEmpty()) {
            chats.remove(player.getUniqueId());
            return;
        }

        ChatRuntime runtime = new ChatRuntime(
                instance.getId(),
                instance.getLocation(),
                definition.getDisplayName(),
                lines,
                definition.getSecondsPerDialogLine()
        );
        chats.put(player.getUniqueId(), runtime);
        sendChatLine(player, runtime);
    }

    /**
     * Shows one behaviour-supplied hologram line for the preset's normal line
     * duration.
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
            runtime = new DialogRuntime(display, List.of(), definition.getSecondsPerDialogLine());
            displays.put(instance.getId(), runtime);
        }
        runtime.display.text(Component.text(line));
        runtime.overrideSeconds = definition.getSecondsPerDialogLine();
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
            if (runtime.overrideSeconds > 0) {
                if (--runtime.overrideSeconds > 0) {
                    continue;
                }
                if (runtime.lines.isEmpty()) {
                    runtime.display.remove();
                    displayIterator.remove();
                    continue;
                }
                runtime.display.text(Component.text(runtime.lines.get(runtime.index)));
                runtime.elapsedSeconds = 0;
                continue;
            }
            if (runtime.lines.isEmpty()) {
                continue;
            }
            runtime.elapsedSeconds++;
            if (runtime.elapsedSeconds < runtime.secondsPerLine) {
                continue;
            }
            runtime.elapsedSeconds = 0;
            runtime.index = (runtime.index + 1) % runtime.lines.size();
            runtime.display.text(Component.text(runtime.lines.get(runtime.index)));
        }

        Iterator<Map.Entry<UUID, ChatRuntime>> iterator = chats.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ChatRuntime> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            ChatRuntime runtime = entry.getValue();
            if (isTooFarAway(player, runtime.location)) {
                player.sendMessage(Component.text(
                        "You are too far away from " + runtime.displayName + ". Dialog stopped."
                ));
                iterator.remove();
                continue;
            }

            runtime.elapsedSeconds++;
            if (runtime.elapsedSeconds < runtime.secondsPerLine) {
                continue;
            }
            runtime.elapsedSeconds = 0;
            runtime.index = (runtime.index + 1) % runtime.lines.size();
            sendChatLine(player, runtime);
        }
    }

    private boolean isTooFarAway(Player player, Location npcLocation) {
        return player.getWorld() != npcLocation.getWorld()
                || player.getLocation().distanceSquared(npcLocation) > MAX_CHAT_DISTANCE_SQUARED;
    }

    private void sendChatLine(Player player, ChatRuntime runtime) {
        player.sendMessage(Component.text(runtime.displayName + ": " + runtime.lines.get(runtime.index)));
    }

    private static final class DialogRuntime {

        private final TextDisplay display;
        private final List<String> lines;
        private final int secondsPerLine;
        private int index;
        private int elapsedSeconds;
        private int overrideSeconds;

        private DialogRuntime(TextDisplay display, List<String> lines, int secondsPerLine) {
            this.display = display;
            this.lines = lines;
            this.secondsPerLine = secondsPerLine;
        }
    }

    private static final class ChatRuntime {

        private final UUID instanceId;
        private Location location;
        private final String displayName;
        private final List<String> lines;
        private final int secondsPerLine;
        private int index;
        private int elapsedSeconds;

        private ChatRuntime(
                UUID instanceId,
                Location location,
                String displayName,
                List<String> lines,
                int secondsPerLine
        ) {
            this.instanceId = instanceId;
            this.location = location;
            this.displayName = displayName;
            this.lines = lines;
            this.secondsPerLine = secondsPerLine;
        }
    }
}
