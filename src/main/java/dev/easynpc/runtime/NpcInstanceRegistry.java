package dev.easynpc.runtime;

import dev.easynpc.dialog.DialogService;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.model.NpcInstance;
import dev.easynpc.repository.NpcDefinitionRepository;
import dev.easynpc.repository.NpcInstanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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
    private final DialogService dialogService;
    private final Map<UUID, NpcInstance> instances = new LinkedHashMap<>();

    public NpcInstanceRegistry(
        NpcDefinitionRepository definitionRepository,
        NpcInstanceRepository instanceRepository,
        NpcRenderer renderer,
        DialogService dialogService
    ) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.renderer = renderer;
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

    public void spawnAllOnline() {
        for (NpcInstance instance : instances.values()) {
            definitionRepository.find(instance.getDefinitionKey()).ifPresent(definition -> spawnInstance(instance, definition));
        }
    }

    public void renderFor(Player player) {
        for (NpcInstance instance : instances.values()) {
            definitionRepository.find(instance.getDefinitionKey()).ifPresent(definition -> renderer.spawnFor(player, instance, definition));
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
            renderer.destroyForAll(instance);
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
            renderer.destroyForAll(instance);
            dialogService.detach(instance.getId());
        }
    }

    public Collection<NpcInstance> findAll() {
        return instances.values();
    }

    public Optional<NpcInstance> findByEntityId(int entityId) {
        return instances.values().stream()
            .filter(instance -> instance.getEntityId() == entityId)
            .findFirst();
    }

    private void spawnInstance(NpcInstance instance, NpcDefinition definition) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            renderer.spawnFor(player, instance, definition);
        }
        dialogService.attach(instance, definition);
    }
}
