package com.fread.CloverChat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class CloverChat extends JavaPlugin {

    private FileConfiguration config;
    private boolean placeholderAPIHooked = false;

    @Override
    public void onEnable() {
        // Создаём или подгружаем config.yml
        saveDefaultConfig();
        config = getConfig();

        // Проверка PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIHooked = true;
            getLogger().info("[CloverChat] PlaceholderAPI найден! Хук успешен.");
        } else {
            getLogger().warning("[CloverChat] PlaceholderAPI не найден. Плейсхолдеры работать не будут (если вы их используете).");
        }

        // Регистрируем слушатель чата (локальный/глобальный)
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // Регистрируем команду /m (личные сообщения)
        if (getCommand("m") != null) {
            getCommand("m").setExecutor(new CommandPrivateMessage(this));
        }

        getLogger().info("[CloverChat] Плагин успешно включён!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[CloverChat] Плагин выключен!");
    }

    public FileConfiguration getConfiguration() {
        return config;
    }

    public boolean isPlaceholderAPIHooked() {
        return placeholderAPIHooked;
    }
}
