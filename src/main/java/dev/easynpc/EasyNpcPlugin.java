package dev.easynpc;

import dev.easynpc.command.EzNpcCommand;
import dev.easynpc.dialog.DialogService;
import dev.easynpc.gui.GuiService;
import dev.easynpc.input.ChatInputService;
import dev.easynpc.listener.PlayerLifecycleListener;
import dev.easynpc.model.NpcDefinition;
import dev.easynpc.repository.NpcDefinitionRepository;
import dev.easynpc.repository.NpcInstanceRepository;
import dev.easynpc.runtime.NpcInstanceRegistry;
import dev.easynpc.runtime.NpcRenderer;
import dev.easynpc.runtime.ProtocolLibNpcRenderer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class EasyNpcPlugin extends JavaPlugin {
    private NpcDefinitionRepository definitionRepository;
    private NpcInstanceRepository instanceRepository;
    private NpcInstanceRegistry instanceRegistry;
    private NpcRenderer npcRenderer;
    private DialogService dialogService;
    private ChatInputService chatInputService;
    private GuiService guiService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        definitionRepository = new NpcDefinitionRepository(this);
        instanceRepository = new NpcInstanceRepository(this);
        npcRenderer = new ProtocolLibNpcRenderer(this, getConfig().getDouble("render-distance", 64.0));
        dialogService = new DialogService(this);
        instanceRegistry = new NpcInstanceRegistry(definitionRepository, instanceRepository, npcRenderer, dialogService);
        chatInputService = new ChatInputService(this, getConfig().getInt("chat-input-timeout-seconds", 60));
        guiService = new GuiService(this, definitionRepository, instanceRegistry, chatInputService);

        definitionRepository.loadAll();
        instanceRegistry.loadPersistedInstances();

        PluginCommand command = getCommand("eznpc");
        if (command == null) {
            getLogger().severe("plugin.yml is missing the eznpc command.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EzNpcCommand executor = new EzNpcCommand(definitionRepository, instanceRegistry, guiService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(chatInputService, this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, instanceRegistry), this);

        npcRenderer.start();
        dialogService.start();
        instanceRegistry.spawnAllOnline();
        getLogger().info("EasyNPC enabled with " + definitionRepository.findAll().size() + " NPC definitions.");
    }

    @Override
    public void onDisable() {
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
