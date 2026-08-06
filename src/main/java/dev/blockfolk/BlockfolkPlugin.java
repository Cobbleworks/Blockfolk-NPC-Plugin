package dev.blockfolk;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.blockfolk.command.BlockfolkCommand;
import dev.blockfolk.dialog.DialogService;
import dev.blockfolk.gui.CustomEventGuiService;
import dev.blockfolk.gui.GuiService;
import dev.blockfolk.gui.RouteGuiService;
import dev.blockfolk.input.ChatInputService;
import dev.blockfolk.integration.beautyquests.BeautyQuestsIntegration;
import dev.blockfolk.model.BehaviourEvent;
import dev.blockfolk.model.NpcDefinition;
import dev.blockfolk.repository.CustomEventRepository;
import dev.blockfolk.repository.LocationRepository;
import dev.blockfolk.repository.NpcDefinitionRepository;
import dev.blockfolk.repository.NpcInstanceRepository;
import dev.blockfolk.repository.RouteRepository;
import dev.blockfolk.runtime.NativeNpcNavigationService;
import dev.blockfolk.runtime.NpcBehaviourService;
import dev.blockfolk.runtime.NpcCombatService;
import dev.blockfolk.runtime.NpcInstanceRegistry;
import dev.blockfolk.runtime.NpcQuestionService;
import dev.blockfolk.runtime.NpcRenderer;
import dev.blockfolk.runtime.PaperMannequinNpcRenderer;
import dev.blockfolk.runtime.RouteMovementService;
import dev.blockfolk.util.ResolvedSkin;
import dev.blockfolk.util.SkinResolver;
import dev.blockfolk.util.SkinTextureUtil;
import dev.blockfolk.ai.AiControlService;
import dev.blockfolk.ai.OpenRouterClient;

public final class BlockfolkPlugin extends JavaPlugin {

    private NpcDefinitionRepository definitionRepository;
    private NpcInstanceRepository instanceRepository;
    private RouteRepository routeRepository;
    private LocationRepository locationRepository;
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
    private NpcQuestionService questionService;
    private SkinResolver skinResolver;
    private AiControlService aiControlService;
    private BeautyQuestsIntegration beautyQuestsIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        definitionRepository = new NpcDefinitionRepository(this);
        instanceRepository = new NpcInstanceRepository(this);
        routeRepository = new RouteRepository(this);
        locationRepository = new LocationRepository(this);
        customEventRepository = new CustomEventRepository(this);
        npcRenderer = new PaperMannequinNpcRenderer(this);
        navigationService = new NativeNpcNavigationService(this);
        dialogService = new DialogService(this);
        instanceRegistry = new NpcInstanceRegistry(
                this,
                definitionRepository,
                instanceRepository,
                npcRenderer,
                navigationService,
                dialogService
        );
        dialogService.setLocationProvider(instanceRegistry::currentLocation);
        chatInputService = new ChatInputService(this, getConfig().getInt("chat-input-timeout-seconds", 60));
        skinResolver = new SkinResolver(
                getName() + "/" + getPluginMeta().getVersion(),
                getConfig().getString("mineskin-api-key", "")
        );
        routeGuiService = new RouteGuiService(
                this,
                routeRepository,
                locationRepository,
                definitionRepository,
                instanceRegistry,
                chatInputService,
                this::openMainGui
        );
        customEventGuiService = new CustomEventGuiService(
                this, customEventRepository, definitionRepository, chatInputService, this::openMainGui);
        guiService = new GuiService(
                this,
                definitionRepository,
                routeRepository,
                instanceRegistry,
                chatInputService,
                skinResolver,
                routeGuiService::openRoutes,
                routeGuiService::createRoute,
                customEventRepository,
                customEventGuiService::open,
                customEventGuiService::createEvent,
                locationRepository
        );
        routeGuiService.setWaypointActionOpener(guiService::openWaypointActions);
        combatService = new NpcCombatService(this, definitionRepository, instanceRegistry, navigationService);
        questionService = new NpcQuestionService(this, instanceRegistry, chatInputService,
                getConfig().getInt("question-timeout-seconds", 30));
        chatInputService.setBeforeRequest(questionService::cancelForAdminInput);
        behaviourService = new NpcBehaviourService(
                this,
                definitionRepository,
                instanceRegistry,
                dialogService,
                questionService,
                getConfig().getInt("proximity-transition-cooldown-seconds", 3)
        );
        behaviourService.setCombatService(combatService);
        routeGuiService.setBehaviourService(behaviourService);
        OpenRouterClient openRouterClient = new OpenRouterClient(
                getConfig().getString("openrouter.endpoint", "https://openrouter.ai/api/v1/chat/completions"),
                getConfig().getString("openrouter.api-key", ""),
                getConfig().getString("openrouter.model", ""),
                getConfig().getInt("openrouter.timeout-seconds", 12),
                getConfig().getInt("openrouter.max-tokens", 1600));
        aiControlService = new AiControlService(this, definitionRepository, instanceRegistry, combatService,
                locationRepository,
                openRouterClient, getConfig().getInt("ai-control.invocation-cooldown-seconds", 2));
        behaviourService.setAiControlService(aiControlService);
        aiControlService.setRouteState(behaviourService::hasRoute);
        guiService.setAiControlService(aiControlService);
        combatService.setBehaviourService(behaviourService);
        guiService.setBehaviourService(behaviourService);
        instanceRegistry.setSpawnListener((instance, definition) -> {
            behaviourService.forget(instance);
            behaviourService.trigger(BehaviourEvent.SPAWN, instance, null);
        });
        instanceRegistry.setRemovalListener(behaviourService::forget);
        routeMovementService = new RouteMovementService(
                this,
                definitionRepository,
                routeRepository,
                instanceRegistry,
                combatService,
                behaviourService
        );
        instanceRegistry.setRelocationListener(routeMovementService::resetProgress);

