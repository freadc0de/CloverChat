package com.fread.CloverChat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent; // Deprecated, но пока работает
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class ChatListener implements Listener {

    private final CloverChat plugin;

    // Регулярка для http/https ссылок
    private static final Pattern LINK_PATTERN = Pattern.compile("(https?://\\S+)", Pattern.CASE_INSENSITIVE);

    public ChatListener(CloverChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();

        // 1) Проверка прав
        if (!sender.hasPermission("cloverchat.chat.use")) {
            event.setCancelled(true);

            List<String> noPermMsg = plugin.getConfiguration().getStringList("no-chat-permission-message");
            if (noPermMsg.isEmpty()) {
                sender.sendMessage("У вас нет прав писать в чат!");
                return;
            }
            for (String line : noPermMsg) {
                sender.sendMessage(applyColor(line));
            }
            return;
        }

        // 2) Определяем, глобальный или локальный чат
        String originalMessage = event.getMessage();
        event.setCancelled(true);

        boolean globalEnabled = plugin.getConfiguration().getBoolean("global-chat.enabled", true);
        String globalPrefix = plugin.getConfiguration().getString("global-chat.prefix", "!");
        boolean isGlobal = globalEnabled && originalMessage.startsWith(globalPrefix);

        String chatMessage;
        String format;
        if (isGlobal) {
            chatMessage = originalMessage.substring(globalPrefix.length());
            format = plugin.getConfiguration().getString("global-chat.format",
                    "&#ffaa00[GLOBAL] %player_name%: %message%");
        } else {
            boolean localEnabled = plugin.getConfiguration().getBoolean("local-chat.enabled", true);
            if (!localEnabled) {
                return;
            }
            chatMessage = originalMessage;
            format = plugin.getConfiguration().getString("local-chat.format",
                    "&#ffffff[LOCAL] %player_name%: %message%");
        }

        // 3) Подставляем имя и текст
        format = format.replace("%player_name%", sender.getName())
                .replace("%message%", chatMessage);

        // 4) PlaceholderAPI (если установлено)
        if (plugin.isPlaceholderAPIHooked()) {
            format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, format);
        }

        // 5) Обрабатываем упоминания @Name
        String finalString = applyColor(format);
        finalString = processMentions(finalString, sender);

        // 6) Пропускаем сообщение через мат-фильтр (censor)
        finalString = censorMessage(finalString);

        // 7) Собираем финальный Component (ссылки + hover на ник)
        Component finalMessage = buildFinalComponent(finalString, sender);

        // 8) Рассылаем
        if (isGlobal) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(finalMessage);
            }
        } else {
            double radius = plugin.getConfiguration().getDouble("local-chat.radius", 70.0);
            Location loc = sender.getLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(loc.getWorld())
                        && p.getLocation().distance(loc) <= radius) {
                    p.sendMessage(finalMessage);
                }
            }
        }
    }

    /**
     * Собираем финальный компонент:
     * - Ищем первое вхождение sender.getName() -> делаем subcomponent с hover (из hover-text).
     * - Остальное (до/после) парсим как ссылки.
     */
    private Component buildFinalComponent(String input, Player sender) {
        String senderName = sender.getName();
        int idx = input.indexOf(senderName);
        if (idx == -1) {
            // Если имя не найдено, парсим всё как ссылки
            return parseLinks(input);
        }

        // Разделяем на 3 части
        String left = input.substring(0, idx);
        String namePart = input.substring(idx, idx + senderName.length());
        String right = input.substring(idx + senderName.length());

        // "left" + (nik c hover) + "right"
        Component leftComp = parseLinks(left);
        Component nameComp = buildNameHoverComponent(namePart, sender);
        Component rightComp = parseLinks(right);

        return Component.empty()
                .append(leftComp)
                .append(nameComp)
                .append(rightComp);
    }

    /**
     * Делает subcomponent для никнейма с hover-текстом (hover-text из конфига)
     * и clickEvent (например, /m <name>)
     */
    private Component buildNameHoverComponent(String rawName, Player sender) {
        List<String> hoverLines = plugin.getConfiguration().getStringList("hover-text");
        if (hoverLines.isEmpty()) {
            // Если нет строк -> просто цветной ник
            return deserializeColored(rawName);
        }

        // Обработаем плейсхолдеры
        for (int i = 0; i < hoverLines.size(); i++) {
            String line = hoverLines.get(i).replace("%player_name%", sender.getName());
            if (plugin.isPlaceholderAPIHooked()) {
                line = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, line);
            }
            hoverLines.set(i, line);
        }

        // Склеим
        String joined = String.join("\n", hoverLines);
        joined = applyColor(joined);

        // Собираем компонент
        return deserializeColored(rawName)
                .hoverEvent(HoverEvent.showText(deserializeColored(joined)))
                .clickEvent(ClickEvent.suggestCommand("/m " + sender.getName() + " "));
    }

    /**
     * Парсим ссылки (http/https), заменяя их на links.format (по умолчанию "*ссылка*"),
     * + hover-lines (links.hover-lines) + openUrl.
     */
    private Component parseLinks(String input) {
        boolean linksEnabled = plugin.getConfiguration().getBoolean("links.enabled", true);
        if (!linksEnabled) {
            return deserializeColored(input);
        }

        String linkFormat = plugin.getConfiguration().getString("links.format", "*ссылка*");
        List<String> hoverLines = plugin.getConfiguration().getStringList("links.hover-lines");
        if (hoverLines.isEmpty()) {
            hoverLines = Collections.singletonList("&7%url% &f- Сайт");
        }

        Component result = Component.empty();
        Matcher matcher = LINK_PATTERN.matcher(input);

        int lastEnd = 0;
        while (matcher.find()) {
            String beforeLink = input.substring(lastEnd, matcher.start());
            String url = matcher.group(1);

            // Добавить текст ДО ссылки
            if (!beforeLink.isEmpty()) {
                result = result.append(deserializeColored(beforeLink));
            }

            // Видимый текст вместо ссылки (например "&#ffcb1b&l*ссылка*")
            String linkTextColored = applyColor(linkFormat);
            Component linkComp = LegacyComponentSerializer.legacySection().deserialize(linkTextColored);

            // Hover (многострочный)
            List<String> replacedHover = new ArrayList<>();
            for (String line : hoverLines) {
                replacedHover.add(line.replace("%url%", url));
            }
            String joinedHover = String.join("\n", replacedHover);
            joinedHover = applyColor(joinedHover);

            // Добавляем HoverEvent и ClickEvent
            linkComp = linkComp.hoverEvent(HoverEvent.showText(
                            LegacyComponentSerializer.legacySection().deserialize(joinedHover)
                    ))
                    .clickEvent(ClickEvent.openUrl(url));

            result = result.append(linkComp);
            lastEnd = matcher.end();
        }

        // Хвост
        if (lastEnd < input.length()) {
            String tail = input.substring(lastEnd);
            result = result.append(deserializeColored(tail));
        }

        return result;
    }

    /**
     * Заменяет @PlayerName на цветной вариант (mention.highlight-format),
     * проигрывает звук упомянутым.
     */
    private String processMentions(String text, Player sender) {
        Pattern mentionPattern = Pattern.compile("@([A-Za-z0-9_]{3,16})");
        Matcher matcher = mentionPattern.matcher(text);

        List<Player> mentionedPlayers = new ArrayList<>();

        String mentionFormat = plugin.getConfiguration().getString("mention.highlight-format", "&#ff5e0d&l@%mention%&r");
        String mentionSoundKey = plugin.getConfiguration().getString("mention.sound", "minecraft:block.note_block.pling");

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String mentionName = matcher.group(1);
            Player target = Bukkit.getPlayerExact(mentionName);
            if (target != null && target.isOnline()) {
                String highlight = mentionFormat.replace("%mention%", mentionName);
                highlight = applyColor(highlight);
                matcher.appendReplacement(sb, highlight);

                mentionedPlayers.add(target);
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);

        // Проиграем звук
        if (!mentionedPlayers.isEmpty()) {
            Sound pingSound = Sound.sound(Key.key(mentionSoundKey), Sound.Source.PLAYER, 1.0f, 1.0f);
            for (Player p : mentionedPlayers) {
                p.playSound(pingSound);
            }
        }

        return sb.toString();
    }

    // -- Методы для цензуры (мат-фильтр) --

    /**
     * Проходит по списку запрещённых слов и частично замещает их
     * (херня -> х***я).
     */
    private String censorMessage(String msg) {
        boolean censorEnabled = plugin.getConfiguration().getBoolean("censor.enabled", true);
        if (!censorEnabled) {
            return msg;
        }

        List<String> banWords = plugin.getConfiguration().getStringList("censor.words");
        if (banWords.isEmpty()) {
            return msg;
        }

        for (String ban : banWords) {
            if (ban.isEmpty()) continue;

            // Без учёта регистра
            String regex = "(?i)" + Pattern.quote(ban);
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(msg);

            while (m.find()) {
                String found = m.group();
                // маскируем
                String masked = maskWord(found);
                // заменяем только первое вхождение за проход
                // потом обновляем matcher
                msg = m.replaceFirst(Matcher.quoteReplacement(masked));
                m = p.matcher(msg);
            }
        }

        return msg;
    }

    /**
     * maskWord: превращает "херня" -> "х***я"
     *            (первая, последняя буква, а середина - '*').
     */
    private String maskWord(String word) {
        if (word.length() <= 2) {
            // Если слово короткое, можно полностью заменить на "**"
            return word.replaceAll(".", "*");
        }
        // херня -> х + *** + я
        StringBuilder sb = new StringBuilder();
        sb.append(word.charAt(0));
        for (int i = 1; i < word.length() - 1; i++) {
            sb.append("*");
        }
        sb.append(word.charAt(word.length() - 1));
        return sb.toString();
    }

    // -- Методы цвета/десериализации:

    private String applyColor(String text) {
        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
            text = Utils.applyHexColors(text);
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    private Component deserializeColored(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}
