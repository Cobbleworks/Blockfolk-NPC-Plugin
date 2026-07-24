package dev.blockfolk.repository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Coalesces YAML updates on the server thread and performs file I/O asynchronously. */
final class DebouncedYamlWriter {

    private static final long DELAY_TICKS = 10L;

    private final JavaPlugin plugin;
    private final Map<Path, Supplier<YamlConfiguration>> pending = new HashMap<>();
    private BukkitTask snapshotTask;
    private CompletableFuture<Void> writeChain = CompletableFuture.completedFuture(null);

    DebouncedYamlWriter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    synchronized void queue(File file, Supplier<YamlConfiguration> configuration) {
        pending.put(file.toPath(), configuration);
        if (snapshotTask != null) snapshotTask.cancel();
        snapshotTask = Bukkit.getScheduler().runTaskLater(plugin, this::snapshot, DELAY_TICKS);
    }

    synchronized void delete(File file) {
        pending.put(file.toPath(), () -> null);
        if (snapshotTask != null) snapshotTask.cancel();
        snapshotTask = Bukkit.getScheduler().runTaskLater(plugin, this::snapshot, DELAY_TICKS);
    }

    void flush() {
        Map<Path, String> updates;
        synchronized (this) {
            if (snapshotTask != null) snapshotTask.cancel();
            snapshotTask = null;
            updates = materializePending();
        }
        writeChain.join();
        write(updates);
    }

    private void snapshot() {
        Map<Path, String> updates;
        synchronized (this) {
            snapshotTask = null;
            updates = materializePending();
            if (updates.isEmpty()) return;
            writeChain = writeChain.thenRunAsync(() -> write(updates));
        }
    }

    private Map<Path, String> materializePending() {
        Map<Path, String> updates = new HashMap<>();
        pending.forEach((path, source) -> {
            YamlConfiguration configuration = source.get();
            updates.put(path, configuration == null ? null : configuration.saveToString());
        });
        pending.clear();
        return updates;
    }

    private void write(Map<Path, String> updates) {
        updates.forEach((path, content) -> {
            try {
                if (content == null) {
                    Files.deleteIfExists(path);
                    return;
                }
                Path parent = path.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(path, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not persist " + path.getFileName() + ".", exception);
            }
        });
    }
}
