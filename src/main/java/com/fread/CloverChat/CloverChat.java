package com.fread.CloverChat;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class CloverChat extends JavaPlugin {

    private FileConfiguration config;
    private boolean placeholderAPIHooked = false;

    @Override
    public void onEnable() {
        // Создаём или загружаем config.yml
        saveDefaultConfig();
        config = getConfig();

        // Проверяем PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIHooked = true;
            getLogger().info("[CloverChat] PlaceholderAPI найден!");
        }

        // Регистрируем слушатели
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new CommandCooldownListener(this), this);

        // Регистрируем команду /m (личные сообщения)
        if (getCommand("m") != null) {
            getCommand("m").setExecutor(new CommandPrivateMessage(this));
        }

        // Регистрируем команду /cloverchatreload
        if (getCommand("cloverchatreload") != null) {
            getCommand("cloverchatreload").setExecutor(new CommandReloadCloverChat(this));
        }

        getLogger().info("[CloverChat] Плагин включён!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[CloverChat] Плагин выключен!");
    }

    // Этот метод используем, чтобы получать конфиг в других классах
    public FileConfiguration getConfiguration() {
        return config;
    }

    // Если нужно обновлять config после reloadConfig()
    public void setConfig(FileConfiguration newConfig) {
        this.config = newConfig;
    }

    public boolean isPlaceholderAPIHooked() {
        return placeholderAPIHooked;
    }
}
