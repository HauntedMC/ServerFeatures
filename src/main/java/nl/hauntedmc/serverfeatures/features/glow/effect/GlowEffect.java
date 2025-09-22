package nl.hauntedmc.serverfeatures.features.glow.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * A glow effect describes how a player's glow should behave.
 * Implementations can be static (single color) or animated (sequence over time).
 */
public interface GlowEffect {

    /** Stable, unique identifier (machine-friendly). Example: "red", "rainbow". */
    String id();

    /** Player-facing name, e.g., "Red", "Rainbow". */
    Component displayName(Player viewer);

    /** Specific permission string for this effect. */
    String permission();

    /** Whether the effect is animated over time. */
    default boolean isAnimated() { return false; }

    /**
     * Color to apply after a number of elapsed seconds since the effect
     * was activated for the player. Implementations should be deterministic.
     */
    NamedTextColor colorAt(Player player, long elapsedSeconds);

    /**
     * Icon used for the effect in selection menus.
     * Prefer a visually-related material.
     */
    Material menuMaterial();
}
