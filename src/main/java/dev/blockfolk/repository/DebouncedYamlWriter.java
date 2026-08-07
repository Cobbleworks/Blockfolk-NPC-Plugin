package dev.blockfolk.repository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Coalesces YAML updates on the server thread and performs file I/O
 * asynchronously.
 */
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
        if (snapshotTask != null)
            snapshotTask.cancel();
        snapshotTask = Bukkit.getScheduler().runTaskLater(plugin, this::snapshot, DELAY_TICKS);
    }

    synchronized void delete(File file) {
        pending.put(file.toPath(), () -> null);
        if (snapshotTask != null)
            snapshotTask.cancel();
        snapshotTask = Bukkit.getScheduler().runTaskLater(plugin, this::snapshot, DELAY_TICKS);
    }

    void flush() {
        Map<Path, String> updates;
        synchronized (this) {
            if (snapshotTask != null)
                snapshotTask.cancel();
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
            if (updates.isEmpty())
                return;
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
                    if (Files.exists(path)) {
                        Files.move(path, path.resolveSibling(path.getFileName() + ".bak"),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                }
                Path parent = path.getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
                try {
                    Files.writeString(temporary, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                    if (Files.exists(path)) {
                        Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not persist " + path.getFileName() + ".", exception);
            }
        });
    }
}