        routeRepository.loadAll();
        locationRepository.loadAll();
        customEventRepository.loadAll();
        definitionRepository.loadAll();
        instanceRegistry.loadPersistedInstances();

        if (getServer().getPluginManager().isPluginEnabled("BeautyQuests")) {
            try {
                beautyQuestsIntegration = new BeautyQuestsIntegration(
                        this, definitionRepository, instanceRegistry, behaviourService);
                beautyQuestsIntegration.register();
                instanceRegistry.setRemovalListener(instance -> {
                    behaviourService.forget(instance);
                    beautyQuestsIntegration.npcRemoved(instance);
                });
            } catch (LinkageError | RuntimeException exception) {
                beautyQuestsIntegration = null;
                getLogger().log(Level.WARNING,
                        "BeautyQuests was detected, but its integration could not be enabled.", exception);
            }
        }

        PluginCommand command = getCommand("blockfolk");
        if (command == null) {
            getLogger().severe("plugin.yml is missing the blockfolk command.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BlockfolkCommand executor = new BlockfolkCommand(
                definitionRepository, instanceRegistry, guiService, routeGuiService,
                customEventGuiService, customEventRepository, behaviourService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(routeGuiService, this);
        getServer().getPluginManager().registerEvents(customEventGuiService, this);
        getServer().getPluginManager().registerEvents(chatInputService, this);
        getServer().getPluginManager().registerEvents(questionService, this);
        getServer().getPluginManager().registerEvents(combatService, this);
        getServer().getPluginManager().registerEvents(behaviourService, this);
        getServer().getPluginManager().registerEvents(instanceRegistry, this);

        npcRenderer.start();
        dialogService.start();
        routeGuiService.start();
        instanceRegistry.spawnAll();
        resolveStoredExternalSkins();
        combatService.start();
        questionService.start();
        behaviourService.start();
        routeMovementService.start();
        getLogger().info(() -> "Blockfolk enabled with " + definitionRepository.findAll().size() + " NPC definitions.");
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
        if (questionService != null) {
            questionService.stop();
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
        if (definitionRepository != null) definitionRepository.flush();
        if (instanceRepository != null) instanceRepository.flush();
        if (routeRepository != null) routeRepository.flush();
        if (locationRepository != null) locationRepository.flush();
        if (customEventRepository != null) customEventRepository.flush();
        if (dialogService != null) {
            dialogService.stop();
        }
        if (npcRenderer != null) {
            npcRenderer.stop();
        }
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
            // Preserve the configured source URL; only the resolved texture data
            // needs to come from MineSkin.
            definition.setResolvedSkin(requestedUrl, resolved.textureValue(), resolved.textureSignature());
            definitionRepository.save(definition);
            instanceRegistry.refreshDefinition(definition);
            getLogger().info(() -> "Processed the external skin for NPC " + definitionKey + ".");
        });
    }
}
