package nl.hauntedmc.serverfeatures.features.balloons.registry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.balloons.Balloons;
import nl.hauntedmc.serverfeatures.features.balloons.model.BalloonDefinition;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads balloons from feature config
 */
public final class BalloonRegistry {

    private final Balloons feature;
    private final Map<String, BalloonDefinition> byId = new LinkedHashMap<>();

    public BalloonRegistry(Balloons feature) {
        this.feature = feature;
    }

    public void reloadFromConfig() {
        byId.clear();

        ConfigNode root = feature.getConfigHandler().node("balloons");
        Map<String, ConfigNode> children = root.children();
        if (children.isEmpty()) {
            feature.getLogger().warning("No balloons configured.");
            return;
        }

        int count = 0;
        for (Map.Entry<String, ConfigNode> entry : children.entrySet()) {
            final String rawId = entry.getKey();
            final String id = rawId.toLowerCase(Locale.ROOT);
            ConfigNode n = entry.getValue();

            String permission = n.get("permission").as(String.class, "serverfeatures.feature.balloons." + id);

            // Prefer "displayname"; support "display_name" as fallback
            String displayStr = Optional.ofNullable(n.get("displayname").as(String.class, null))
                    .orElseGet(() -> n.get("display_name").as(String.class, null));

            // Item material (takes precedence over head)
            Material itemMat = null;
            String itemStr = n.get("item").as(String.class, null);
            if (itemStr != null && !itemStr.isBlank()) {
                itemMat = Material.matchMaterial(itemStr);
                if (itemMat == null) {
                    feature.getLogger().warning("Invalid material for '" + rawId + "': " + itemStr);
                }
            }

            Integer customModelData = n.get("custom_model_data").as(Integer.class, null);
            String head = n.get("head").as(String.class, null);

            BalloonDefinition def = new BalloonDefinition(
                    id,
                    permission,
                    toComponent(displayStr != null ? displayStr : rawId),
                    itemMat,
                    customModelData,
                    head
            );
            byId.put(id, def);
            count++;
        }

        feature.getLogger().info("Loaded " + count + " balloon(s).");
    }

    public List<BalloonDefinition> all() {
        return List.copyOf(byId.values());
    }

    public Optional<BalloonDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    // -----------------
    // helpers
    // -----------------

    private static Component toComponent(String s) {
        if (s == null || s.isBlank()) return Component.empty();
        // Support both '&' and '§' style color codes. Fallback to plain text.
        if (s.indexOf('§') >= 0) return LegacyComponentSerializer.legacySection().deserialize(s);
        if (s.indexOf('&') >= 0) return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
        return Component.text(s);
    }
}
