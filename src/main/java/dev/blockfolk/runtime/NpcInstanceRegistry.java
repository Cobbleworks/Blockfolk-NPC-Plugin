package dev.blockfolk.runtime;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import dev.blockfolk.dialog.DialogService;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.model.NpcInstance;
import dev.blockfolk.model.WalkingSpeed;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.NpcInstanceRepository;

public final class NpcInstanceRegistry implements Listener {

    private final Plugin plugin;
    private final NamespacedKey instanceKey;
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRepository instanceRepository;
    private final NpcRenderer renderer;
    private final NativeNpcNavigationService navigationService;
    private final DialogService dialogService;
    private final Map<UUID, NpcInstance> instances = new LinkedHashMap<>();
    private final Map<Integer, UUID> instancesByEntityId = new java.util.HashMap<>();
    private BiConsumer<NpcInstance, NpcDefinition> spawnListener = (instance, definition) -> {
    };
    private Consumer<NpcInstance> relocationListener = instance -> {
    };

    public void setSpawnListener(BiConsumer<NpcInstance, NpcDefinition> spawnListener) {
        this.spawnListener = spawnListener == null ? (instance, definition) -> {
        } : spawnListener;
    }

    public void setRelocationListener(Consumer<NpcInstance> relocationListener) {
        this.relocationListener = relocationListener == null ? instance -> {
        } : relocationListener;
    }

