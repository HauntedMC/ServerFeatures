package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ChatFormatRegistry {

    private final TreeMap<Integer, ChatFormat> formats = new TreeMap<>();

    public ChatFormatRegistry(ChatLayout feature) {
        loadFormats(feature);
    }

    public void loadFormats(ChatLayout feature) {
        formats.clear();

        ConfigNode root = feature.getConfigHandler().node("formats");
        Map<String, ConfigNode> children = root.children();
        if (children.isEmpty()) {
            return;
        }

        for (Map.Entry<String, ConfigNode> entry : children.entrySet()) {
            String key = entry.getKey();
            ConfigNode n = entry.getValue();

            int priority = n.get("priority").as(Integer.class, Integer.MAX_VALUE);

            ChatFormat chatFormat = new ChatFormat(key, priority);
            chatFormat.setPrefix(n.get("prefix").as(String.class, ""));
            chatFormat.setName(n.get("name").as(String.class, "%player_name%"));
            chatFormat.setSuffix(n.get("suffix").as(String.class, " > "));

            chatFormat.setPrefixTooltip(listOrEmpty(n.get("prefix_tooltip").listOf(String.class)));
            chatFormat.setNameTooltip(listOrEmpty(n.get("name_tooltip").listOf(String.class)));
            chatFormat.setSuffixTooltip(listOrEmpty(n.get("suffix_tooltip").listOf(String.class)));

            chatFormat.setPreClickCmd(n.get("prefix_click_command").as(String.class, ""));
            chatFormat.setNameClickCmd(n.get("name_click_command").as(String.class, ""));
            chatFormat.setSuffixClickCmd(n.get("suffix_click_command").as(String.class, ""));

            formats.put(priority, chatFormat);
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

    private static List<String> listOrEmpty(List<String> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
