package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.util.MiniMessageFormatter;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.util.StarTierModifier;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler {

    private static final Pattern HEX_PATTERN = Pattern.compile("(§x(?:§[0-9a-fA-F]){6})");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,})"
    );

    private final ChatLayout feature;

    public ChatHandler(ChatLayout feature) {
        this.feature = feature;
    }

    public String translateMinecraftToMiniMessage(String text, boolean prefix) {
        text = setHexColors(text);
        if (!prefix) {
            text = processURLs(text);
        }
        Map<String, String> replacements = new HashMap<>();
        replacements.put("§0", "<black>");
        replacements.put("§1", "<dark_blue>");
        replacements.put("§2", "<dark_green>");
        replacements.put("§3", "<dark_aqua>");
        replacements.put("§4", "<dark_red>");
        replacements.put("§5", "<dark_purple>");
        replacements.put("§6", "<gold>");
        replacements.put("§7", "<gray>");
        replacements.put("§8", "<dark_gray>");
        replacements.put("§9", "<blue>");
        replacements.put("§a", "<green>");
        replacements.put("§b", "<aqua>");
        replacements.put("§c", "<red>");
        replacements.put("§d", "<light_purple>");
        replacements.put("§e", "<yellow>");
        replacements.put("§f", "<white>");
        replacements.put("§k", "<obfuscated>");
        replacements.put("§l", "<bold>");
        replacements.put("§m", "<strikethrough>");
        replacements.put("§n", "<underline>");
        replacements.put("§o", "<italic>");
        replacements.put("§r", "<reset>");
        replacements.put("&0", "<black>");
        replacements.put("&1", "<dark_blue>");
        replacements.put("&2", "<dark_green>");
        replacements.put("&3", "<dark_aqua>");
        replacements.put("&4", "<dark_red>");
        replacements.put("&5", "<dark_purple>");
        replacements.put("&6", "<gold>");
        replacements.put("&7", "<gray>");
        replacements.put("&8", "<dark_gray>");
        replacements.put("&9", "<blue>");
        replacements.put("&a", "<green>");
        replacements.put("&b", "<aqua>");
        replacements.put("&c", "<red>");
        replacements.put("&d", "<light_purple>");
        replacements.put("&e", "<yellow>");
        replacements.put("&f", "<white>");
        replacements.put("&k", "<obfuscated>");
        replacements.put("&l", "<bold>");
        replacements.put("&m", "<strikethrough>");
        replacements.put("&n", "<underline>");
        replacements.put("&o", "<italic>");
        replacements.put("&r", "<reset>");
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    private String processURLs(String message) {
        Matcher m = URL_PATTERN.matcher(message);
        while (m.find()) {
            String originalURL = m.group(1);
            String URL = originalURL;
            if (originalURL.contains("http://")) {
                URL = originalURL.replace("http://", "https://");
            } else if (!originalURL.contains("http://") && !originalURL.contains("https://")) {
                URL = "https://" + originalURL;
            }
            String minimessageURL = "<url:'" + URL + "'>" + originalURL + "</url>";
            message = message.replace(originalURL, minimessageURL);
        }
        return message;
    }

    public static String setHexColors(String message) {
        Matcher m = HEX_PATTERN.matcher(message);
        while (m.find()) {
            String hex = m.group(1);
            String newHex = "<" + hex.replaceFirst("§x", "#").replaceAll("§", "") + ">";
            message = message.replace(hex, newHex);
        }
        return message;
    }

    public String filterChatAttributes(Player player, String text) {
        if (!player.hasPermission("deluxechat.color")) {
            text = text.replaceAll("(§+)([0-9a-fA-Fx])", "");
        }
        if (!player.hasPermission("deluxechat.formatting")) {
            text = text.replaceAll("(§+)([k-orK-OR])", "");
        }
        return text;
    }

    public void sendModifiedChatMessage(Player player, String prefix, String chatMessage,
                                        Set<Audience> audienceSet) {
        if (audienceSet == null) return;

        Component prefixComponent = MiniMessageFormatter.prefixSerializer.deserialize(prefix);
        Component chatComponent;

        if (player.hasPermission("deluxechat.interactive")) {
            chatComponent = MiniMessageFormatter.chatColorAllSerializer.deserialize(chatMessage);
        } else if (player.hasPermission("deluxechat.formatting")) {
            chatComponent = MiniMessageFormatter.chatColorExtraSerializer.deserialize(chatMessage);
        } else if (player.hasPermission("deluxechat.color")) {
            chatComponent = MiniMessageFormatter.chatColorSerializer.deserialize(chatMessage);
        } else {
            chatComponent = MiniMessageFormatter.chatSerializer.deserialize(chatMessage);
        }
        Component finalMessage = prefixComponent.append(chatComponent);

        for (Audience audience : audienceSet) {
            audience.sendMessage(finalMessage);
        }
    }

    public String formatChatMessage(Player player, String chatMessage) {
        String filtered = filterChatAttributes(player, chatMessage);
        return translateMinecraftToMiniMessage(filtered, false);
    }

    public static String formatTooltip(List<String> strings, Player player) {
        StringBuilder concatenatedString = new StringBuilder();
        int length = strings.size();
        int index = 0;
        for (String str : strings) {
            String replacedString = PlaceholderAPIHook.parseWithPAPI(str, player);
            concatenatedString.append(replacedString);

            if (index < length-1) {
                concatenatedString.append("\n<reset>");
            }
            index++;
        }
        return concatenatedString.toString();
    }

    public String formatPrefix(Player player) {
        ChatFormat playerFormat = getPlayerFormat(player);

        // Prefix (stars + rank)
        int starTier = StarTierModifier.getStarTier(player);
        String starTierFormat = StarTierModifier.getStarTierFormat(starTier);
        String rank = PlaceholderAPIHook.parseWithPAPI(playerFormat.getPrefix(), player);
        String prefix = starTierFormat + rank;
        String prefixCommand = playerFormat.getPreClickCmd();
        String prefixTooltip = formatTooltip(playerFormat.getPrefixTooltip(), player).replace("<star_tier>", String.valueOf(starTier));
        String processed_prefix =  "<click:run_command:'"+prefixCommand+"'><hover:show_text:'"+prefixTooltip+"'>"+prefix+"</hover></click>";

        // Name
        String name = PlaceholderAPIHook.parseWithPAPI(playerFormat.getName(), player);
        String nameCommand = playerFormat.getNameClickCmd();
        String nameTooltip = formatTooltip(playerFormat.getNameTooltip(), player);
        String processed_name =  "<click:suggest_command:'"+nameCommand+"'><hover:show_text:'"+nameTooltip+"'>"+name+"</hover></click>";

        // Suffix
        String suffix = PlaceholderAPIHook.parseWithPAPI(playerFormat.getSuffix(), player);

        String chatLayout = processed_prefix + processed_name + suffix;
        chatLayout = translateMinecraftToMiniMessage(chatLayout, true);
        return chatLayout;
    }

    public ChatFormat getPlayerFormat(Player player) {
        return feature.getChatFormatRegistry().getPlayerFormat(player);
    }
}
