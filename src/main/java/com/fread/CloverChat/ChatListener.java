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

    // Регулярка для поиска "@" + nickname (упрощённая; на практике можно доработать)
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]{3,16})");

    public ChatListener(CloverChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String originalMessage = event.getMessage();

        // Отключаем дефолтный чат
        event.setCancelled(true);

        boolean globalEnabled = plugin.getConfiguration().getBoolean("global-chat.enabled", true);
        String globalPrefix = plugin.getConfiguration().getString("global-chat.prefix", "!");
        boolean isGlobal = globalEnabled && originalMessage.startsWith(globalPrefix);

        String format;
        String chatMessage;

        if (isGlobal) {
            // Глобальный чат
            chatMessage = originalMessage.substring(globalPrefix.length());
            format = plugin.getConfiguration().getString("global-chat.format",
                    "&#ffaa00[GLOBAL] %player_name%: %message%");
        } else {
            // Локальный чат
            boolean localEnabled = plugin.getConfiguration().getBoolean("local-chat.enabled", true);
            if (!localEnabled) {
                // Если локальный отключён — ничего не отправляем
                return;
            }

            chatMessage = originalMessage;
            format = plugin.getConfiguration().getString("local-chat.format",
                    "&#ffffff[LOCAL] %player_name%: %message%");
        }

        // Подставляем базовые плейсхолдеры
        format = format.replace("%player_name%", sender.getName())
                .replace("%message%", chatMessage);

        // PlaceholderAPI (если есть)
        if (plugin.isPlaceholderAPIHooked()) {
            format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, format);
        }

        // Теперь обрабатываем упоминания в самом тексте (chatMessage),
        // чтобы заменить @PlayerName на цветной вариант, если PlayerName онлайн.
        // И параллельно соберём список игроков, которых упомянули, для проигрывания звука.

        // Сначала сделаем "pre-colored" строку (дальше всё равно будем разбирать под Adventure).
        String coloredFormat = plugin.getConfiguration().getBoolean("hex-colors", true)
                ? Utils.applyHexColors(format)
                : org.bukkit.ChatColor.translateAlternateColorCodes('&', format);

        // Ищем в coloredFormat все вхождения @nickname
        Matcher matcher = MENTION_PATTERN.matcher(coloredFormat);

        // Храним список игроков, которых упомянули
        // (Чтобы потом проиграть звук)
        List<Player> mentionedOnlinePlayers = new java.util.ArrayList<>();

        // Получаем шаблон форматирования упоминания из конфига
        // (например, "&#ff5e0d&l@%mention%&r")
        String mentionFormat = plugin.getConfiguration()
                .getString("mention.highlight-format", "&#ff5e0d&l@%mention%&r");

        // Если нужно будет воспроизводить звук
        String mentionSoundKey = plugin.getConfiguration()
                .getString("mention.sound", "minecraft:block.note_block.pling");

        // Проходим по всем совпадениям
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String mentionName = matcher.group(1); // Ник без '@'
            Player mentioned = Bukkit.getPlayerExact(mentionName);

            if (mentioned != null && mentioned.isOnline()) {
                // Игрок онлайн → выделяем упоминание
                String highlight = mentionFormat.replace("%mention%", mentionName);
                // Переводим & + &#RRGGBB => §
                if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
                    highlight = Utils.applyHexColors(highlight);
                } else {
                    highlight = org.bukkit.ChatColor.translateAlternateColorCodes('&', highlight);
                }
                matcher.appendReplacement(sb, highlight);

                // Добавим в список для воспроизведения звука
                mentionedOnlinePlayers.add(mentioned);
            } else {
                // Игрок офлайн → оставляем упоминание без изменений
                // Или, если хотите совсем убрать '@', можно это сделать.
                matcher.appendReplacement(sb, matcher.group(0));

                // При желании можно отправлять sender'у системное сообщение
                String msgNotFound = plugin.getConfiguration()
                        .getString("system-messages.player-not-found", "&cИгрок @%mention% не найден!");
                // msgNotFound = msgNotFound.replace("%mention%", mentionName) и т.д.
                // sender.sendMessage(...), если хотите оповестить об офлайне
            }
        }
        matcher.appendTail(sb);

        // В итоге sb — уже строка, где упоминания онлайновых игроков заменены на подсвеченный текст
        coloredFormat = sb.toString();

        // Собираем Adventure-компонент — но учтём, что нам ещё нужно сделать hover/click на ИМЯ отправителя
        Component finalMessage = buildChatComponentWithSenderHover(sender, coloredFormat);

        // Рассылаем сообщение
        if (isGlobal) {
            // Глобал: всем
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(finalMessage);
            }
        } else {
            // Локал: radius
            double radius = plugin.getConfiguration().getDouble("local-chat.radius", 70.0);
            Location loc = sender.getLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(loc.getWorld()) && p.getLocation().distance(loc) <= radius) {
                    p.sendMessage(finalMessage);
                }
            }
        }

        // Проигрываем звук упомянутым онлайн-игрокам
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

    /**
     * Собираем Adventure-компонент для сообщения, в котором
     * НИК отправителя делается кликабельным/hover'ящимся,
     * а всё остальное идёт как есть (уже готовая legacy-строка coloredFormat).
     */
    private Component buildChatComponentWithSenderHover(Player sender, String coloredFormat) {
        // Ищем индекс senderName
        String senderName = sender.getName();
        int idx = coloredFormat.indexOf(senderName);
        if (idx == -1) {
            // Если не нашли имя, просто десериализуем всё
            return LegacyComponentSerializer.legacySection().deserialize(coloredFormat);
        }

        String left = coloredFormat.substring(0, idx);
        String namePart = coloredFormat.substring(idx, idx + senderName.length());
        String right = coloredFormat.substring(idx + senderName.length());

        // Собираем hover-текст (настройки из config.yml -> hover-text)
        List<String> hoverLines = plugin.getConfiguration().getStringList("hover-text");
        String joinedHover = String.join("\n", hoverLines);

        if (plugin.isPlaceholderAPIHooked()) {
            joinedHover = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, joinedHover);
        }
        joinedHover = plugin.getConfiguration().getBoolean("hex-colors", true)
                ? Utils.applyHexColors(joinedHover)
                : org.bukkit.ChatColor.translateAlternateColorCodes('&', joinedHover);

        Component hoverComponent = LegacyComponentSerializer.legacySection().deserialize(joinedHover);

        // Делаем кликабельный ник
        Component nameComponent = LegacyComponentSerializer.legacySection().deserialize(namePart)
                .hoverEvent(HoverEvent.showText(hoverComponent))
                .clickEvent(ClickEvent.suggestCommand("/m " + senderName + " "));

        // Левая и правая части
        Component leftComp = LegacyComponentSerializer.legacySection().deserialize(left);
        Component rightComp = LegacyComponentSerializer.legacySection().deserialize(right);

        // Склеиваем
        return Component.empty().append(leftComp).append(nameComponent).append(rightComp);
    }
}
