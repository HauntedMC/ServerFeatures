package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public class ChatFormatRegistry {

    private final TreeMap<Integer, ChatFormat> formats = new TreeMap<>();

    public ChatFormatRegistry(ChatLayout feature) {
        loadFormats(feature);
    }

    public void loadFormats(ChatLayout feature) {
        formats.clear();
        Object formatSetting = feature.getConfigHandler().getSetting("formats");
        if (formatSetting instanceof ConfigurationSection sec) {
            Set<String> keys = sec.getKeys(false);
            for (String key : keys) {
                int priority = sec.getInt(key + ".priority", Integer.MAX_VALUE);
                ChatFormat chatFormat = new ChatFormat(key, priority);
                chatFormat.setPrefix(sec.getString(key + ".prefix", ""));
                chatFormat.setName(sec.getString(key + ".name", "%player_name%"));
                chatFormat.setSuffix(sec.getString(key + ".suffix", " > "));
                chatFormat.setPrefixTooltip(sec.getStringList(key + ".prefix_tooltip"));
                chatFormat.setNameTooltip(sec.getStringList(key + ".name_tooltip"));
                chatFormat.setSuffixTooltip(sec.getStringList(key + ".suffix_tooltip"));
                chatFormat.setPreClickCmd(sec.getString(key + ".prefix_click_command", ""));
                chatFormat.setNameClickCmd(sec.getString(key + ".name_click_command", ""));
                chatFormat.setSuffixClickCmd(sec.getString(key + ".suffix_click_command", ""));
                formats.put(priority, chatFormat);
            }
        }
    }

    public ChatFormat getPlayerFormat(org.bukkit.entity.Player player) {
        for (Map.Entry<Integer, ChatFormat> entry : formats.entrySet()) {
            ChatFormat format = entry.getValue();
            if (player.hasPermission("chatformat." + format.getIdentifier())) {
                return format;
            }
        }
        // Return the format with the highest priority (last entry) as default
        if (!formats.isEmpty()) {
            return formats.lastEntry().getValue();
        }
        // Fallback default if no formats were loaded
        ChatFormat defaultFormat = new ChatFormat("default", Integer.MAX_VALUE);
        defaultFormat.setPrefix("");
        defaultFormat.setName("%player_name%");
        defaultFormat.setSuffix(" > ");
        return defaultFormat;
    }
}
