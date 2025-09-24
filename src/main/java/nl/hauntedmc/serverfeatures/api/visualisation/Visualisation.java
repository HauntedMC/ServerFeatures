package nl.hauntedmc.serverfeatures.api.visualisation;

import nl.hauntedmc.serverfeatures.api.visualisation.options.VisualOptions;
import nl.hauntedmc.serverfeatures.api.visualisation.shape.RegionShape;
import org.bukkit.entity.Player;

/**
 * Renders a {@link RegionShape} for a single viewer.
 * Implementations decide how to visualise (e.g., Bukkit Display entities, particles, maps, etc.).
 */
public interface Visualisation {

    /**
     * Render the given shape with the provided options for a single player.
     * Returns a handle that can be used to clear the visualisation.
     */
    VisualHandle show(Player viewer, RegionShape shape, VisualOptions options);
}
