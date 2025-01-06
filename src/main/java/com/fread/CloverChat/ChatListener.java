package com.fread.CloverChat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final CloverChat plugin;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]{3,16})");

    public ChatListener(CloverChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();

        // 1. Проверяем право на чат: cloverchat.chat.use
        if (!sender.hasPermission("cloverchat.chat.use")) {
            event.setCancelled(true);

            // Выводим сообщение об ошибке из no-chat-permission-message
            List<String> noPermMsg = plugin.getConfiguration().getStringList("no-chat-permission-message");
            if (noPermMsg.isEmpty()) {
                sender.sendMessage("У вас нет прав писать в чат!");
                return;
            }
            for (String line : noPermMsg) {
                line = applyColor(line);
                sender.sendMessage(line);
            }
            return;
        }

        // 2. Локальный/глобальный чат (как раньше)
        String originalMessage = event.getMessage();
        event.setCancelled(true);

        boolean globalEnabled = plugin.getConfiguration().getBoolean("global-chat.enabled", true);
        String globalPrefix = plugin.getConfiguration().getString("global-chat.prefix", "!");
        boolean isGlobal = globalEnabled && originalMessage.startsWith(globalPrefix);

        String format;
        String chatMessage;

        if (isGlobal) {
            // Глобальный
            chatMessage = originalMessage.substring(globalPrefix.length());
            format = plugin.getConfiguration().getString("global-chat.format",
                    "&#ffaa00[GLOBAL] %player_name%: %message%");
        } else {
            // Локальный
            boolean localEnabled = plugin.getConfiguration().getBoolean("local-chat.enabled", true);
            if (!localEnabled) {
                return;
            }
            chatMessage = originalMessage;
            format = plugin.getConfiguration().getString("local-chat.format",
                    "&#ffffff[LOCAL] %player_name%: %message%");
        }

        format = format.replace("%player_name%", sender.getName())
                .replace("%message%", chatMessage);

        if (plugin.isPlaceholderAPIHooked()) {
            format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, format);
        }

        // Обрабатываем упоминания
        String coloredFormat = applyColor(format);
        Matcher matcher = MENTION_PATTERN.matcher(coloredFormat);

        List<Player> mentionedOnlinePlayers = new java.util.ArrayList<>();
        String mentionFormat = plugin.getConfiguration().getString("mention.highlight-format", "&#ff5e0d&l@%mention%&r");
        String mentionSoundKey = plugin.getConfiguration().getString("mention.sound", "minecraft:block.note_block.pling");

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String mentionName = matcher.group(1);
            Player mentioned = Bukkit.getPlayerExact(mentionName);
            if (mentioned != null && mentioned.isOnline()) {
                // Заменяем на подсвеченный вариант
                String highlight = mentionFormat.replace("%mention%", mentionName);
                highlight = applyColor(highlight);
                matcher.appendReplacement(sb, highlight);
                mentionedOnlinePlayers.add(mentioned);
            } else {
                // Оставляем как есть
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        coloredFormat = sb.toString();

        // Собираем Adventure-компонент, добавляем hover/click на ник
        Component finalMessage = buildChatComponentWithSenderHover(sender, coloredFormat);

        // Рассылаем
        if (isGlobal) {
            // Глобал — всем
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(finalMessage);
            }
        } else {
            // Локал — в радиусе
            double radius = plugin.getConfiguration().getDouble("local-chat.radius", 70.0);
            Location loc = sender.getLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(loc.getWorld()) && p.getLocation().distance(loc) <= radius) {
                    p.sendMessage(finalMessage);
                }
            }
        }

        // Проигрываем звук упомянутым
        if (!mentionedOnlinePlayers.isEmpty()) {
            Sound pingSound = Sound.sound(
                    Key.key(mentionSoundKey),
                    Sound.Source.PLAYER,
                    1.0f,
                    1.0f
            );
            for (Player mentioned : mentionedOnlinePlayers) {
                mentioned.playSound(pingSound);
            }
        }
    }

    private Component buildChatComponentWithSenderHover(Player sender, String coloredFormat) {
        String senderName = sender.getName();
        int idx = coloredFormat.indexOf(senderName);
        if (idx == -1) {
            return LegacyComponentSerializer.legacySection().deserialize(coloredFormat);
        }

        String left = coloredFormat.substring(0, idx);
        String namePart = coloredFormat.substring(idx, idx + senderName.length());
        String right = coloredFormat.substring(idx + senderName.length());

        // hover-text
        List<String> hoverLines = plugin.getConfiguration().getStringList("hover-text");
        String joinedHover = String.join("\n", hoverLines);

        if (plugin.isPlaceholderAPIHooked()) {
            joinedHover = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, joinedHover);
        }
        joinedHover = applyColor(joinedHover);

        Component hoverComponent = LegacyComponentSerializer.legacySection().deserialize(joinedHover);
        Component nameComponent = LegacyComponentSerializer.legacySection().deserialize(namePart)
                .hoverEvent(HoverEvent.showText(hoverComponent))
                .clickEvent(ClickEvent.suggestCommand("/m " + senderName + " "));

        Component leftComp = LegacyComponentSerializer.legacySection().deserialize(left);
        Component rightComp = LegacyComponentSerializer.legacySection().deserialize(right);

        return Component.empty().append(leftComp).append(nameComponent).append(rightComp);
    }

    private String applyColor(String text) {
        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
            return Utils.applyHexColors(text);
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}