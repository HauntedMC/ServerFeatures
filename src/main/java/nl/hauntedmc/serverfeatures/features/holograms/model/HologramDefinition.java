package nl.hauntedmc.serverfeatures.features.holograms.model;

import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.Locale;
import java.util.Optional;

public final class HologramDefinition {

    public final String id;
    public final String worldName;
    public final double x, y, z;
    public final float yaw, pitch;

    public final Display.Billboard billboard;
    public final TextDisplay.TextAlignment alignment;
    public final int lineWidth; // pixels; 0 = no wrapping

    public final boolean seeThrough;
    public final boolean shadowed;
    public final boolean useDefaultBackground;
    public final Integer backgroundARGB; // nullable

    public final boolean glow;
    public final Integer glowColorARGB;   // nullable

    public final Float viewRange;         // nullable
    public final Integer brightnessBlock; // 0..15 nullable
    public final Integer brightnessSky;   // 0..15 nullable

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
            Integer backgroundARGB,
            boolean glow,
            Integer glowColorARGB,
            Float viewRange,
            Integer brightnessBlock,
            Integer brightnessSky
    ) {
        this.id = id;
        this.worldName = worldName;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
        this.billboard = billboard;
        this.alignment = alignment;
        this.lineWidth = Math.max(0, lineWidth);
        this.seeThrough = seeThrough;
        this.shadowed = shadowed;
        this.useDefaultBackground = useDefaultBackground;
        this.backgroundARGB = backgroundARGB;
        this.glow = glow;
        this.glowColorARGB = glowColorARGB;
        this.viewRange = viewRange;
        this.brightnessBlock = brightnessBlock;
        this.brightnessSky = brightnessSky;
    }

    public static Display.Billboard parseBillboard(Object v, Display.Billboard def) {
        if (v == null) return def;
        try { return Display.Billboard.valueOf(String.valueOf(v).toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return def; }
    }

    public static TextDisplay.TextAlignment parseAlignment(Object v, TextDisplay.TextAlignment def) {
        if (v == null) return def;
        try { return TextDisplay.TextAlignment.valueOf(String.valueOf(v).toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return def; }
    }

    public static Integer parseARGB(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        try {
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) return (0xFF << 24) | Integer.parseInt(s, 16);
            if (s.length() == 8) return (int)Long.parseLong(s, 16);
        } catch (Exception ignored) {}
        return null;
    }

    public static Integer parseInt(Object v, Integer def) {
        if (v == null) return def;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }
    public static Float parseFloat(Object v, Float def) {
        if (v == null) return def;
        try { return Float.parseFloat(String.valueOf(v)); } catch (Exception e) { return def; }
    }
    public static Double parseDouble(Object v, Double def) {
        if (v == null) return def;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    public Optional<World> resolveWorld(org.bukkit.Server server) {
        return Optional.ofNullable(server.getWorld(worldName));
    }
}
