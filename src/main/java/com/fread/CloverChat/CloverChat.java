package com.fread.CloverChat;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

public class CloverChat extends JavaPlugin {

    private FileConfiguration config;
    private boolean placeholderAPIHooked = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIHooked = true;
            getLogger().info("[CloverChat] PlaceholderAPI найден!");
        }

        // Регистрируем слушатели и команды
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);

        // Если в plugin.yml есть "cloverchatreload" команда:
        if (getCommand("cloverchatreload") != null) {
            getCommand("cloverchatreload").setExecutor(new CommandReloadCloverChat(this));
        }

        if (getCommand("m") != null) {
            getCommand("m").setExecutor(new CommandPrivateMessage(this));
        }

        getLogger().info("[CloverChat] Плагин включён!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[CloverChat] Плагин выключен!");
    }

    // Если хотим публично вернуть config
    public FileConfiguration getConfiguration() {
        return config;
    }

    // Если нужно «принудительно» перезаписать config (для reload команды):
    public void setConfig(FileConfiguration newConfig) {
        this.config = newConfig;
    }

    public boolean isPlaceholderAPIHooked() {
        return placeholderAPIHooked;
    }

    // Методы для цвета/десериализации:
    public String applyColor(String text) {
        if (getConfiguration().getBoolean("hex-colors", true)) {
            text = Utils.applyHexColors(text);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public Component deserializeColored(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}
