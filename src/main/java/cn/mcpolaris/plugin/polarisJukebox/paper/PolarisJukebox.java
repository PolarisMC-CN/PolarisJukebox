package cn.mcpolaris.plugin.polarisJukebox.paper;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class PolarisJukebox extends JavaPlugin {
    @Getter
    private static PolarisJukebox instance;

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        ConfigManager.save();
        getLogger().info("Goodbye");
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        ConfigManager.reloadConfig();
        getServer().getPluginManager().registerEvents(new EventListener(), getInstance());
        getCommand("jukebox").setExecutor(new CommandManager());
        getLogger().info("Loaded");
    }
}
