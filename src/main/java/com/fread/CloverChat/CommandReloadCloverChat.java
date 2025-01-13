package com.fread.CloverChat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.List;

public class CommandReloadCloverChat implements CommandExecutor {

    private final CloverChat plugin;

    public CommandReloadCloverChat(CloverChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Например, проверяем право cloverchat.command.reload
        if (!sender.hasPermission("cloverchat.command.reload")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на команду /" + label);
            return true;
        }

        // Перезагружаем конфиг
        plugin.reloadConfig();
        plugin.setConfig(plugin.getConfig()); // <- теперь метод setConfig есть

        // Возьмём список строк из system-messages.reload-success (пример)
        List<String> reloadMsg = plugin.getConfiguration().getStringList("system-messages.reload-success");
        if (reloadMsg.isEmpty()) {
            reloadMsg = Collections.singletonList("&aCloverChat config reloaded!");
        }

        // Окрашиваем и отправляем
        for (String line : reloadMsg) {
            line = plugin.applyColor(line);
            Component comp = plugin.deserializeColored(line);
            sender.sendMessage(comp);
        }

        return true;
    }
}