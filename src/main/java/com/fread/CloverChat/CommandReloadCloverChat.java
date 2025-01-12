package com.fread.CloverChat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class CommandReloadCloverChat implements CommandExecutor {

    private final CloverChat plugin;

    public CommandReloadCloverChat(CloverChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Проверка права
        if (!sender.hasPermission("cloverchat.command.reload")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на /" + label + "!");
            return true;
        }

        // Перезагружаем конфиг
        plugin.reloadConfig();
        plugin.setConfig(plugin.getConfig());

        // Считываем список строк system-messages.reload-success
        List<String> reloadMsgList = plugin.getConfiguration()
                .getStringList("system-messages.reload-success");

        if (reloadMsgList.isEmpty()) {
            reloadMsgList = Arrays.asList("&aCloverChat config reloaded (default message).");
        }

        for (String line : reloadMsgList) {
            line = applyColor(line);
            sender.sendMessage(line);
        }

        return true;
    }

    private String applyColor(String text) {
        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
            return Utils.applyHexColors(text);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
