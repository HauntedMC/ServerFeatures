package nl.hauntedmc.serverfeatures.api.ui.world.display.shape;

import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

/**
 * Abstract geometry for something that can be visualised.
 * Shapes provide corner points (if any), a sampling of edge points given a step,
 * and optionally named points (e.g., "pos1","pos2") for highlighting/labels.
 */
public interface RegionShape {

    /**
     * Centers of "corner" blocks (if applicable for the shape).
     * For shapes without discrete corners (e.g., sphere), return an empty list.
     */
    List<Vector> cornerCenters();

    /**
     * Sample points along edges or outlines to draw dotted lines.
     * The implementor decides which outlines are "edges".
     */
    List<Vector> sampleEdgePoints(double stepBlocks);

    /**
     * Named anchor points that may receive special styling and labels (e.g., pos1/pos2).
     */
    Map<String, Vector> namedPoints();
}
