package com.fread.CloverChat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandPrivateMessage implements CommandExecutor {

    private final CloverChat plugin;
    private final Map<UUID, Long> lastPmTime = new HashMap<>();

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
            List<String> usageMsg = plugin.getConfiguration().getStringList("private-chat.usage-message");
            if (usageMsg.isEmpty()) {
                usageMsg = Arrays.asList("&eИспользование: /m <ник_игрока> <сообщение>");
            }
            for (String line : usageMsg) {
                line = applyColor(line);
                player.sendMessage(line);
            }
            return true;
        }

        // Проверка кулдауна
        long cooldownSeconds = plugin.getConfiguration().getLong("private-chat.cooldown-seconds", 5);
        long cooldownMillis = cooldownSeconds * 1000L;
        long now = System.currentTimeMillis();

        if (lastPmTime.containsKey(player.getUniqueId())) {
            long lastTime = lastPmTime.get(player.getUniqueId());
            long diff = now - lastTime;
            if (diff < cooldownMillis) {
                long remain = (cooldownMillis - diff) / 1000;
                List<String> cooldownMsg = plugin.getConfiguration().getStringList("private-chat.cooldown-message");
                if (!cooldownMsg.isEmpty()) {
                    for (String line : cooldownMsg) {
                        line = line.replace("%remain%", String.valueOf(remain));
                        line = applyColor(line);
                        player.sendMessage(line);
                    }
                } else {
                    player.sendMessage("Подождите ещё " + remain + " секунд перед отправкой следующего ЛС.");
                }
                return true;
            }
        }
        lastPmTime.put(player.getUniqueId(), now);

        // Ищем получателя
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            List<String> offlineMsg = plugin.getConfiguration().getStringList("private-chat.offline-player-message");
            if (offlineMsg.isEmpty()) {
                offlineMsg = Arrays.asList("&cИгрок %target_name% не в сети.");
            }
            for (String line : offlineMsg) {
                line = line.replace("%target_name%", targetName);
                line = applyColor(line);
                player.sendMessage(line);
            }
            return true;
        }

        // Собираем текст сообщения
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String message = sb.toString().trim();

        // Форматы
        String formatSender = plugin.getConfiguration().getString("private-chat.format-sender",
                "&#55FFFF[-> %target_name%] %message%");
        String formatReceiver = plugin.getConfiguration().getString("private-chat.format-receiver",
                "&#55FFFF[%player_name% -> You] %message%");

        formatSender = formatSender.replace("%target_name%", target.getName())
                .replace("%player_name%", player.getName())
                .replace("%message%", message);

        formatReceiver = formatReceiver.replace("%player_name%", player.getName())
                .replace("%message%", message);

        if (plugin.isPlaceholderAPIHooked()) {
            formatSender = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatSender);
            formatReceiver = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(target, formatReceiver);
        }

        // Цвета
        boolean useHex = plugin.getConfiguration().getBoolean("hex-colors", true);
        if (useHex) {
            formatSender = Utils.applyHexColors(formatSender);
            formatReceiver = Utils.applyHexColors(formatReceiver);
        } else {
            formatSender = org.bukkit.ChatColor.translateAlternateColorCodes('&', formatSender);
            formatReceiver = org.bukkit.ChatColor.translateAlternateColorCodes('&', formatReceiver);
        }

        // Adventure
        Component senderComp = LegacyComponentSerializer.legacySection().deserialize(formatSender);
        Component receiverComp = LegacyComponentSerializer.legacySection().deserialize(formatReceiver);

        // Отправка
        player.sendMessage(senderComp);
        target.sendMessage(receiverComp);

        // Звук получателю
        String soundKey = plugin.getConfiguration().getString("private-chat.notification-sound",
                "minecraft:block.note_block.pling");
        target.playSound(
                Sound.sound(
                        Key.key(soundKey),
                        Sound.Source.PLAYER,
                        1.0f,
                        1.0f
                )
        );

        return true;
    }

    private String applyColor(String text) {
        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
            return Utils.applyHexColors(text);
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
