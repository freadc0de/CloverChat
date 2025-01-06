package com.fread.CloverChat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

public class CommandPrivateMessage implements CommandExecutor {

    private final CloverChat plugin;

    public CommandPrivateMessage(CloverChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        boolean pmEnabled = plugin.getConfiguration().getBoolean("private-chat.enabled", true);
        if (!pmEnabled) {
            sender.sendMessage("Личные сообщения отключены на сервере.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Только игроки могут использовать эту команду!");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("Использование: /m <ник_игрока> <сообщение>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage("Игрок " + targetName + " не в сети.");
            return true;
        }

        // Собираем сообщение
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String message = sb.toString().trim();

        // Достаем форматы
        String formatSender = plugin.getConfiguration().getString(
                "private-chat.format-sender",
                "&#55FFFF[-> %target_name%] %message%"
        );
        String formatReceiver = plugin.getConfiguration().getString(
                "private-chat.format-receiver",
                "&#55FFFF[%player_name% -> You] %message%"
        );

        // Заменяем плейсхолдеры
        formatSender = formatSender.replace("%target_name%", target.getName())
                .replace("%player_name%", player.getName())
                .replace("%message%", message);

        formatReceiver = formatReceiver.replace("%player_name%", player.getName())
                .replace("%message%", message);

        // PlaceholderAPI (если нужно)
        if (plugin.isPlaceholderAPIHooked()) {
            formatSender = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatSender);
            formatReceiver = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(target, formatReceiver);
        }

        // Переводим цвета (hex или &)
        formatSender = plugin.getConfiguration().getBoolean("hex-colors", true)
                ? Utils.applyHexColors(formatSender)
                : org.bukkit.ChatColor.translateAlternateColorCodes('&', formatSender);

        formatReceiver = plugin.getConfiguration().getBoolean("hex-colors", true)
                ? Utils.applyHexColors(formatReceiver)
                : org.bukkit.ChatColor.translateAlternateColorCodes('&', formatReceiver);

        // Превращаем в Adventure-компоненты
        Component senderComp = LegacyComponentSerializer.legacySection().deserialize(formatSender);
        Component receiverComp = LegacyComponentSerializer.legacySection().deserialize(formatReceiver);

        // Отправляем
        player.sendMessage(senderComp);
        target.sendMessage(receiverComp);

        return true;
    }
}
