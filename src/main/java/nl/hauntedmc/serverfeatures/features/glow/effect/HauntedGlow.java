package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Animated glow that rotates every second through the default color list.
 */
public final class HauntedGlow implements GlowEffect {

    private static final List<NamedTextColor> SEQUENCE = List.of(
            NamedTextColor.GOLD,
            NamedTextColor.AQUA
    );

    private static final Component NAME = Component.text("HauntedMC");
    private static final String PERM = "serverfeatures.feature.glow.effect.hauntedmc";

    @Override
    public String id() {
        return "hauntedmc";
    }

    @Override
    public Component displayName(Player viewer) {
        return NAME;
    }

    @Override
    public String permission() {
        return PERM;
    }

    @Override
    public boolean isAnimated() {
        return true;
    }

    @Override
    public NamedTextColor colorAt(Player player, long elapsedSeconds) {
        int idx = (int) (elapsedSeconds % SEQUENCE.size());
        return SEQUENCE.get(idx);
    }

    @Override
    public Material menuMaterial() {
        // Distinctive icon suggesting "special/animated"
        return Material.GHAST_SPAWN_EGG;
    }
}
