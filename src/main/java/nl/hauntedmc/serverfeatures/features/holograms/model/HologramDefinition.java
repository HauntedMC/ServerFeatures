package nl.hauntedmc.serverfeatures.features.holograms.model;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.Optional;

public record HologramDefinition(
        String id, String worldName, double x, double y, double z, float yaw, float pitch,
        Display.Billboard billboard, TextDisplay.TextAlignment alignment, int lineWidth,
        boolean seeThrough, boolean shadowed, boolean useDefaultBackground,
        Integer backgroundARGB, boolean glow, Integer glowColorARGB, Float viewRange,
        Integer brightnessBlock, Integer brightnessSky
) {
    /* --- Compact canonical constructor: validate/normalize components --- */
    public HologramDefinition {
        // normalize width
        lineWidth = Math.max(0, lineWidth);

        // clamp brightness values to 0..15 if present
        if (brightnessBlock != null) brightnessBlock = clampLightLevel(brightnessBlock);
        if (brightnessSky != null) brightnessSky = clampLightLevel(brightnessSky);
    }

    /* --- Convenience ctor: accepts String ARGBs and delegates to canonical --- */
    public HologramDefinition(
            String id,
            String worldName,
            double x, double y, double z,
            float yaw, float pitch,
            Display.Billboard billboard,
            TextDisplay.TextAlignment alignment,
            int lineWidth,
            boolean seeThrough,
            boolean shadowed,
            boolean useDefaultBackground,
            String backgroundARGB,            // String in
            boolean glow,
            String glowColorARGB,             // String in
            Float viewRange,
            Integer brightnessBlock,
            Integer brightnessSky
    ) {
        this(
                id, worldName, x, y, z, yaw, pitch,
                billboard, alignment,
                lineWidth, seeThrough, shadowed, useDefaultBackground,
                parseARGB(backgroundARGB),        // -> Integer
                glow,
                parseARGB(glowColorARGB),         // -> Integer
                viewRange,
                brightnessBlock,
                brightnessSky
        );
    }

    public Optional<World> resolveWorld(Server server) {
        return Optional.ofNullable(server.getWorld(worldName));
    }

    /* --- Helpers --- */
    public static Integer parseARGB(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) return (0xFF << 24) | Integer.parseInt(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static Integer parseInt(Object v, Integer def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static int clampLightLevel(int v) {
        return Math.max(0, Math.min(15, v));
    }
}
