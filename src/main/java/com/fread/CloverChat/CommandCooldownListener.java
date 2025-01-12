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
        String message = event.getMessage(); // "/help" или "/m name hi", etc.

        boolean cooldownEnabled = plugin.getConfiguration().getBoolean("commands-cooldown.enabled", true);
        if (!cooldownEnabled) return;

        // Если это /m, пропускаем без кулдауна
        if (message.toLowerCase().startsWith("/m ")) {
            return;
        }

        // Проверяем право bypass
        if (player.hasPermission("cloverchat.commandcooldown.bypass")) {
            return;
        }

        long cooldownSeconds = plugin.getConfiguration().getLong("commands-cooldown.seconds", 5);
        long cooldownMillis = cooldownSeconds * 1000L;
        long now = System.currentTimeMillis();

        if (lastCmdTime.containsKey(player.getUniqueId())) {
            long lastTime = lastCmdTime.get(player.getUniqueId());
            long diff = now - lastTime;

            if (diff < cooldownMillis) {
                long remain = (cooldownMillis - diff) / 1000;

                List<String> cooldownMsg = plugin.getConfiguration().getStringList("commands-cooldown.message");
                if (!cooldownMsg.isEmpty()) {
                    for (String line : cooldownMsg) {
                        line = line.replace("%remain%", String.valueOf(remain));
                        if (plugin.getConfiguration().getBoolean("hex-colors", true)) {
                            line = Utils.applyHexColors(line);
                        } else {
                            line = org.bukkit.ChatColor.translateAlternateColorCodes('&', line);
                        }
                        player.sendMessage(line);
                    }
                } else {
                    player.sendMessage("Подождите ещё " + remain + " секунд перед вводом следующей команды.");
                }
                event.setCancelled(true);
                return;
            }
        }
        lastCmdTime.put(player.getUniqueId(), now);
    }
}
