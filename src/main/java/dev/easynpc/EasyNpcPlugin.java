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
import dev.easynpc.runtime.NativeNpcNavigationService;
import dev.easynpc.runtime.NpcRenderer;
import dev.easynpc.runtime.PaperMannequinNpcRenderer;
import dev.easynpc.runtime.RouteMovementService;
import org.bukkit.command.PluginCommand;
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
        guiService = new GuiService(definitionRepository, routeRepository, instanceRegistry, chatInputService, dialogService);
        routeGuiService = new RouteGuiService(this, routeRepository, definitionRepository, instanceRegistry, chatInputService);
        routeMovementService = new RouteMovementService(this, definitionRepository, routeRepository, instanceRegistry);

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

        npcRenderer.start();
        dialogService.start();
        routeGuiService.start();
        instanceRegistry.spawnAll();
        routeMovementService.start();
        getLogger().info("EasyNPC enabled with " + definitionRepository.findAll().size() + " NPC definitions.");
    }

    @Override
    public void onDisable() {
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
}
