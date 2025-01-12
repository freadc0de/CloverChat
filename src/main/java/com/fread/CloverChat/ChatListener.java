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
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final CloverChat plugin;

    // Регулярка для ссылок (http/https) — можно менять по желанию
    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://[^\\s]+)", Pattern.CASE_INSENSITIVE);

    public ChatListener(CloverChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();

        // Проверяем право на чат
        if (!sender.hasPermission("cloverchat.chat.use")) {
            event.setCancelled(true);

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

        // Основная логика локального/глобального чата
        String originalMessage = event.getMessage();
        event.setCancelled(true);

        boolean globalEnabled = plugin.getConfiguration().getBoolean("global-chat.enabled", true);
        String globalPrefix = plugin.getConfiguration().getString("global-chat.prefix", "!");
        boolean isGlobal = globalEnabled && originalMessage.startsWith(globalPrefix);

        String chatMessage;
        String format;

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

        // PlaceholderAPI, если включен
        if (plugin.isPlaceholderAPIHooked()) {
            format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, format);
        }

        // Обработка упоминаний (@PlayerName)
        String coloredFormat = applyColor(format);
        coloredFormat = processMentions(coloredFormat, sender);

        // Финальная сборка Adventure-компонента с учётом ссылок
        Component finalMessage = buildLinkAwareComponent(coloredFormat);

        // Рассылаем
        if (isGlobal) {
            // Всем
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(finalMessage);
            }
        } else {
            // Локальный радиус
            double radius = plugin.getConfiguration().getDouble("local-chat.radius", 70.0);
            Location loc = sender.getLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(loc.getWorld()) && p.getLocation().distance(loc) <= radius) {
                    p.sendMessage(finalMessage);
                }
            }
        }
    }

    /**
     * Обрабатываем упоминания: если в тексте найдётся @PlayerName (3..16 символов),
     * то подсвечиваем их и проигрываем звук упомянутым игрокам.
     */
    private String processMentions(String text, Player sender) {
        List<Player> mentionedOnlinePlayers = new ArrayList<>();

        String mentionFormat = plugin.getConfiguration()
                .getString("mention.highlight-format", "&#ff5e0d&l@%mention%&r");
        String mentionSoundKey = plugin.getConfiguration()
                .getString("mention.sound", "minecraft:block.note_block.pling");

        Pattern mentionPattern = Pattern.compile("@([A-Za-z0-9_]{3,16})");
        Matcher matcher = mentionPattern.matcher(text);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String mentionName = matcher.group(1);
            Player mentioned = Bukkit.getPlayerExact(mentionName);
            if (mentioned != null && mentioned.isOnline()) {
                String highlight = mentionFormat.replace("%mention%", mentionName);
                highlight = applyColor(highlight);
                matcher.appendReplacement(sb, highlight);

                mentionedOnlinePlayers.add(mentioned);
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);

        String result = sb.toString();

        // Проиграем звук всем упомянутым
        if (!mentionedOnlinePlayers.isEmpty()) {
            Sound pingSound = Sound.sound(Key.key(mentionSoundKey), Sound.Source.PLAYER, 1.0f, 1.0f);
            for (Player mentioned : mentionedOnlinePlayers) {
                mentioned.playSound(pingSound);
            }
        }
        return result;
    }

    /**
     * Собирает компонент, где ссылки (http/https) кликабельны и имеют hover.
     */
    private Component buildLinkAwareComponent(String coloredFormat) {
        // Проверяем, включена ли обработка ссылок
        boolean linksEnabled = plugin.getConfiguration().getBoolean("links.enabled", true);
        if (!linksEnabled) {
            // Если не включена, просто десериализуем всё
            return LegacyComponentSerializer.legacySection().deserialize(coloredFormat);
        }

        // Считываем формат ссылки и hover-текст
        String linkFormat = plugin.getConfiguration().getString("links.format", "&#ff5e0d&l%link%");
        String hoverText = plugin.getConfiguration().getString("links.hover-text", "&c(ссылка на сайт) &f- Сайт");

        Component result = Component.empty();
        Matcher matcher = LINK_PATTERN.matcher(coloredFormat);

        int lastEnd = 0;
        while (matcher.find()) {
            String beforeLink = coloredFormat.substring(lastEnd, matcher.start());
            String url = matcher.group(1);

            // Добавляем кусок до ссылки
            if (!beforeLink.isEmpty()) {
                result = result.append(deserializeColored(beforeLink));
            }

            // Подставим URL
            String linkText = linkFormat.replace("%link%", url);

            // Создаём компонент для ссылки
            Component linkComp = deserializeColored(linkText)
                    .hoverEvent(HoverEvent.showText(deserializeColored(hoverText)))
                    .clickEvent(ClickEvent.openUrl(url));

            result = result.append(linkComp);

            lastEnd = matcher.end();
        }

        // Хвост
        if (lastEnd < coloredFormat.length()) {
            String tail = coloredFormat.substring(lastEnd);
            result = result.append(deserializeColored(tail));
        }

        return result;
    }

    /**
     * Простой метод для обработки цвета (& или &#RRGGBB) и десериализации в Component.
     */
    private Component deserializeColored(String text) {
        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
            text = Utils.applyHexColors(text);
        } else {
            text = org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
        }
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private String applyColor(String text) {
        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
            return Utils.applyHexColors(text);
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}
