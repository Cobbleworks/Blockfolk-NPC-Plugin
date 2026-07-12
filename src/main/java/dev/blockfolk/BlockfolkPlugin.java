package dev.blockfolk;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.command.BlockfolkCommand;
import dev.blockfolk.dialog.DialogService;
import dev.blockfolk.gui.GuiService;
import dev.blockfolk.gui.CustomEventGuiService;
import dev.blockfolk.gui.RouteGuiService;
import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.CustomEventRepository;
import dev.blockfolk.repository.NpcInstanceRepository;
import dev.blockfolk.repository.RouteRepository;
import dev.blockfolk.runtime.NativeNpcNavigationService;
import dev.blockfolk.runtime.NpcBehaviourService;
import dev.blockfolk.runtime.NpcCombatService;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.runtime.NpcRenderer;
import dev.blockfolk.runtime.PaperMannequinNpcRenderer;
import dev.blockfolk.runtime.RouteMovementService;
import dev.blockfolk.util.ResolvedSkin;
import dev.blockfolk.util.SkinResolver;
import dev.blockfolk.util.SkinTextureUtil;

public final class BlockfolkPlugin extends JavaPlugin {

    private NpcDefinitionRepository definitionRepository;
    private NpcInstanceRepository instanceRepository;
    private RouteRepository routeRepository;
    private CustomEventRepository customEventRepository;
    private NpcInstanceRegistry instanceRegistry;
    private NpcRenderer npcRenderer;
    private NativeNpcNavigationService navigationService;
    private DialogService dialogService;
    private ChatInputService chatInputService;
    private GuiService guiService;
    private RouteGuiService routeGuiService;
    private CustomEventGuiService customEventGuiService;
    private RouteMovementService routeMovementService;
    private NpcCombatService combatService;
    private NpcBehaviourService behaviourService;
    private SkinResolver skinResolver;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        definitionRepository = new NpcDefinitionRepository(this);
        instanceRepository = new NpcInstanceRepository(this);
        routeRepository = new RouteRepository(this);
        customEventRepository = new CustomEventRepository(this);
        npcRenderer = new PaperMannequinNpcRenderer(this);
        navigationService = new NativeNpcNavigationService(this);
        dialogService = new DialogService(this);
        instanceRegistry = new NpcInstanceRegistry(
                definitionRepository,
                instanceRepository,
                npcRenderer,
                navigationService,
                dialogService
        );
        chatInputService = new ChatInputService(this, getConfig().getInt("chat-input-timeout-seconds", 60));
        skinResolver = new SkinResolver(
                getName() + "/" + getPluginMeta().getVersion(),
                getConfig().getString("mineskin-api-key", "")
        );
        routeGuiService = new RouteGuiService(
                this,
                routeRepository,
                definitionRepository,
                instanceRegistry,
                chatInputService,
                this::openMainGui
        );
        customEventGuiService = new CustomEventGuiService(
                customEventRepository, definitionRepository, chatInputService, this::openMainGui);
        guiService = new GuiService(
                this,
                definitionRepository,
                routeRepository,
                instanceRegistry,
                chatInputService,
                skinResolver,
                routeGuiService::openRoutes,
                customEventRepository,
                customEventGuiService::open
        );
        routeGuiService.setWaypointActionOpener(guiService::openWaypointActions);
        combatService = new NpcCombatService(this, definitionRepository, instanceRegistry, navigationService);
        behaviourService = new NpcBehaviourService(
                this,
                definitionRepository,
                instanceRegistry,
                dialogService,
                getConfig().getInt("proximity-transition-cooldown-seconds", 3)
        );
        behaviourService.setCombatService(combatService);
        combatService.setBehaviourService(behaviourService);
        guiService.setBehaviourService(behaviourService);
        instanceRegistry.setSpawnListener((instance, definition) -> {
            behaviourService.forget(instance);
            behaviourService.trigger(BehaviourEvent.SPAWN, instance, null);
        });
        routeMovementService = new RouteMovementService(
                this,
                definitionRepository,
                routeRepository,
                instanceRegistry,
                combatService,
                behaviourService
        );

        routeRepository.loadAll();
        customEventRepository.loadAll();
        definitionRepository.loadAll();
        instanceRegistry.loadPersistedInstances();

        PluginCommand command = getCommand("blockfolk");
        if (command == null) {
            getLogger().severe("plugin.yml is missing the blockfolk command.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BlockfolkCommand executor = new BlockfolkCommand(
                definitionRepository, instanceRegistry, guiService, routeGuiService,
                customEventGuiService, customEventRepository, behaviourService, this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(routeGuiService, this);
        getServer().getPluginManager().registerEvents(customEventGuiService, this);
        getServer().getPluginManager().registerEvents(chatInputService, this);
        getServer().getPluginManager().registerEvents(combatService, this);
        getServer().getPluginManager().registerEvents(behaviourService, this);

        npcRenderer.start();
        dialogService.start();
        routeGuiService.start();
        instanceRegistry.spawnAll();
        resolveStoredExternalSkins();
        combatService.start();
        behaviourService.start();
        routeMovementService.start();
        getLogger().info("Blockfolk enabled with " + definitionRepository.findAll().size() + " NPC definitions.");
    }

    @Override
    public void onDisable() {
        if (guiService != null) {
            guiService.stop();
        }
        if (combatService != null) {
            combatService.stop();
        }
        if (behaviourService != null) {
            behaviourService.stop();
        }
        if (routeGuiService != null) {
            routeGuiService.stop();
        }
        if (routeMovementService != null) {
            routeMovementService.stop();
        }
        if (chatInputService != null) {
            chatInputService.cancelAll();
        }
        if (instanceRegistry != null) {
            try {
                instanceRegistry.saveAndDespawnAll();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Failed to save NPC instances during shutdown.", exception);
            }
        }
        if (dialogService != null) {
            dialogService.stop();
        }
        if (npcRenderer != null) {
            npcRenderer.stop();
        }
    }

    public NpcDefinition createDefinition(String name) {
        NpcDefinition definition = NpcDefinition.create(name);
        definitionRepository.save(definition);
        return definition;
    }

    private void openMainGui(Player player) {
        guiService.openMain(player);
    }

    private void resolveStoredExternalSkins() {
        for (NpcDefinition definition : definitionRepository.findAll()) {
            String skinUrl = definition.getSkinUrl();
            if (skinUrl == null || definition.getSkinTextureValue() != null
                    || SkinTextureUtil.isMinecraftTextureUrl(skinUrl)) {
                continue;
            }
            skinResolver.resolve(skinUrl).whenComplete((resolved, error)
                    -> Bukkit.getScheduler().runTask(this,
                            () -> finishStoredSkinResolution(definition.getKey(), skinUrl, resolved, error))
            );
        }
    }

    private void finishStoredSkinResolution(
            String definitionKey,
            String requestedUrl,
            ResolvedSkin resolved,
            Throwable error
    ) {
        if (error != null) {
            getLogger().log(Level.WARNING, "Could not process the stored skin for NPC " + definitionKey, error);
            return;
        }
        definitionRepository.find(definitionKey).ifPresent(definition -> {
            if (!requestedUrl.equals(definition.getSkinUrl())) {
                return;
            }
            definition.setResolvedSkin(resolved.url(), resolved.textureValue(), resolved.textureSignature());
            definitionRepository.save(definition);
            instanceRegistry.refreshDefinition(definition);
            getLogger().info("Processed the external skin for NPC " + definitionKey + ".");
        });
    }
}
