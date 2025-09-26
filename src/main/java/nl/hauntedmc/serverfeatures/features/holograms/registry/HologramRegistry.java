package nl.hauntedmc.serverfeatures.features.holograms.registry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.serverfeatures.features.holograms.Holograms;
import nl.hauntedmc.serverfeatures.features.holograms.model.HologramDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.*;

/**
 * Loads hologram definitions from a Bukkit ConfigurationSection (MemorySection) and
 * resolves/caches lines from MessageMap:
 *
 *   holograms.hologram.<id>.0
 *   holograms.hologram.<id>.1
 *   ...
 *
 * Stops either when a key is missing OR when a line contains the "<end>" marker
 * (the marker is removed from the rendered Component).
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

        Object raw = feature.getConfigHandler().getSetting("holograms");
        feature.getLogger().info("[Holograms] type of raw holograms: " + (raw == null ? "null" : raw.getClass().getName()));

        if (!(raw instanceof ConfigurationSection root)) {
            feature.getLogger().warning("[Holograms] No 'holograms' section found or not a MemorySection");
            return;
        }

        // Parse each hologram section
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            String world = sec.getString("world", "world");
            double x = HologramDefinition.parseDouble(sec.get("x"), 0.0D);
            double y = HologramDefinition.parseDouble(sec.get("y"), 64.0D);
            double z = HologramDefinition.parseDouble(sec.get("z"), 0.0D);
            float yaw = HologramDefinition.parseFloat(sec.get("yaw"), 0.0F);
            float pitch = HologramDefinition.parseFloat(sec.get("pitch"), 0.0F);

            Display.Billboard billboard = HologramDefinition.parseBillboard(sec.get("billboard"), Display.Billboard.CENTER);
            TextDisplay.TextAlignment align = HologramDefinition.parseAlignment(sec.get("alignment"), TextDisplay.TextAlignment.CENTER);
            int lineWidth = HologramDefinition.parseInt(sec.get("line_width"), 0);

            boolean seeThrough   = sec.getBoolean("see_through", false);
            boolean shadowed     = sec.getBoolean("shadowed", true);
            boolean useDefaultBg = sec.getBoolean("use_default_background", true);
            Integer bgColor      = HologramDefinition.parseARGB(sec.get("background_color"));

            boolean glow      = sec.getBoolean("glow", false);
            Integer glowColor = HologramDefinition.parseARGB(sec.get("glow_color"));

            Float viewRange = HologramDefinition.parseFloat(sec.get("view_range"), null);

            Integer brightBlock = null, brightSky = null;
            ConfigurationSection bsec = sec.getConfigurationSection("brightness");
            if (bsec != null) {
                brightBlock = HologramDefinition.parseInt(bsec.get("block"), null);
                brightSky   = HologramDefinition.parseInt(bsec.get("sky"), null);
            }

            HologramDefinition hd = new HologramDefinition(
                    id, world, x, y, z, yaw, pitch,
                    billboard, align, lineWidth, seeThrough, shadowed, useDefaultBg, bgColor,
                    glow, glowColor, viewRange, brightBlock, brightSky
            );
            byId.put(id.toLowerCase(Locale.ROOT), hd);
        }

        // Resolve & cache lines from MessageMap once
        resolveAllLines();

        feature.getLogger().info("[Holograms] Loaded " + byId.size() + " hologram definitions; cached lines for " + cachedLines.size() + ".");
    }

    /** Build and cache lines from message keys holograms.hologram.<id>.0..N */
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

    public Collection<HologramDefinition> all() { return List.copyOf(byId.values()); }

    public Optional<HologramDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    /** Returns cached line list for an id (never empty). */
    public List<Component> lines(String id) {
        return cachedLines.getOrDefault(id.toLowerCase(Locale.ROOT), List.of(Component.text(id)));
    }

    /** Returns the cached lines joined with newline separators. */
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
