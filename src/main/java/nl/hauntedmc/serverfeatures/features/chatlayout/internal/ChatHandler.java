package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.util.StarTierModifier;
import org.bukkit.entity.Player;

import java.util.List;

public class ChatHandler {

    private final ChatFormatRegistry registry;

    public ChatHandler(ChatFormatRegistry registry) {
        this.registry = registry;
    }

    /**
     * Builds the full rendered message:
     * [prefix + name + suffix] + [formatted chat message]
     */
    public Component renderBaseMessage(Player sender, Component messageComponent) {
        Component prefix = buildPrefixComponent(sender);
        Component chat = buildChatComponent(sender, messageComponent);
        return prefix.append(chat);
    }


    private Component buildPrefixComponent(Player player) {
        ChatFormat playerFormat = registry.getPlayerFormat(player);

        // Stars
        int starTier = StarTierModifier.getStarTier(player);
        String starTierFormat = StarTierModifier.getStarTierFormat(starTier);

        // Prefix
        String rank = playerFormat.getPrefix();
        String prefix = starTierFormat + rank;
        String prefixCommand = playerFormat.getPreClickCmd();
        String prefixTooltip = listToString(playerFormat.getPrefixTooltip()).replace("<star_tier>", String.valueOf(starTier));
        String processed_prefix = "<click:run_command:'" + prefixCommand + "'><hover:show_text:'" + prefixTooltip + "'>" + prefix + "</hover></click>";

        // Name
        String name = playerFormat.getName();
        String nameCommand = playerFormat.getNameClickCmd();
        String nameTooltip = listToString(playerFormat.getNameTooltip());
        String processed_name = "<click:suggest_command:'" + nameCommand + "'><hover:show_text:'" + nameTooltip + "'>" + name + "</hover></click>";

        // Suffix
        String suffix = playerFormat.getSuffix();

        // Combine
        String chatLayout = processed_prefix + processed_name + suffix;

        // Parse
        chatLayout = TextFormatter.convert(chatLayout)
                .expect(TextFormatter.InputFormat.ANY)
                .preprocess(s -> {
                    s = PlaceholderAPIHook.applyPlaceholders(s, player);
                    return s;
                })
                .toMiniMessage();

        return ComponentFormatter.deserialize(chatLayout)
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(ComponentFormatter.Feature.CLICK,
                        ComponentFormatter.Feature.HOVER,
                        ComponentFormatter.Feature.COLORS,
                        ComponentFormatter.Feature.DECORATIONS,
                        ComponentFormatter.Feature.RESET,
                        ComponentFormatter.Feature.GRADIENT)
                .toComponent();
    }

    /**
     * Chat message component (colors/formatting/url handling gated by perms).
     */
    private Component buildChatComponent(Player player, Component messageComponent) {
        String rawMessage = ComponentFormatter.serialize(messageComponent).format(ComponentFormatter.Serializer.Format.PLAIN).build();

        // Admin permissions for chat formatting
        if (player.hasPermission("deluxechat.interactive")) {
            return ComponentFormatter.deserialize(rawMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .features(ComponentFormatter.Feature.CLICK,
                            ComponentFormatter.Feature.HOVER,
                            ComponentFormatter.Feature.COLORS,
                            ComponentFormatter.Feature.DECORATIONS,
                            ComponentFormatter.Feature.RESET,
                            ComponentFormatter.Feature.GRADIENT,
                            ComponentFormatter.Feature.RAINBOW)
                    .autoLinkUrls(true)
                    .toComponent();
        // Formatting permissions for chat formatting with colors and decorations
        } else if (player.hasPermission("deluxechat.formatting")) {
            return ComponentFormatter.deserialize(rawMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .features(ComponentFormatter.Feature.COLORS,
                            ComponentFormatter.Feature.DECORATIONS,
                            ComponentFormatter.Feature.RESET,
                            ComponentFormatter.Feature.GRADIENT,
                            ComponentFormatter.Feature.RAINBOW)
                    .autoLinkUrls(true)
                    .toComponent();
        // Formatting permissions for chat formatting with colors only
        } else if (player.hasPermission("deluxechat.color")) {
            return ComponentFormatter.deserialize(rawMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .features(ComponentFormatter.Feature.COLORS,
                            ComponentFormatter.Feature.RESET,
                            ComponentFormatter.Feature.GRADIENT,
                            ComponentFormatter.Feature.RAINBOW)
                    .autoLinkUrls(true)
                    .toComponent();
        // No formatting permissions, only url auto-linking
        } else {
            return ComponentFormatter.deserialize(rawMessage)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .autoLinkUrls(true)
                    .toComponent();
        }
    }

    private String listToString(List<String> strings) {
        StringBuilder sb = new StringBuilder();
        int length = strings.size();
        int index = 0;
        for (String str : strings) {
            sb.append(str);
            if (index < length - 1) {
                sb.append("\n<reset>");
            }
            index++;
        }
        return sb.toString();
    }
}
