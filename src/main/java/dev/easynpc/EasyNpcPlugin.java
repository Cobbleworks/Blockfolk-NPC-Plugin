package dev.easynpc;

import dev.easynpc.command.EzNpcCommand;
import dev.easynpc.dialog.DialogService;
import dev.easynpc.gui.GuiService;
import dev.easynpc.gui.RouteGuiService;
import dev.easynpc.input.ChatInputService;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.repository.NpcDefinitionRepository;
import dev.easynpc.repository.NpcInstanceRepository;
import dev.easynpc.repository.RouteRepository;
import dev.easynpc.runtime.NpcInstanceRegistry;
import dev.easynpc.runtime.NpcCombatService;
import dev.easynpc.runtime.NativeNpcNavigationService;
import dev.easynpc.runtime.NpcRenderer;
import dev.easynpc.runtime.PaperMannequinNpcRenderer;
import dev.easynpc.runtime.RouteMovementService;
import dev.easynpc.util.ResolvedSkin;
import dev.easynpc.util.SkinResolver;
import dev.easynpc.util.SkinTextureUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class EasyNpcPlugin extends JavaPlugin {
    private NpcDefinitionRepository definitionRepository;
    private NpcInstanceRepository instanceRepository;
    private RouteRepository routeRepository;
    private NpcInstanceRegistry instanceRegistry;
    private NpcRenderer npcRenderer;
    private NativeNpcNavigationService navigationService;
    private DialogService dialogService;
    private ChatInputService chatInputService;
    private GuiService guiService;
    private RouteGuiService routeGuiService;
    private RouteMovementService routeMovementService;
    private NpcCombatService combatService;
    private SkinResolver skinResolver;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        definitionRepository = new NpcDefinitionRepository(this);
        instanceRepository = new NpcInstanceRepository(this);
        routeRepository = new RouteRepository(this);
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
        guiService = new GuiService(
            this,
            definitionRepository,
            routeRepository,
            instanceRegistry,
            chatInputService,
            dialogService,
            skinResolver,
            routeGuiService::openRoutes
        );
        combatService = new NpcCombatService(this, definitionRepository, instanceRegistry, navigationService);
        routeMovementService = new RouteMovementService(
            this,
            definitionRepository,
            routeRepository,
            instanceRegistry,
            combatService
        );

        routeRepository.loadAll();
        definitionRepository.loadAll();
        instanceRegistry.loadPersistedInstances();

        PluginCommand command = getCommand("eznpc");
        if (command == null) {
            getLogger().severe("plugin.yml is missing the eznpc command.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EzNpcCommand executor = new EzNpcCommand(definitionRepository, instanceRegistry, guiService, routeGuiService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(routeGuiService, this);
        getServer().getPluginManager().registerEvents(chatInputService, this);
        getServer().getPluginManager().registerEvents(combatService, this);

        npcRenderer.start();
        dialogService.start();
        routeGuiService.start();
        instanceRegistry.spawnAll();
        resolveStoredExternalSkins();
        combatService.start();
        routeMovementService.start();
        getLogger().info("EasyNPC enabled with " + definitionRepository.findAll().size() + " NPC definitions.");
    }

    @Override
    public void onDisable() {
        if (combatService != null) {
            combatService.stop();
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
            skinResolver.resolve(skinUrl).whenComplete((resolved, error) ->
                Bukkit.getScheduler().runTask(this,
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
