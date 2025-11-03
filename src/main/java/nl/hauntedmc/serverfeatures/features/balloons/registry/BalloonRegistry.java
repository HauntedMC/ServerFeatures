package nl.hauntedmc.serverfeatures.features.balloons.registry;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigService;
import nl.hauntedmc.serverfeatures.api.io.config.ConfigView;
import nl.hauntedmc.serverfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.serverfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.serverfeatures.features.balloons.Balloons;
import nl.hauntedmc.serverfeatures.features.balloons.model.BalloonDefinition;
import org.bukkit.Material;

import java.util.*;

/**
 * Loads balloons from local/balloons.yml (root key: "balloons"), using the unified config API.
 */
public final class BalloonRegistry {

    private final Balloons feature;
    private final ConfigView store; // points at local/balloons.yml
    private final Map<String, BalloonDefinition> byId = new LinkedHashMap<>();

    public BalloonRegistry(Balloons feature) {
        this.feature = feature;
        this.store = new ConfigService(feature.getPlugin()).view("local/balloons.yml", /* copyDefaultsIfPresent */ true);
    }

    public void reloadFromConfig() {
        byId.clear();

        ConfigNode root = store.node("balloons");
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
        return ComponentFormatter
                .deserialize(s)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .features(ComponentFormatter.ALL_DEFAULTS())
                .toComponent();
    }
}
