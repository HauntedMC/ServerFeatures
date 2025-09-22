package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/** Static, single-color glow effect. */
public final class StaticColorGlow implements GlowEffect {

    private final NamedTextColor color;
    private final String id;
    private final String permission;
    private final Material material;
    private final Component displayName;

    private static final Map<NamedTextColor, Material> COLOR_MATERIALS = Map.ofEntries(
            Map.entry(NamedTextColor.BLACK, Material.BLACK_CONCRETE),
            Map.entry(NamedTextColor.DARK_BLUE, Material.BLUE_CONCRETE),
            Map.entry(NamedTextColor.DARK_GREEN, Material.GREEN_CONCRETE),
            Map.entry(NamedTextColor.DARK_AQUA, Material.CYAN_CONCRETE),
            Map.entry(NamedTextColor.DARK_RED, Material.RED_CONCRETE),
            Map.entry(NamedTextColor.DARK_PURPLE, Material.PURPLE_CONCRETE),
            Map.entry(NamedTextColor.GOLD, Material.ORANGE_CONCRETE),
            Map.entry(NamedTextColor.GRAY, Material.LIGHT_GRAY_CONCRETE),
            Map.entry(NamedTextColor.DARK_GRAY, Material.GRAY_CONCRETE),
            Map.entry(NamedTextColor.BLUE, Material.LIGHT_BLUE_CONCRETE),
            Map.entry(NamedTextColor.GREEN, Material.LIME_CONCRETE),
            Map.entry(NamedTextColor.AQUA, Material.LIGHT_BLUE_CONCRETE),
            Map.entry(NamedTextColor.RED, Material.RED_CONCRETE),
            Map.entry(NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_CONCRETE),
            Map.entry(NamedTextColor.YELLOW, Material.YELLOW_CONCRETE),
            Map.entry(NamedTextColor.WHITE, Material.WHITE_CONCRETE)
    );

    public StaticColorGlow(NamedTextColor color) {
        this.color = color;
        String base = color.toString().toLowerCase(Locale.ROOT);
        this.id = base;
        this.permission = "serverfeatures.feature.glow.color." + base;
        this.material = COLOR_MATERIALS.getOrDefault(color, Material.WHITE_CONCRETE);
        this.displayName = Component.text(pretty(base));
    }

    @Override public String id() { return id; }
    @Override public Component displayName(Player viewer) { return displayName; }
    @Override public String permission() { return permission; }
    @Override public NamedTextColor colorAt(Player player, long elapsedSeconds) { return color; }
    @Override public Material menuMaterial() { return material; }

    private static String pretty(String raw) {
        String[] parts = raw.replace('_',' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)))
              .append(p.substring(1))
              .append(' ');
        }
        return sb.toString().trim();
    }
}
