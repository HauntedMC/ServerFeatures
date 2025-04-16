package nl.hauntedmc.serverfeatures.common.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Map;

public class TextUtils {

    public static Component deserializeMultilineComponent(String text) {
        return Component.join(JoinConfiguration.separator(Component.newline()),
                Arrays.stream(text.split("<newline>"))
                        .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line))
                        .toList());
    }

    public static Component deserializeComponent(String text) {
        if (text.contains("<newline>")) {
            return deserializeMultilineComponent(text);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public static Component deserializeMMComponent(String text) {
        MiniMessage textSerializer = MiniMessage.builder().tags(TagResolver.builder()
                        .resolver(StandardTags.color())
                        .resolver(StandardTags.decorations())
                        .resolver(StandardTags.clickEvent())
                        .resolver(StandardTags.hoverEvent())
                        .resolver(StandardTags.gradient())
                        .resolver(StandardTags.newline())
                        .resolver(StandardTags.transition())
                        .resolver(StandardTags.shadowColor())
                        .build())
                .build();

        return textSerializer.deserialize(text);
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