    public NpcInstanceRegistry(
            Plugin plugin,
            NpcDefinitionRepository definitionRepository,
            NpcInstanceRepository instanceRepository,
            NpcRenderer renderer,
            NativeNpcNavigationService navigationService,
            DialogService dialogService
    ) {
        this.plugin = plugin;
        this.instanceKey = new NamespacedKey(plugin, "instance-id");
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.renderer = renderer;
        this.navigationService = navigationService;
        this.dialogService = dialogService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMannequinRemoved(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Mannequin mannequin)
                || event.getCause() == EntityRemoveEvent.Cause.UNLOAD
                || event.getCause() == EntityRemoveEvent.Cause.DEATH) {
            return;
        }
        String storedId = mannequin.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        if (storedId == null) {
            return;
        }
        UUID instanceId;
        try {
            instanceId = UUID.fromString(storedId);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        int removedEntityId = mannequin.getEntityId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            NpcInstance instance = instances.get(instanceId);
            if (instance != null && instance.getEntityId() == removedEntityId) {
                deleteInstance(instanceId);
            }
        });
    }

    public void loadPersistedInstances() {
        instances.clear();
        instancesByEntityId.clear();
        for (NpcInstance instance : instanceRepository.loadAll()) {
            if (definitionRepository.find(instance.getDefinitionKey()).isPresent()) {
                instances.put(instance.getId(), instance);
            }
        }
    }

    public NpcInstance spawnPersistent(NpcDefinition definition, Location location) {
        NpcInstance instance = new NpcInstance(UUID.randomUUID(), definition.getKey(), location);
        instances.put(instance.getId(), instance);
        instanceRepository.saveAll(instances.values());
        spawnInstance(instance, definition);
        return instance;
    }

    public void spawnAll() {
        for (NpcInstance instance : instances.values()) {
            definitionRepository.find(instance.getDefinitionKey()).ifPresent(definition -> spawnInstance(instance, definition));
        }
    }

    public void refreshDefinition(NpcDefinition definition) {
        for (NpcInstance instance : instances.values()) {
            if (instance.getDefinitionKey().equals(definition.getKey())) {
                renderer.refresh(instance, definition);
                indexEntity(instance);
            }
        }
    }

    public int deleteInstances(NpcDefinition definition) {
        int removed = 0;
        Iterator<NpcInstance> iterator = instances.values().iterator();
        while (iterator.hasNext()) {
            NpcInstance instance = iterator.next();
            if (!instance.getDefinitionKey().equals(definition.getKey())) {
                continue;
            }
            renderer.destroy(instance);
            instancesByEntityId.values().remove(instance.getId());
            navigationService.destroy(instance);
            dialogService.detach(instance.getId());
            iterator.remove();
            removed++;
        }
        if (removed > 0) {
            instanceRepository.saveAll(instances.values());
        }
        return removed;
    }

    public void saveAndDespawnAll() {
        instanceRepository.saveAll(instances.values());
        for (NpcInstance instance : instances.values()) {
            renderer.destroy(instance);
            navigationService.destroy(instance);
            dialogService.detach(instance.getId());
        }
        instancesByEntityId.clear();
    }

    public Collection<NpcInstance> findAll() {
        return instances.values();
    }

    public boolean isNavigationEntity(Entity entity) {
        return navigationService.isNavigator(entity);
    }

    public boolean move(NpcInstance instance, Location location) {
        return move(instance, location, false);
    }

    public boolean relocate(NpcInstance instance, Location location) {
        return move(instance, location, true);
    }

    private boolean move(NpcInstance instance, Location location, boolean updateSpawnLocation) {
        if (!instances.containsKey(instance.getId())) {
            return false;
        }

        // A routed NPC has an invisible navigation mob at its previous location.
        // Remove it before teleporting so the next movement tick starts from the
        // new location instead of snapping the mannequin back to the old path.
        navigationService.destroy(instance);
        if (!renderer.move(instance, location)) {
            return false;
        }
        if (updateSpawnLocation) {
            instance.setSpawnLocation(location);
            relocationListener.accept(instance);
        }
        dialogService.move(instance);
        instanceRepository.saveAll(instances.values());
        return true;
    }

    public NativeNpcNavigationService.NavigationStatus navigate(
            NpcInstance instance,
            Location target,
            WalkingSpeed walkingSpeed
    ) {
        if (!instances.containsKey(instance.getId())) {
            return NativeNpcNavigationService.NavigationStatus.STALLED;
        }
        NativeNpcNavigationService.NavigationUpdate update = navigationService.navigate(instance, target, walkingSpeed);
        if (renderer.move(instance, update.location())) {
            dialogService.move(instance);
        }
        return update.status();
    }

    public void stopNavigating(NpcInstance instance) {
        navigationService.stop(instance);
    }

    public Optional<NpcInstance> findByEntityId(int entityId) {
        UUID instanceId = instancesByEntityId.get(entityId);
        return instanceId == null ? Optional.empty() : Optional.ofNullable(instances.get(instanceId));
    }

    public Optional<NpcInstance> findById(UUID instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    public Optional<LivingEntity> findEntity(NpcInstance instance) {
        if (!instances.containsKey(instance.getId())) {
            return Optional.empty();
        }
        return renderer.findLivingEntity(instance);
    }

    /** Returns the rendered position, including movement caused by entity collisions. */
    public Location currentLocation(NpcInstance instance) {
        if (!instances.containsKey(instance.getId())) {
            return instance.getLocation();
        }
        Location location = renderer.currentLocation(instance).orElseGet(instance::getLocation);
        instance.setLocation(location);
        return location.clone();
    }

    public void pose(NpcInstance instance, Pose pose) {
        renderer.pose(instance, pose);
    }

    public void stand(NpcInstance instance) {
        renderer.stand(instance);
    }

    public void wave(NpcInstance instance) {
        renderer.wave(instance);
    }

    public void jump(NpcInstance instance) {
        renderer.jump(instance);
    }

    public void lookAt(NpcInstance instance, Location target) {
        renderer.lookAt(instance, target);
    }

    public Collection<NpcInstance> findByDefinition(NpcDefinition definition) {
        return instances.values().stream()
                .filter(instance -> instance.getDefinitionKey().equals(definition.getKey()))
                .toList();
    }

    public boolean deleteInstance(UUID instanceId) {
        NpcInstance instance = instances.remove(instanceId);
        if (instance == null) {
            return false;
        }
        instancesByEntityId.remove(instance.getEntityId());
        renderer.destroy(instance);
        navigationService.destroy(instance);
        dialogService.detach(instance.getId());
        instanceRepository.saveAll(instances.values());
        return true;
    }

    private void spawnInstance(NpcInstance instance, NpcDefinition definition) {
        // Clear any tagged dialog display left by an earlier server run,
        // including displays from the removed chatter system.
        dialogService.detach(instance.getId());
        renderer.spawn(instance, definition);
        indexEntity(instance);
        spawnListener.accept(instance, definition);
    }

    private void indexEntity(NpcInstance instance) {
        instancesByEntityId.values().remove(instance.getId());
        if (instance.getEntityId() != 0) instancesByEntityId.put(instance.getEntityId(), instance.getId());
    }
}
