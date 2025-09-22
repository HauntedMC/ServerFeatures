package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Animated glow that rotates every second through the default color list.
 */
public final class RainbowGlow implements GlowEffect {

    private static final List<NamedTextColor> SEQUENCE = List.of(
            NamedTextColor.RED,
            NamedTextColor.GOLD,
            NamedTextColor.YELLOW,
            NamedTextColor.GREEN,
            NamedTextColor.AQUA,
            NamedTextColor.BLUE,
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.WHITE,
            NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY,
            NamedTextColor.DARK_RED,
            NamedTextColor.DARK_PURPLE,
            NamedTextColor.DARK_BLUE,
            NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_GREEN,
            NamedTextColor.BLACK
    );

    private static final Component NAME = Component.text("Rainbow");
    private static final String PERM = "serverfeatures.feature.glow.effect.rainbow";

    @Override public String id() { return "rainbow"; }
    @Override public Component displayName(Player viewer) { return NAME; }
    @Override public String permission() { return PERM; }
    @Override public boolean isAnimated() { return true; }

    @Override
    public NamedTextColor colorAt(Player player, long elapsedSeconds) {
        int idx = (int) (elapsedSeconds % SEQUENCE.size());
        return SEQUENCE.get(idx);
    }

    @Override
    public Material menuMaterial() {
        // Distinctive icon suggesting "special/animated"
        return Material.BEACON;
    }
}
