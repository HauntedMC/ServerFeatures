package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.util.MiniMessageFormatter;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.util.StarTierModifier;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Handles formatting & component creation for the renderer.
public class ChatHandler {

    private static final Pattern HEX_PATTERN = Pattern.compile("(§x(?:§[0-9a-fA-F]){6})");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,})"
    );

    private final ChatLayout feature;

    public ChatHandler(ChatLayout feature) {
        this.feature = feature;
    }

    /**
     * Builds the full rendered message:
     *   [prefix + name + suffix] + [formatted chat message]
     */
    public Component renderBaseMessage(Player sender, Component messageComponent) {
        Component prefix = buildPrefixComponent(sender);
        Component chat = buildChatComponent(sender, messageComponent);
        return prefix.append(chat);
    }

    /** Prefix + name + suffix (click/hover + star tier + placeholders). */
    private Component buildPrefixComponent(Player player) {
        String mm = formatPrefix(player); // MiniMessage string
        return MiniMessageFormatter.prefixSerializer.deserialize(mm);
    }

    /** Chat message component (colors/formatting/url handling gated by perms). */
    private Component buildChatComponent(Player player, Component messageComponent) {
        // Convert incoming component to legacy text so we can support users typing &/§ codes.
        String rawMessage = LegacyComponentSerializer.legacySection().serialize(messageComponent);

        String filtered = filterChatAttributes(player, rawMessage);
        String miniMsg = translateMinecraftToMiniMessage(filtered, false);

        if (player.hasPermission("deluxechat.interactive")) {
            return MiniMessageFormatter.chatColorAllSerializer.deserialize(miniMsg);
        } else if (player.hasPermission("deluxechat.formatting")) {
            return MiniMessageFormatter.chatColorExtraSerializer.deserialize(miniMsg);
        } else if (player.hasPermission("deluxechat.color")) {
            return MiniMessageFormatter.chatColorSerializer.deserialize(miniMsg);
        } else {
            return MiniMessageFormatter.chatSerializer.deserialize(miniMsg);
        }
    }

    /* ==== Legacy color/format → MiniMessage helpers ==== */

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

        for (Map.Entry<String, String> e : replacements.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }
        return text;
    }

    private String processURLs(String message) {
        Matcher m = URL_PATTERN.matcher(message);
        while (m.find()) {
            String originalURL = m.group(1);
            String url = originalURL;
            if (originalURL.startsWith("http://")) {
                url = originalURL.replace("http://", "https://");
            } else if (!originalURL.startsWith("https://")) {
                url = "https://" + originalURL;
            }
            String minimessageURL = "<url:'" + url + "'>" + originalURL + "</url>";
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

    /* ==== Prefix formatting (stars + rank + name + suffix) ==== */

    public static String formatTooltip(List<String> strings, Player player) {
        StringBuilder sb = new StringBuilder();
        int length = strings.size();
        int index = 0;
        for (String str : strings) {
            String replaced = PlaceholderAPIHook.applyPlaceholders(str, player);
            sb.append(replaced);
            if (index < length - 1) {
                sb.append("\n<reset>");
            }
            index++;
        }
        return sb.toString();
    }

    public String formatPrefix(Player player) {
        ChatFormat playerFormat = getPlayerFormat(player);

        // Prefix (stars + rank)
        int starTier = StarTierModifier.getStarTier(player);
        String starTierFormat = StarTierModifier.getStarTierFormat(starTier);
        String rank = PlaceholderAPIHook.applyPlaceholders(playerFormat.getPrefix(), player);
        String prefix = starTierFormat + rank;
        String prefixCommand = playerFormat.getPreClickCmd();
        String prefixTooltip = formatTooltip(playerFormat.getPrefixTooltip(), player)
                .replace("<star_tier>", String.valueOf(starTier));
        String processed_prefix = "<click:run_command:'" + prefixCommand + "'><hover:show_text:'" + prefixTooltip + "'>" + prefix + "</hover></click>";

        // Name
        String name = PlaceholderAPIHook.applyPlaceholders(playerFormat.getName(), player);
        String nameCommand = playerFormat.getNameClickCmd();
        String nameTooltip = formatTooltip(playerFormat.getNameTooltip(), player);
        String processed_name = "<click:suggest_command:'" + nameCommand + "'><hover:show_text:'" + nameTooltip + "'>" + name + "</hover></click>";

        // Suffix
        String suffix = PlaceholderAPIHook.applyPlaceholders(playerFormat.getSuffix(), player);

        String chatLayout = processed_prefix + processed_name + suffix;
        chatLayout = translateMinecraftToMiniMessage(chatLayout, true);
        return chatLayout;
    }

    public ChatFormat getPlayerFormat(Player player) {
        return feature.getChatFormatRegistry().getPlayerFormat(player);
    }
}
