package com.fread.CloverChat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;

public class ChatListener implements Listener {

    private final CloverChat plugin;

    public ChatListener(CloverChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String originalMessage = event.getMessage();

        // Отключаем дефолтный формат, будем отсылать сами
        event.setCancelled(true);

        boolean globalEnabled = plugin.getConfiguration().getBoolean("global-chat.enabled", true);
        String globalPrefix = plugin.getConfiguration().getString("global-chat.prefix", "!");
        boolean isGlobal = globalEnabled && originalMessage.startsWith(globalPrefix);

        if (isGlobal) {
            // Убираем префикс из сообщения
            String messageWithoutPrefix = originalMessage.substring(globalPrefix.length());

            // Получаем формат глобального чата
            String format = plugin.getConfiguration().getString(
                    "global-chat.format",
                    "&#ffaa00[GLOBAL] %player_name%: %message%"
            );
            // Подставляем примитивные плейсхолдеры
            format = format.replace("%player_name%", sender.getName())
                    .replace("%message%", messageWithoutPrefix);

            // PlaceholderAPI
            if (plugin.isPlaceholderAPIHooked()) {
                format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, format);
            }

            // Adventure-компонент
            Component finalMessage = buildChatComponent(sender, format);

            // Отправляем всем
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(finalMessage);
            }

        } else {
            // Локальный чат
            boolean localEnabled = plugin.getConfiguration().getBoolean("local-chat.enabled", true);
            if (!localEnabled) {
                // Если локальный отключён, можно заглушить или сделать fallback
                return;
            }

            double radius = plugin.getConfiguration().getDouble("local-chat.radius", 70.0);
            String format = plugin.getConfiguration().getString(
                    "local-chat.format",
                    "&#ffffff[LOCAL] %player_name%: %message%"
            );

            format = format.replace("%player_name%", sender.getName())
                    .replace("%message%", originalMessage);

            if (plugin.isPlaceholderAPIHooked()) {
                format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, format);
            }

            Component finalMessage = buildChatComponent(sender, format);

            // Шлём только игрокам в радиусе
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
     * Формирует Adventure-компонент. При этом ник игрока (sender.getName())
     * заменяется на кликабельный элемент с ховером и suggestCommand.
     */
    private Component buildChatComponent(Player sender, String formattedText) {
        // Сначала переведём & + &#RRGGBB => §-цвета
        String colored = plugin.getConfiguration().getBoolean("hex-colors", true)
                ? Utils.applyHexColors(formattedText)
                : org.bukkit.ChatColor.translateAlternateColorCodes('&', formattedText);

        // Найдём index имени
        String senderName = sender.getName();
        int idx = colored.indexOf(senderName);
        if (idx == -1) {
            // Если не нашли, просто десериализуем всё в один компонент
            return LegacyComponentSerializer.legacySection().deserialize(colored);
        }

        // Разделим строку на 3 части: левая, ник, правая
        String left = colored.substring(0, idx);
        String namePart = colored.substring(idx, idx + senderName.length());
        String right = colored.substring(idx + senderName.length());

        // Собираем hover-текст
        List<String> hoverLines = plugin.getConfiguration().getStringList("hover-text");
        String joinedHover = String.join("\n", hoverLines);

        // Подставим PlaceholderAPI в hover (если есть)
        if (plugin.isPlaceholderAPIHooked()) {
            joinedHover = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(sender, joinedHover);
        }

        // Переводим цвета в hover
        joinedHover = plugin.getConfiguration().getBoolean("hex-colors", true)
                ? Utils.applyHexColors(joinedHover)
                : org.bukkit.ChatColor.translateAlternateColorCodes('&', joinedHover);

        // Adventure-компонент для hover
        Component hoverComponent = LegacyComponentSerializer.legacySection().deserialize(joinedHover);

        // Собираем сам компонент ника
        Component nameComponent = LegacyComponentSerializer.legacySection().deserialize(namePart)
                .hoverEvent(HoverEvent.showText(hoverComponent))
                // Ключевой момент: suggestCommand, чтобы не выполнялась сразу
                .clickEvent(ClickEvent.suggestCommand("/m " + senderName + " "));

        // Десериализуем левую и правую часть
        Component leftComp = LegacyComponentSerializer.legacySection().deserialize(left);
        Component rightComp = LegacyComponentSerializer.legacySection().deserialize(right);

        // Склеим всё: left + (ник) + right
        return Component.empty().append(leftComp).append(nameComponent).append(rightComp);
    }
}
