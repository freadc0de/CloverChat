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

    // Храним время последней отправки ЛС (кулдаун)
    private final Map<UUID, Long> lastPmTime = new HashMap<>();

    public CommandPrivateMessage(CloverChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1) Проверяем, включены ли личные сообщения
        boolean pmEnabled = plugin.getConfiguration().getBoolean("private-chat.enabled", true);
        if (!pmEnabled) {
            sender.sendMessage("Личные сообщения отключены на сервере.");
            return true;
        }

        // 2) Проверяем, что отправитель — игрок
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только игроки могут использовать эту команду!");
            return true;
        }
        Player player = (Player) sender;

        // 3) Проверяем количество аргументов
        if (args.length < 2) {
            // Выводим кастомное usage из конфига
            List<String> usageMsg = plugin.getConfiguration().getStringList("private-chat.usage-message");
            if (usageMsg.isEmpty()) {
                usageMsg = Arrays.asList("&eИспользование: /m <ник_игрока> <сообщение>");
            }
            for (String line : usageMsg) {
                // Пропускаем через applyColor и превращаем в Component
                line = plugin.applyColor(line);
                player.sendMessage(plugin.deserializeColored(line));
            }
            return true; // Возвращаем true, чтобы Bukkit НЕ дублировал usage
        }

        // 4) Проверка кулдауна
        long cooldownSeconds = plugin.getConfiguration().getLong("private-chat.cooldown-seconds", 5);
        long cooldownMillis = cooldownSeconds * 1000;
        long now = System.currentTimeMillis();

        if (lastPmTime.containsKey(player.getUniqueId())) {
            long lastTime = lastPmTime.get(player.getUniqueId());
            long diff = now - lastTime;
            if (diff < cooldownMillis) {
                // Кулдаун ещё не вышел
                long remain = (cooldownMillis - diff) / 1000;
                List<String> cooldownMsg = plugin.getConfiguration().getStringList("private-chat.cooldown-message");
                if (!cooldownMsg.isEmpty()) {
                    for (String line : cooldownMsg) {
                        line = line.replace("%remain%", String.valueOf(remain));
                        line = plugin.applyColor(line);
                        player.sendMessage(plugin.deserializeColored(line));
                    }
                } else {
                    // Если блок в конфиге пуст
                    player.sendMessage("Подождите ещё " + remain + " секунд перед отправкой следующего ЛС.");
                }
                return true;
            }
        }
        lastPmTime.put(player.getUniqueId(), now);

        // 5) Проверяем, что адресат онлайн
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            // Сообщение, что игрок офлайн
            List<String> offlineMsg = plugin.getConfiguration().getStringList("private-chat.offline-player-message");
            if (offlineMsg.isEmpty()) {
                offlineMsg = Arrays.asList("&cИгрок %target_name% не в сети.");
            }
            for (String line : offlineMsg) {
                line = line.replace("%target_name%", targetName);
                line = plugin.applyColor(line);
                player.sendMessage(plugin.deserializeColored(line));
            }
            return true;
        }

        // 6) Собираем сообщение (остальные аргументы)
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String message = sb.toString().trim();

        // 7) Форматы
        String formatSender = plugin.getConfiguration().getString("private-chat.format-sender",
                "&#55FFFF[-> %target_name%] %message%");
        String formatReceiver = plugin.getConfiguration().getString("private-chat.format-receiver",
                "&#55FFFF[%player_name% -> You] %message%");

        formatSender = formatSender.replace("%target_name%", target.getName())
                .replace("%player_name%", player.getName())
                .replace("%message%", message);

        formatReceiver = formatReceiver.replace("%player_name%", player.getName())
                .replace("%message%", message);

        // Если PlaceholderAPI установлен
        if (plugin.isPlaceholderAPIHooked()) {
            formatSender = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatSender);
            formatReceiver = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(target, formatReceiver);
        }

        // 8) Переводим & / &#RRGGBB в цвет, превращаем в компонент
        formatSender = plugin.applyColor(formatSender);
        Component senderComp = plugin.deserializeColored(formatSender);

        formatReceiver = plugin.applyColor(formatReceiver);
        Component receiverComp = plugin.deserializeColored(formatReceiver);

        // 9) Отправляем
        player.sendMessage(senderComp);
        target.sendMessage(receiverComp);

        // 10) Звук получателю
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

        // 11) Возвращаем true, чтобы не выводился дефолтный usage
        return true;
    }
}