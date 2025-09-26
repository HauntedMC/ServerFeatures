package nl.hauntedmc.serverfeatures.features.balloons.registry;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.balloons.Balloons;
import nl.hauntedmc.serverfeatures.features.balloons.model.BalloonDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Loads balloons from top-level config:
 *  enabled: true
 *  balloons:
 *    <id>:
 *      permission: ...
 *      item: MATERIAL | head: <base64>
 *      displayname: "§bSome Name"
 * Notes:
 * - Accepts Bukkit ConfigurationSection (MemorySection) or a plain Map.
 * - We use the "displayname" key (but accept "display_name" as fallback).
 * - If both "item" and "head" exist, "item" takes precedence.
 */
public final class BalloonRegistry {

    private final Balloons feature;
    private final Map<String, BalloonDefinition> byId = new LinkedHashMap<>();

    public BalloonRegistry(Balloons feature) {
        this.feature = feature;
    }

    public void reloadFromConfig() {
        byId.clear();

        Object balloonsObj = feature.getConfigHandler().getSetting("balloons");
        feature.getLogger().info("Type of balloons config: " + (balloonsObj == null ? "null" : balloonsObj.getClass().getName()));

        if (balloonsObj instanceof ConfigurationSection section) {
            loadFromSection(section);
            return;
        }
        feature.getLogger().warning("No balloons configured (unsupported type).");
    }

    private void loadFromSection(ConfigurationSection section) {
        int count = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection def = section.getConfigurationSection(id);
            if (def == null) {
                feature.getLogger().warning("[Balloons] Skipping '" + id + "': not a section");
                continue;
            }

            String permission = def.getString("permission", "serverfeatures.feature.balloons." + id);

            // Prefer "displayname"; support "display_name" as fallback
            String display = def.getString("displayname");

            Material itemMat = null;
            String itemStr = def.getString("item");
            if (itemStr != null && !itemStr.isBlank()) {
                try {
                    itemMat = Material.valueOf(itemStr.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    feature.getLogger().warning("Invalid material for '" + id + "': " + itemStr);
                }
            }

            Integer cmd = def.isInt("custom_model_data") ? def.getInt("custom_model_data") : null;
            String head = def.getString("head");

            BalloonDefinition bd = new BalloonDefinition(
                    id,
                    permission,
                    Component.text(display),
                    itemMat,
                    cmd,
                    head
            );
            byId.put(id.toLowerCase(Locale.ROOT), bd);
            count++;
        }
        feature.getLogger().info("Loaded " + count + " balloon(s) from ConfigurationSection.");
    }

    public List<BalloonDefinition> all() {
        return List.copyOf(byId.values());
    }

    public Optional<BalloonDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }
}
