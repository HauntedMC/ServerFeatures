package nl.hauntedmc.serverfeatures.api.ui.world.display.shape;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidRegionShapeTest {

    @Test
    void constructorNormalizesMinMaxAndReturnsEightCorners() {
        CuboidRegionShape shape = new CuboidRegionShape(5, 5, 5, 3, 3, 3, null);

        assertEquals(8, shape.cornerCenters().size());
        assertTrue(shape.cornerCenters().contains(new Vector(3.5, 3.5, 3.5)));
        assertTrue(shape.cornerCenters().contains(new Vector(5.5, 5.5, 5.5)));
    }

    @Test
    void edgeSamplingReturnsExpectedCountForSimpleCube() {
        CuboidRegionShape shape = new CuboidRegionShape(0, 0, 0, 1, 1, 1, Map.of());
        assertEquals(24, shape.sampleEdgePoints(1.0).size());
    }

    @Test
    void namedPointsArePreserved() {
        Map<String, Vector> named = Map.of("pos1", new Vector(1, 2, 3));
        CuboidRegionShape shape = new CuboidRegionShape(0, 0, 0, 1, 1, 1, named);
        assertEquals(named, shape.namedPoints());
    }
}
