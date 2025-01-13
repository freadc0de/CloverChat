package com.fread.CloverChat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import net.kyori.adventure.text.Component;

/**
 * Пример слушателя входа/выхода,
 * использует методы plugin.applyColor(...) и plugin.deserializeColored(...).
 */
public class JoinQuitListener implements Listener {

    private final CloverChat plugin;

    public JoinQuitListener(CloverChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        boolean joinEnabled = plugin.getConfiguration().getBoolean("join-message.enabled", true);
        if (!joinEnabled) {
            return;
        }

        // Строка из конфига
        String joinText = plugin.getConfiguration().getString(
                "join-message.text",
                "&#55FF55[+] %player_name% зашёл на сервер!"
        );

        // Подставляем %player_name%
        joinText = joinText.replace("%player_name%", player.getName());

        // Если есть PlaceholderAPI, пропускаем
        if (plugin.isPlaceholderAPIHooked()) {
            joinText = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, joinText);
        }

        // Окрашиваем (hex + &-коды)
        joinText = plugin.applyColor(joinText);

        // Убираем стандартное сообщение:
        event.joinMessage(null);

        // Собираем Adventure-компонент
        Component joinComp = plugin.deserializeColored(joinText);

        // Рассылаем всем
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(joinComp));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        boolean leaveEnabled = plugin.getConfiguration().getBoolean("leave-message.enabled", true);
        if (!leaveEnabled) {
            return;
        }

        String leaveText = plugin.getConfiguration().getString(
                "leave-message.text",
                "&#FF5555[-] %player_name% вышел с сервера!"
        );

        leaveText = leaveText.replace("%player_name%", player.getName());

        if (plugin.isPlaceholderAPIHooked()) {
            leaveText = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, leaveText);
        }

        leaveText = plugin.applyColor(leaveText);

        event.quitMessage(null);

        Component leaveComp = plugin.deserializeColored(leaveText);

        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(leaveComp));
    }
}