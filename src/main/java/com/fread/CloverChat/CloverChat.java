package com.fread.CloverChat;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class CloverChat extends JavaPlugin {

    // Локальная переменная для хранения конфига
    private FileConfiguration config;

    // Флаг для PlaceholderAPI
    private boolean placeholderAPIHooked = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIHooked = true;
            getLogger().info("[CloverChat] PlaceholderAPI найден!");
        }

        // ВАЖНО
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandCooldownListener(this), this);

        if (getCommand("m") != null) {
            getCommand("m").setExecutor(new CommandPrivateMessage(this));
        }

        if (getCommand("cloverchatreload") != null) {
            getCommand("cloverchatreload").setExecutor(new CommandReloadCloverChat(this));
        }

        getLogger().info("[CloverChat] Плагин включён!");
    }

    // Этот метод нужен, если где-то в коде вы вручную хотите заменять config
    public void setConfig(FileConfiguration newConfig) {
        this.config = newConfig;
    }

    // Вот тот самый метод, который вы потом будете вызывать
    public FileConfiguration getConfiguration() {
        return config;
    }

    public boolean isPlaceholderAPIHooked() {
        return placeholderAPIHooked;
    }
}
