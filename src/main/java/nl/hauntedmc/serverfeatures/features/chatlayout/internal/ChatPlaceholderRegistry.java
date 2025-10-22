package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Loads chat placeholders from config and resolves them against localization.
 * Config layout:
 * placeholders:
 *   ping: "[ping]"
 * Localization keys:
 * - chatlayout.placeholders.<key>.replacetext
 * - chatlayout.placeholders.<key>.description
 * - chatlayout.placeholders.hover
 */
public final class ChatPlaceholderRegistry {

    private final ChatLayout feature;
    /** token -> mainKey */
    private final Map<String, String> tokenToKey = new LinkedHashMap<>();
    /** Precompiled regex for fast replace-all */
    private final Map<String, Pattern> tokenPatterns = new HashMap<>();

    public ChatPlaceholderRegistry(ChatLayout feature) {
        this.feature = feature;
        reload();
    }

    public void reload() {
        tokenToKey.clear();
        tokenPatterns.clear();

        ConfigNode root = feature.getConfigHandler().node("placeholders");
        Map<String, ConfigNode> children = root.children();
        if (children.isEmpty()) return;

        // Preserve user order by iterating entrySet (ConfigNode children likely a LinkedHashMap)
        for (Map.Entry<String, ConfigNode> e : children.entrySet()) {
            String mainKey = e.getKey();
            String token = e.getValue().as(String.class, null);
            if (token == null || token.isBlank()) continue;
            tokenToKey.put(token, mainKey);
            tokenPatterns.put(token, Pattern.compile(Pattern.quote(token)));
        }
    }

    /**
     * Replace all known chat placeholders in the raw message with a localized, hoverable block.
     * Called after mentions parsing.
     */
    public String applyPlaceholders(Player sender, String raw) {
        if (raw == null || raw.isEmpty() || tokenToKey.isEmpty()) return raw;

        String result = raw;
        for (Map.Entry<String, String> entry : tokenToKey.entrySet()) {
            String token = entry.getKey();
            String key = entry.getValue();

            String replacement = buildReplacement(sender, key);

            Pattern p = tokenPatterns.get(token);
            result = p.matcher(result).replaceAll(replacement);
        }
        return result;
    }

    /**
     * Build the MiniMessage/Mixed string for one placeholder, with a hover that combines
     * description and the global verified hover text.
     */
    private String buildReplacement(Player sender, String mainKey) {
        // replacetext component
        Component replaceComp = feature.getLocalizationHandler()
                .getMessage("chatlayout.placeholders." + mainKey + ".replacetext")
                .forAudience(sender)
                .build();

        // global hover tail: ✓ Geverifieerd bericht
        Component verifiedComp = feature.getLocalizationHandler()
                .getMessage("chatlayout.placeholders.hover")
                .forAudience(sender)
                .build();

        // Serialize to MiniMessage-like mixed text so your existing ComponentFormatter
        // pipeline can preserve colors/formatting.
        String replaceText = ComponentFormatter.serialize(replaceComp)
                .format(ComponentFormatter.Serializer.Format.MINIMESSAGE)
                .build();
        String verifiedText = ComponentFormatter.serialize(verifiedComp)
                .format(ComponentFormatter.Serializer.Format.MINIMESSAGE)
                .build();

        // Escape single quotes for MiniMessage attribute
        String hoverEscaped = verifiedText
                // Escape single quotes for MiniMessage attribute
                .replace("'", "\\'");

        // Wrap replacetext with hover
        return "<hover:show_text:'" + hoverEscaped + "'>" + replaceText + "</hover>";
    }


    /** Public, ordered view for commands/UI. */
    public java.util.List<PlaceholderInfo> getAll() {
        java.util.List<PlaceholderInfo> list = new java.util.ArrayList<>(tokenToKey.size());
        for (java.util.Map.Entry<String, String> e : tokenToKey.entrySet()) {
            list.add(new PlaceholderInfo(e.getKey(), e.getValue()));
        }
        return java.util.Collections.unmodifiableList(list);
    }

    /**
     * Token + main key pair
     */
        public record PlaceholderInfo(String token, String key) {
    }
}
