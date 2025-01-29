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
    private final Map<UUID, Long> lastCmdTime = new HashMap<>();

    public CommandCooldownListener(CloverChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage(); // Например "/help" или "/m Player test"

        // 1) Проверяем, включён ли кулдаун на команды
        boolean cooldownEnabled = plugin.getConfiguration().getBoolean("commands-cooldown.enabled", true);
        if (!cooldownEnabled) {
            return; // Кулдаун отключён, ничего не делаем
        }

        // 2) Если это /m, пропускаем без кулдауна
        // (ЛС обрабатываются отдельно)
        if (message.toLowerCase().startsWith("/m ")) {
            return;
        }

        // 3) Проверяем право bypass
        // Если у игрока есть "cloverchat.commandcooldown.bypass", пропускаем без кулдауна
        if (player.hasPermission("cloverchat.commandcooldown.bypass")) {
            return;
        }

        // 4) Получаем настройку времени кулдауна
        long cooldownSeconds = plugin.getConfiguration().getLong("commands-cooldown.seconds", 5);
        long cooldownMillis = cooldownSeconds * 1000L;

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        // 5) Если в словаре lastCmdTime нет записи — впервые
        // Иначе проверим разницу (now - lastTime)
        if (lastCmdTime.containsKey(uuid)) {
            long lastTime = lastCmdTime.get(uuid);
            long diff = now - lastTime;

            if (diff < cooldownMillis) {
                // Кулдаун ещё не вышел → запрещаем команду, выводим сообщение
                long remain = (cooldownMillis - diff) / 1000;

                List<String> cooldownMsg = plugin.getConfiguration().getStringList("commands-cooldown.message");
                if (!cooldownMsg.isEmpty()) {
                    for (String line : cooldownMsg) {
                        line = line.replace("%remain%", String.valueOf(remain));
                        // Переводим цвета (если нужно)
                        line = plugin.applyColor(line);
                        player.sendMessage(plugin.deserializeColored(line));
                    }
                } else {
                    player.sendMessage("Подождите ещё " + remain + " секунд перед вводом следующей команды.");
                }

                // Важно: отменяем событие, чтобы команда НЕ выполнилась
                event.setCancelled(true);
                return;
            }
        }

        // 6) Если дошли сюда — кулдаун не мешает
        // Обновляем время последней введённой команды
        lastCmdTime.put(uuid, now);
        // Позволяем событию продолжиться (команда исполнится)
    }
}