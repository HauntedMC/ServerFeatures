package nl.hauntedmc.serverfeatures.features.chatlayout.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import nl.hauntedmc.serverfeatures.features.chatlayout.ChatLayout;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import org.bukkit.entity.Player;

import java.util.*;

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

    public ChatPlaceholderRegistry(ChatLayout feature) {
        this.feature = feature;
        reload();
    }

    public void reload() {
        tokenToKey.clear();

        ConfigNode root = feature.getConfigHandler().node("placeholders");
        Map<String, ConfigNode> children = root.children();
        if (children.isEmpty()) return;

        // Preserve user order by iterating entrySet (ConfigNode children likely a LinkedHashMap)
        for (Map.Entry<String, ConfigNode> e : children.entrySet()) {
            String mainKey = e.getKey();
            String token = e.getValue().as(String.class, null);
            if (token == null || token.isBlank()) continue;
            tokenToKey.put(token, mainKey);
        }
    }

    /**
     * Replace all known chat placeholders in the raw message with a localized, hoverable block.
     * Called after mentions parsing.
     */
    public Component applyPlaceholders(Player sender, Component base) {
        Component out = base;
        for (Map.Entry<String, String> e : tokenToKey.entrySet()) {
            String token = e.getKey(); // e.g., "[ping]"
            String key   = e.getValue(); // "ping"

            Component replacement = buildReplacement(sender, key);
            out = out.replaceText(cfg -> cfg
                    .matchLiteral(token)
                    .replacement(replacement));
        }
        return out;
    }

    /**
     * Build the MiniMessage/Mixed string for one placeholder, with a hover that combines
     * description and the global verified hover text.
     */
    private Component buildReplacement(Player sender, String mainKey) {
        // Localized visible text
        Component visible = feature.getLocalizationHandler()
                .getMessage("chatlayout.placeholders." + mainKey + ".replacetext")
                .forAudience(sender)
                .build();

        // Localized hover tail (e.g., ✓ Geverifieerd bericht)
        Component hover = feature.getLocalizationHandler()
                .getMessage("chatlayout.placeholders.hover")
                .forAudience(sender)
                .build();

        // Attach hover — add click if you want different tokens to be clickable later
        return visible.hoverEvent(HoverEvent.showText(hover));
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
