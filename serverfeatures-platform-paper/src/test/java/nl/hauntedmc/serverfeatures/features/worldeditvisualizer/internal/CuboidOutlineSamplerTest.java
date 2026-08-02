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

        Set<VisualPoint> points = CuboidOutlineSampler.sample(
                bounds, VisualPoint.of(1.5, 1.5, 1.5), 16, 1.0, 1000);

        assertEquals(20, points.size());
        assertTrue(points.containsAll(bounds.corners()));
        assertFalse(points.contains(VisualPoint.of(1.5, 1.5, 1.5)));
    }

    @Test
    void supportsStableFractionalEdgeSpacing() {
        CuboidBounds bounds = new CuboidBounds(0, 0, 0, 1, 1, 1);

        Set<VisualPoint> points = CuboidOutlineSampler.sample(
                bounds, VisualPoint.of(1, 1, 1), 16, 0.25, 1000);

        assertTrue(points.contains(VisualPoint.of(0.75, 0.5, 0.5)));
        assertTrue(points.contains(VisualPoint.of(1.25, 1.5, 1.5)));
    }

    @Test
    void onlySamplesVisibleEdgeSegments() {
        CuboidBounds bounds = new CuboidBounds(-10_000, 0, 0, 10_000, 10, 10);

        Set<VisualPoint> points = CuboidOutlineSampler.sample(
                bounds, VisualPoint.of(0.5, 0.5, 0.5), 32, 0.25, 1000);

        assertTrue(points.stream().allMatch(point -> Math.abs(point.x() - 0.5) <= 32.01));
        assertTrue(points.size() <= 1000);
    }

    @Test
    void increasesSamplingStepToRespectEntityBudget() {
        CuboidBounds bounds = new CuboidBounds(-1000, -1000, -1000, 1000, 1000, 1000);

        Set<VisualPoint> points = CuboidOutlineSampler.sample(
                bounds, VisualPoint.of(1000.5, 1000.5, 1000.5), 512, 0.05, 64);

        assertTrue(points.size() <= 64);
        assertTrue(points.contains(VisualPoint.of(1000.5, 1000.5, 1000.5)));
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
    void visibilityHandlesExtremeCoordinatesWithoutOverflow() {
        assertFalse(CuboidOutlineSampler.isVisible(
                VisualPoint.blockCenter(Integer.MIN_VALUE, 0, 0),
                VisualPoint.blockCenter(Integer.MAX_VALUE, 0, 0),
                128
        ));
    }

    @Test
    void extremeCoordinatesRemainBounded() {
        CuboidBounds bounds = new CuboidBounds(
                Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0);

        Set<VisualPoint> points = CuboidOutlineSampler.sample(
                bounds, VisualPoint.blockCenter(Integer.MAX_VALUE, 0, 0), 16, 0.25, 64);

        assertTrue(points.size() <= 64);
    }

    @Test
    void defensiveCapTerminatesWithTinyBudget() {
        CuboidBounds bounds = new CuboidBounds(-100, -100, -100, 100, 100, 100);

        Set<VisualPoint> points = CuboidOutlineSampler.sample(
                bounds, VisualPoint.of(100.5, 100.5, 100.5), 100, 0.05, 1);

        assertEquals(1, points.size());
    }
}
