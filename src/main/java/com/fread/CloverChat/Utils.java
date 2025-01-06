package com.fread.CloverChat;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([A-Fa-f0-9]{6})");

    /**
     * Превращает &#RRGGBB в §x§R§R§G§G§B§B (Minecraft 1.16+).
     * А затем переводит &-коды в §-коды (для совместимости).
     */
    public static String applyHexColors(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String group = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : group.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        // После этого переводим &x, &c и т.д.
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
