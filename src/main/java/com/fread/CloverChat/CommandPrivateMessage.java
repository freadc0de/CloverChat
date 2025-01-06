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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandPrivateMessage implements CommandExecutor {

    private final CloverChat plugin;

    // Храним время последней отправки ЛС (в мс) для каждого игрока
    private final Map<UUID, Long> lastPmTime = new HashMap<>();

    public CommandPrivateMessage(CloverChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Проверяем, включены ли личные сообщения
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

        // Проверка аргументов
        if (args.length < 2) {
            player.sendMessage("Использование: /m <ник_игрока> <сообщение>");
            return true;
        }

        // --- Начало проверки кулдауна ---
        long cooldownSeconds = plugin.getConfiguration().getLong("private-chat.cooldown-seconds", 5);
        long cooldownMillis = cooldownSeconds * 1000L;

        long now = System.currentTimeMillis();
        if (lastPmTime.containsKey(player.getUniqueId())) {
            long lastTime = lastPmTime.get(player.getUniqueId());
            long diff = now - lastTime;

            if (diff < cooldownMillis) {
                // Кулдаун ещё не вышел
                long remain = (cooldownMillis - diff) / 1000; // оставшиеся секунды

                // Выводим сообщение из "cooldown-message" (список строк)
                List<String> cooldownMsg = plugin.getConfiguration().getStringList("private-chat.cooldown-message");
                if (!cooldownMsg.isEmpty()) {
                    for (String line : cooldownMsg) {
                        // Подставляем %remain%
                        line = line.replace("%remain%", String.valueOf(remain));

                        // Обрабатываем цвета
                        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
                            line = Utils.applyHexColors(line);
                        } else {
                            line = org.bukkit.ChatColor.translateAlternateColorCodes('&', line);
                        }

                        // Отправляем строку
                        player.sendMessage(line);
                    }
                } else {
                    // Если список пуст, отправим дефолт
                    player.sendMessage("Подождите ещё " + remain + " секунд перед отправкой следующего ЛС.");
                }
                return true; // Прерываем команду
            }
        }
        // Обновляем время последней отправки
        lastPmTime.put(player.getUniqueId(), now);
        // --- Конец проверки кулдауна ---

        // Находим получателя
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage("Игрок " + targetName + " не в сети.");
            return true;
        }

        // Собираем текст сообщения
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String message = sb.toString().trim();

        // Форматы
        String formatSender = plugin.getConfiguration().getString(
                "private-chat.format-sender",
                "&#55FFFF[-> %target_name%] %message%"
        );
        String formatReceiver = plugin.getConfiguration().getString(
                "private-chat.format-receiver",
                "&#55FFFF[%player_name% -> You] %message%"
        );

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

        // Переводим цвета
        boolean useHex = plugin.getConfiguration().getBoolean("hex-colors", true);
        if (useHex) {
            formatSender = Utils.applyHexColors(formatSender);
            formatReceiver = Utils.applyHexColors(formatReceiver);
        } else {
            formatSender = org.bukkit.ChatColor.translateAlternateColorCodes('&', formatSender);
            formatReceiver = org.bukkit.ChatColor.translateAlternateColorCodes('&', formatReceiver);
        }

        // Adventure-компоненты
        Component senderComp = LegacyComponentSerializer.legacySection().deserialize(formatSender);
        Component receiverComp = LegacyComponentSerializer.legacySection().deserialize(formatReceiver);

        // Отправляем
        player.sendMessage(senderComp);
        target.sendMessage(receiverComp);

        // --- Добавляем звук уведомления получателю ---
        String soundKey = plugin.getConfiguration().getString("private-chat.notification-sound", "minecraft:block.note_block.pling");
        target.playSound(
                Sound.sound(
                        Key.key(soundKey),
                        Sound.Source.PLAYER, // Или MASTER, AMBIENT, etc.
                        1.0f,   // громкость
                        1.0f    // питч
                )
        );

        return true;
    }
}