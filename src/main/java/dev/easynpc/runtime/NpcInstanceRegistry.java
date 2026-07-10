package dev.easynpc.runtime;

import dev.easynpc.dialog.DialogService;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.model.WalkingSpeed;
import dev.easynpc.repository.NpcDefinitionRepository;
import dev.easynpc.repository.NpcInstanceRepository;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NpcInstanceRegistry {
    private final NpcDefinitionRepository definitionRepository;
    private final NpcInstanceRepository instanceRepository;
    private final NpcRenderer renderer;
    private final NativeNpcNavigationService navigationService;
    private final DialogService dialogService;
    private final Map<UUID, NpcInstance> instances = new LinkedHashMap<>();

    public NpcInstanceRegistry(
        NpcDefinitionRepository definitionRepository,
        NpcInstanceRepository instanceRepository,
        NpcRenderer renderer,
        NativeNpcNavigationService navigationService,
        DialogService dialogService
    ) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.renderer = renderer;
        this.navigationService = navigationService;
        this.dialogService = dialogService;
    }

    public void loadPersistedInstances() {
        instances.clear();
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
                dialogService.attach(instance, definition);
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
    }

    public Collection<NpcInstance> findAll() {
        return instances.values();
    }

    public boolean move(NpcInstance instance, Location location) {
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
        return instances.values().stream()
            .filter(instance -> instance.getEntityId() == entityId)
            .findFirst();
    }

    public Optional<LivingEntity> findEntity(NpcInstance instance) {
        if (!instances.containsKey(instance.getId())) {
            return Optional.empty();
        }
        return renderer.findLivingEntity(instance);
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
        renderer.destroy(instance);
        navigationService.destroy(instance);
        dialogService.detach(instance.getId());
        instanceRepository.saveAll(instances.values());
        return true;
    }

    private void spawnInstance(NpcInstance instance, NpcDefinition definition) {
        renderer.spawn(instance, definition);
        dialogService.attach(instance, definition);
    }
}
