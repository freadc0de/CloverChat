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

        // Проверяем PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIHooked = true;
            getLogger().info("[CloverChat] PlaceholderAPI найден!");
        }

        // Регистрируем наши слушатели
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);

        getLogger().info("[CloverChat] Плагин включён!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[CloverChat] Плагин выключен!");
    }

    // Если нужно публично получить конфиг
    public FileConfiguration getConfiguration() {
        return config;
    }

    // Проверка, есть ли PlaceholderAPI
    public boolean isPlaceholderAPIHooked() {
        return placeholderAPIHooked;
    }

    /**
     * Пример публичного метода, который обрабатывает &-цветы и &#RRGGBB (hex),
     * если в конфиге hex-colors: true.
     */
    public String applyColor(String text) {
        if (this.getConfiguration().getBoolean("hex-colors", true)) {
            text = Utils.applyHexColors(text); // Допустим, Utils.applyHexColors(...) у вас уже есть
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Превращаем уже «окрашенную» строку (с §-кодами) в Adventure-компонент.
     * Здесь используем LegacyComponentSerializer.legacySection().
     */
    public Component deserializeColored(String text) {
        // Можно ещё раз вызвать applyColor, если нужно,
        // но обычно мы делаем это заранее.
        // text = applyColor(text);
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}