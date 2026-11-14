package dev.blockfolk.runtime;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.repository.NpcDefinitionRepository;

/**
 * Holds plugin chunk tickets around every instance whose preset has <em>Keep
 * Chunks Loaded</em> on, so the NPC keeps walking routes and other paths when
 * no player is nearby. The ticketed square follows the NPC; chunks it left
 * behind are released.
 */
public final class NpcChunkLoadService implements Listener {

    public static final int DEFAULT_RADIUS = 1;
    public static final int MAX_RADIUS = 3;
    private static final long INTERVAL_TICKS = 10L;

    private final Plugin plugin;
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRegistry instanceRegistry;
    private final NativeNpcNavigationService navigationService;
    private final int radius;
    private final Set<ChunkKey> ticketed = new HashSet<>();
    private final Set<ChunkKey> loading = new HashSet<>();
    private Set<ChunkKey> wanted = Set.of();
    private BukkitTask task;

    public NpcChunkLoadService(Plugin plugin, NpcDefinitionRepository definitionRepository,
            NpcInstanceRegistry instanceRegistry, NativeNpcNavigationService navigationService, int radius) {
        this.plugin = plugin;
        this.definitionRepository = definitionRepository;
        this.instanceRegistry = instanceRegistry;
        this.navigationService = navigationService;
        this.radius = clampRadius(radius);
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        wanted = Set.of();
        loading.clear();
        for (ChunkKey key : ticketed)
            release(key);
        ticketed.clear();
        navigationService.setAlwaysActive(Set.of());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
        // The world's tickets go with it.
        ticketed.removeIf(key -> key.worldId().equals(worldId));
        loading.removeIf(key -> key.worldId().equals(worldId));
    }

    private void tick() {
        Set<ChunkKey> next = new HashSet<>();
        Set<UUID> keptActive = new HashSet<>();
        for (NpcInstance instance : instanceRegistry.findAll()) {
            if (instance.isAwaitingRespawn())
                continue;
            NpcDefinition definition = definitionRepository.find(instance.getDefinitionKey()).orElse(null);
            if (definition == null || !definition.isKeepChunksLoaded())
                continue;
            // An unloaded mannequin (for example right after startup) still gets
            // its last known chunk loaded, which brings the entity back.
            Location location = instanceRegistry.findEntity(instance).map(Entity::getLocation)
                    .orElseGet(instance::getLocation);
            World world = location.getWorld();
            if (world == null)
                continue;
            keptActive.add(instance.getId());
            next.addAll(chunksAround(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4, radius));
        }
        navigationService.setAlwaysActive(keptActive);
        wanted = next;

        Iterator<ChunkKey> held = ticketed.iterator();
        while (held.hasNext()) {
            ChunkKey key = held.next();
            if (!next.contains(key)) {
                release(key);
                held.remove();
            }
        }
        for (ChunkKey key : next) {
            if (ticketed.contains(key) || loading.contains(key))
                continue;
            World world = Bukkit.getWorld(key.worldId());
            if (world == null)
                continue;
            if (world.isChunkLoaded(key.x(), key.z())) {
                acquire(world, key);
                continue;
            }
            // Adding a ticket to an unloaded chunk loads it synchronously; load it
            // in the background first so a walking NPC never stalls the server.
            loading.add(key);
            world.getChunkAtAsync(key.x(), key.z()).whenComplete((chunk, error) -> {
                Runnable finish = () -> {
                    loading.remove(key);
                    if (error == null && chunk != null && task != null && wanted.contains(key)
                            && !ticketed.contains(key))
                        acquire(world, key);
                };
                if (Bukkit.isPrimaryThread())
                    finish.run();
                else if (plugin.isEnabled())
                    Bukkit.getScheduler().runTask(plugin, finish);
            });
        }
    }

    private void acquire(World world, ChunkKey key) {
        // false only means this plugin already holds the ticket.
        world.addPluginChunkTicket(key.x(), key.z(), plugin);
        ticketed.add(key);
    }

    private void release(ChunkKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world != null)
            world.removePluginChunkTicket(key.x(), key.z(), plugin);
    }

    static int clampRadius(int radius) {
        return Math.max(0, Math.min(MAX_RADIUS, radius));
    }

    /** The square of chunks within {@code radius} of the given chunk. */
    static Set<ChunkKey> chunksAround(UUID worldId, int chunkX, int chunkZ, int radius) {
        Set<ChunkKey> chunks = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++)
                chunks.add(new ChunkKey(worldId, chunkX + dx, chunkZ + dz));
        }
        return chunks;
    }

    record ChunkKey(UUID worldId, int x, int z) {
    }
}
