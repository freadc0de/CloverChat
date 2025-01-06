package com.fread.CloverChat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandCooldownListener implements Listener {

    private final CloverChat plugin;

    // Храним время последней введённой команды (в мс) для каждого игрока
    private final Map<UUID, Long> lastCmdTime = new HashMap<>();

    public CommandCooldownListener(CloverChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage(); // например "/help" или "/m Player test"

        // Проверяем, включён ли кулдаун на команды
        boolean cooldownEnabled = plugin.getConfiguration().getBoolean("commands-cooldown.enabled", true);
        if (!cooldownEnabled) return;

        // Если это команда /m, пропускаем без кулдауна
        if (message.toLowerCase().startsWith("/m ")) {
            return;
        }

        // Если у игрока есть право bypass, тоже не проверяем кулдаун
        if (player.hasPermission("cloverchat.commandcooldown.bypass")) {
            return;
        }

        // Достаём настройки кулдауна
        long cooldownSeconds = plugin.getConfiguration().getLong("commands-cooldown.seconds", 5);
        long cooldownMillis = cooldownSeconds * 1000L;

        long now = System.currentTimeMillis();
        if (lastCmdTime.containsKey(player.getUniqueId())) {
            long lastTime = lastCmdTime.get(player.getUniqueId());
            long diff = now - lastTime;

            if (diff < cooldownMillis) {
                // Кулдаун ещё не вышел
                long remain = (cooldownMillis - diff) / 1000; // оставшиеся секунды

                // Сообщение из config
                List<String> cooldownMsg = plugin.getConfiguration().getStringList("commands-cooldown.message");
                if (!cooldownMsg.isEmpty()) {
                    for (String line : cooldownMsg) {
                        line = line.replace("%remain%", String.valueOf(remain));
                        // Пропускаем через цвет
                        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
                            line = Utils.applyHexColors(line);
                        } else {
                            line = org.bukkit.ChatColor.translateAlternateColorCodes('&', line);
                        }
                        player.sendMessage(line);
                    }
                } else {
                    // Дефолт
                    player.sendMessage("Подождите ещё " + remain + " секунд перед вводом следующей команды.");
                }
                event.setCancelled(true);
                return;
            }
        }
        // Обновляем время последней введённой команды
        lastCmdTime.put(player.getUniqueId(), now);
    }
}