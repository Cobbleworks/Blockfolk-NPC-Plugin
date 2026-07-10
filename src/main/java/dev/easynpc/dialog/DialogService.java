package dev.easynpc.dialog;

import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DialogService {
    private static final double MAX_CHAT_DISTANCE_SQUARED = 12.0 * 12.0;

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
        Location location = instance.getLocation().add(0.0, 2.25, 0.0);
        TextDisplay display = (TextDisplay) instance.getLocation().getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        display.text(Component.text(lines.getFirst()));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setShadowed(true);
        display.setPersistent(true);
        display.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, instance.getId().toString());
        displays.put(instance.getId(), new DialogRuntime(display, lines, definition.getSecondsPerDialogLine()));
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
        for (DialogRuntime runtime : displays.values()) {
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

        private DialogRuntime(TextDisplay display, List<String> lines, int secondsPerLine) {
            this.display = display;
            this.lines = lines;
            this.secondsPerLine = secondsPerLine;
        }
    }

    private static final class ChatRuntime {
        private final UUID instanceId;
        private final Location location;
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
