package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.effect.sound.SoundProfile;
import nl.hauntedmc.serverfeatures.api.hook.PlaceholderAPIHook;
import nl.hauntedmc.serverfeatures.api.ui.hud.toast.ToastAPI;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import nl.hauntedmc.serverfeatures.features.chatlayout.internal.util.StarTierModifier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatHandler {

    private final ChatFormatRegistry registry;
    private final Map<Player, Long> mentionCooldownMap = new HashMap<>();
    private final ChatPlaceholderRegistry placeholderRegistry;
    private final Boolean mentionsEnabled;
    private final Long mentionsCooldown;
    private final ChatLayout feature;


    public ChatHandler(ChatLayout feature, ChatFormatRegistry registry, ChatPlaceholderRegistry placeholderRegistry) {
        this.feature = feature;
        this.registry = registry;
        this.placeholderRegistry = placeholderRegistry;
        this.mentionsEnabled = feature.getConfigHandler().getSetting("mention.enabled", Boolean.class);
        this.mentionsCooldown = feature.getConfigHandler().getSetting("mention.cooldown_seconds", Long.class);
    }

    /**
     * Builds the full rendered message:
     * [prefix + name + suffix] + [formatted chat message]
     */
    public Component renderBaseMessage(Player sender, Component messageComponent) {
        String rawMessage = ComponentFormatter.serialize(messageComponent).format(ComponentFormatter.Serializer.Format.PLAIN).build();


        if (mentionsEnabled) {
            rawMessage = parseMentions(sender, rawMessage);
        }

        rawMessage = placeholderRegistry.applyPlaceholders(sender, rawMessage);

        Component prefix = buildPrefixComponent(sender);
        Component chat = buildChatComponent(sender, rawMessage);
        return prefix.append(chat);
    }

    /**
     * Detect mentions in the message (e.g., @<playername>) and highlight them.
     * It will also handle sending a toast message to the mentioned player.
     */
    private String parseMentions(Player sender, String rawMessage) {
        // Split the message by spaces to detect mentions
        String[] words = rawMessage.split(" ");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            // Check if the word starts with '@' (indicating a mention)
            if (word.startsWith("@")) {
                String playerName = word.substring(1);  // Get the player name after '@'

                Player mentionedPlayer = Bukkit.getPlayer(playerName);  // Get the player object

                if (mentionedPlayer != null && mentionedPlayer.isOnline()) {
                    // Format the mention as a clickable and colored tag
                    String formattedMention = "<color:aqua>" + "@" + playerName + "</color>";

                    // Replace the word with the formatted mention in the message
                    words[i] = formattedMention;

                    // Send the mention Toast to the mentioned player
                    handleMention(sender, mentionedPlayer);
                }
            }
        }

        // Join the words back together into a single message string
        return String.join(" ", words);
    }

    /**
     * Send a toast message to the mentioned player with cooldown.
     */
    private void handleMention(Player sender, Player mentionedPlayer) {
        // Check cooldown for the mentioned player
        long lastMentionTime = mentionCooldownMap.getOrDefault(mentionedPlayer, 0L);
        long currentTime = System.currentTimeMillis();
        long cooldownInMillis = this.mentionsCooldown * 1000;

        if (currentTime - lastMentionTime >= cooldownInMillis) {
            Component toastMessage = feature.getLocalizationHandler().getMessage("chatlayout.mention.toast_title")
                    .with("player", sender.getName())
                    .forAudience(mentionedPlayer)
                    .build();

            ToastAPI.showToast(
                    feature.getPlugin(),
                    mentionedPlayer,
                    ComponentFormatter.serialize(toastMessage).format(ComponentFormatter.Serializer.Format.JSON).build(),
                    Material.BELL,
                    ToastAPI.Frame.GOAL,
                    40L,
                    SoundProfile.of(Sound.BLOCK_NOTE_BLOCK_PLING,
                            SoundCategory.UI,
                            0.8f,
                            2.2f)
            );

            mentionCooldownMap.put(mentionedPlayer, currentTime);
        }
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
    private Component buildChatComponent(Player player, String rawMessage) {

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

    public ChatPlaceholderRegistry getPlaceholderRegistry() {
        return placeholderRegistry;
    }
}
