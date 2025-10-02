package nl.hauntedmc.serverfeatures.features.holograms.registry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.serverfeatures.config.ConfigNode;
import nl.hauntedmc.serverfeatures.features.holograms.Holograms;
import nl.hauntedmc.serverfeatures.features.holograms.model.HologramDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.*;

/**
 * Loads hologram definitions
 */
public final class HologramRegistry {

    private static final String END_MARKER = "<end>";

    private final Holograms feature;

    private final Map<String, HologramDefinition> byId = new LinkedHashMap<>();
    private final Map<String, List<Component>> cachedLines = new LinkedHashMap<>();

    public HologramRegistry(Holograms feature) {
        this.feature = feature;
        reload();
    }

    public void reload() {
        byId.clear();
        cachedLines.clear();

        // New: use typed ConfigNode API
        ConfigNode root = feature.getConfigHandler().node("holograms");
        Map<String, ConfigNode> children = root.children();
        if (children.isEmpty()) {
            feature.getLogger().warning("No 'holograms' section found or empty");
            return;
        }

        // Parse each hologram node
        for (Map.Entry<String, ConfigNode> entry : children.entrySet()) {
            String id = entry.getKey();
            ConfigNode n = entry.getValue();

            String world = n.get("world").as(String.class, "world");
            double x = n.get("x").as(Double.class, 0.0D);
            double y = n.get("y").as(Double.class, 64.0D);
            double z = n.get("z").as(Double.class, 0.0D);
            float yaw = n.get("yaw").as(Float.class, 0.0F);
            float pitch = n.get("pitch").as(Float.class, 0.0F);
            Display.Billboard billboard = n.get("billboard").as(Display.Billboard.class, Display.Billboard.CENTER);
            TextDisplay.TextAlignment align = n.get("alignment").as(TextDisplay.TextAlignment.class, TextDisplay.TextAlignment.CENTER);
            int lineWidth = n.get("line_width").as(Integer.class, 0);

            boolean seeThrough = n.get("see_through").as(Boolean.class, false);
            boolean shadowed = n.get("shadowed").as(Boolean.class, true);
            boolean useDefaultBg = n.get("use_default_background").as(Boolean.class, true);
            String bgColor = n.get("background_color").as(String.class, null); // ARGB int or null

            boolean glow = n.get("glow").as(Boolean.class, false);
            String glowColor = n.get("glow_color").as(String.class, null); // ARGB int or null

            Float viewRange = n.get("view_range").as(Float.class, null);

            // brightness:
            Integer brightBlock, brightSky;
            ConfigNode bsec = n.get("brightness");
            brightBlock = bsec.get("block").as(Integer.class, null);
            brightSky = bsec.get("sky").as(Integer.class, null);

            HologramDefinition hd = new HologramDefinition(
                    id, world, x, y, z, yaw, pitch,
                    billboard, align, lineWidth, seeThrough, shadowed, useDefaultBg, bgColor,
                    glow, glowColor, viewRange, brightBlock, brightSky
            );
            byId.put(id.toLowerCase(Locale.ROOT), hd);
        }

        // Resolve & cache lines from MessageMap once
        resolveAllLines();

        feature.getLogger().info("Loaded " + byId.size() + " hologram definitions; cached lines for " + cachedLines.size() + ".");
    }

    /**
     * Build and cache lines from message keys holograms.hologram.<id>.0..N
     */
    private void resolveAllLines() {
        var lh = feature.getLocalizationHandler();

        for (HologramDefinition def : byId.values()) {
            List<Component> lines = new ArrayList<>();
            String base = "holograms.hologram." + def.id + ".";

            // Scan sequentially until a gap or <end>; generous cap to avoid runaway.
            boolean stop = false;
            for (int i = 0; i < 256 && !stop; i++) {
                String key = base + i;

                // If the localization returns the key as plain text, treat as missing.
                String plain = lh.getMessage(key).buildPlain();
                if (plain == null || plain.equals(key)) break;

                boolean endsHere = plain.contains(END_MARKER);

                Component c = lh.getMessage(key).build()
                        // strip the <end> marker if present
                        .replaceText(builder -> builder.matchLiteral(END_MARKER).replacement(Component.empty()))
                        // disable italic by default for display readability
                        .decoration(TextDecoration.ITALIC, false);

                lines.add(c);
                if (endsHere) stop = true;
            }

            if (lines.isEmpty()) {
                lines.add(Component.text(def.id).decoration(TextDecoration.ITALIC, false));
            }
            cachedLines.put(def.id.toLowerCase(Locale.ROOT), List.copyOf(lines));
        }
    }

    public Collection<HologramDefinition> all() {
        return List.copyOf(byId.values());
    }

    public Optional<HologramDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns cached line list for an id (never empty).
     */
    public List<Component> lines(String id) {
        return cachedLines.getOrDefault(id.toLowerCase(Locale.ROOT), List.of(Component.text(id)));
    }

    /**
     * Returns the cached lines joined with newline separators.
     */
    public Component joinedText(String id) {
        List<Component> ls = lines(id);
        if (ls.isEmpty()) return Component.empty();
        Component out = Component.empty();
        boolean first = true;
        for (Component c : ls) {
            if (!first) out = out.append(Component.newline());
            out = out.append(c);
            first = false;
        }
        return out;
    }
}
