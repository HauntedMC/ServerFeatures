package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidOutlineSamplerTest {

    @Test
    void samplesAllEdgesOfSmallCuboidWithoutDuplicates() {
        CuboidBounds bounds = new CuboidBounds(0, 0, 0, 2, 2, 2);

        Set<BlockPoint> points = CuboidOutlineSampler.sample(
                bounds, new BlockPoint(1, 1, 1), 16, 1, 1000);

        assertEquals(20, points.size());
        assertTrue(points.containsAll(bounds.corners()));
        assertFalse(points.contains(new BlockPoint(1, 1, 1)));
    }

    @Test
    void onlySamplesVisibleEdgeSegments() {
        CuboidBounds bounds = new CuboidBounds(-10_000, 0, 0, 10_000, 10, 10);

        Set<BlockPoint> points = CuboidOutlineSampler.sample(
                bounds, new BlockPoint(0, 0, 0), 32, 1, 1000);

        assertTrue(points.stream().allMatch(point -> Math.abs(point.x()) <= 32));
        assertTrue(points.size() < 400);
    }

    @Test
    void increasesSamplingStepToRespectBlockBudget() {
        CuboidBounds bounds = new CuboidBounds(-1000, -1000, -1000, 1000, 1000, 1000);

        Set<BlockPoint> points = CuboidOutlineSampler.sample(
                bounds, new BlockPoint(1000, 1000, 1000), 512, 1, 64);

        assertTrue(points.size() <= 64);
        assertTrue(points.contains(new BlockPoint(1000, 1000, 1000)));
    }

    @Test
    void normalizesReversedBounds() {
        CuboidBounds bounds = new CuboidBounds(4, 5, 6, 1, 2, 3);

        assertEquals(1, bounds.minX());
        assertEquals(2, bounds.minY());
        assertEquals(3, bounds.minZ());
        assertEquals(4, bounds.maxX());
        assertEquals(5, bounds.maxY());
        assertEquals(6, bounds.maxZ());
    }

    @Test
    void visibilityUsesBoundedAxisDistancesWithoutOverflow() {
        assertFalse(CuboidOutlineSampler.isVisible(
                new BlockPoint(Integer.MIN_VALUE, 0, 0),
                new BlockPoint(Integer.MAX_VALUE, 0, 0),
                128
        ));
    }
}
