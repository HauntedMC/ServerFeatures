package nl.hauntedmc.serverfeatures.features.holograms.internal;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.holograms.Holograms;
import nl.hauntedmc.serverfeatures.features.holograms.model.HologramDefinition;
import nl.hauntedmc.serverfeatures.features.holograms.registry.HologramRegistry;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns/removes TextDisplay entities and applies the appearance from HologramDefinition.
 * Uses pre-resolved, cached lines from HologramRegistry.
 */
public final class HologramHandler {

    private final Holograms feature;
    private final HologramRegistry registry;
    private final Map<String, List<UUID>> spawned = new ConcurrentHashMap<>();

    public HologramHandler(Holograms feature, HologramRegistry registry) {
        this.feature = feature;
        this.registry = registry;
    }

    /** Remove any existing and spawn all, logging missing worlds. */
    public void spawnAllSafe() {
        removeAll();
        for (HologramDefinition def : registry.all()) {
            Optional<World> w = def.resolveWorld(feature.getPlugin().getServer());
            if (w.isEmpty()) {
                continue;
            }
            spawn(def, w.get());
        }
    }

    public void removeAll() {
        for (List<UUID> list : spawned.values()) {
            for (UUID id : list) {
                var ent = findTextDisplay(id);
                if (ent != null && !ent.isDead()) ent.remove();
            }
        }
        spawned.clear();
    }

    private void spawn(HologramDefinition def, World world) {
        Component text = registry.joinedText(def.id);
        Location loc = new Location(world, def.x, def.y, def.z, def.yaw, def.pitch);

        TextDisplay td = world.spawn(loc, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(def.billboard);
            d.setAlignment(def.alignment);
            if (def.lineWidth > 0) d.setLineWidth(def.lineWidth);

            d.setSeeThrough(def.seeThrough);
            d.setShadowed(def.shadowed);

            if (def.useDefaultBackground) {
                d.setDefaultBackground(true);
            } else {
                d.setDefaultBackground(false);
                if (def.backgroundARGB != null) d.setBackgroundColor(argbToColor(def.backgroundARGB));
            }

            d.setGlowing(def.glow);
            if (def.glow && def.glowColorARGB != null) {
                try { d.setGlowColorOverride(argbToColor(def.glowColorARGB)); } catch (Throwable ignored) {}
            }

            if (def.viewRange != null) { try { d.setViewRange(def.viewRange); } catch (Throwable ignored) {} }
            if (def.brightnessBlock != null || def.brightnessSky != null) {
                try {
                    int block = clamp(def.brightnessBlock, 0, 15, 0);
                    int sky = clamp(def.brightnessSky, 0, 15, 0);
                    d.setBrightness(new Display.Brightness(block, sky));
                } catch (Throwable ignored) {}
            }

            d.setPersistent(false);
        });

        spawned.computeIfAbsent(def.id, k -> new ArrayList<>()).add(td.getUniqueId());
    }

    public void remove(String hologramId) {
        List<UUID> uuids = spawned.remove(hologramId);
        if (uuids == null) return;
        for (UUID id : uuids) {
            var e = findTextDisplay(id);
            if (e != null && !e.isDead()) e.remove();
        }
    }

    private TextDisplay findTextDisplay(UUID id) {
        var e = feature.getPlugin().getServer().getEntity(id);
        return (e instanceof TextDisplay td) ? td : null;
    }

    private static Color argbToColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;
        return Color.fromARGB(a, r, g, b);
    }

    private static int clamp(Integer v, int min, int max, int def) {
        if (v == null) return def;
        return Math.max(min, Math.min(max, v));
    }
}
