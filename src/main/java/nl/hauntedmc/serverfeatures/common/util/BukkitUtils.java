package nl.hauntedmc.serverfeatures.common.util;

import org.bukkit.ChatColor;

public class BukkitUtils {
    public static String parseLegacyColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

}
