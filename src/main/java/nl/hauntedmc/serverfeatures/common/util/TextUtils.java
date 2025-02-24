package nl.hauntedmc.serverfeatures.common.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Map;

public class TextUtils {

    public static Component serializeComponent(String text) {
        return Component.join(JoinConfiguration.separator(Component.newline()),
                Arrays.stream(text.split("\n"))
                        .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line))
                        .toList());
    }

    public static String parseWithPAPI(String text, Player player) {
        String output = text;
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            output = PlaceholderAPI.setPlaceholders(player, text);
        }
        return output;
    }

    public static String parseLegacyColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String parsePlaceholders(String message, Map<String, String> placeholders) {
        String output = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }
}
