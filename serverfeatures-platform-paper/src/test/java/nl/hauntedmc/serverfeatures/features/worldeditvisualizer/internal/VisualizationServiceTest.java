package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualizationServiceTest {

    @Test
    void samplesEveryUniqueWireframeBlockForSmallCuboid() {
        Set<VisualizationService.BlockPosition> points = VisualizationService.CuboidWireframe.sample(
                BlockVector3.at(0, 0, 0), BlockVector3.at(2, 2, 2), 1, 128
        );

        assertEquals(20, points.size());
        assertTrue(points.contains(new VisualizationService.BlockPosition(0, 0, 0)));
        assertTrue(points.contains(new VisualizationService.BlockPosition(2, 2, 2)));
        assertTrue(points.contains(new VisualizationService.BlockPosition(1, 0, 0)));
    }

    @Test
    void collapsesDegenerateSingleBlockSelection() {
        Set<VisualizationService.BlockPosition> points = VisualizationService.CuboidWireframe.sample(
                BlockVector3.at(4, 5, 6), BlockVector3.at(4, 5, 6), 1, 128
        );

        assertEquals(Set.of(new VisualizationService.BlockPosition(4, 5, 6)), points);
    }

    @Test
    void dynamicallyIncreasesSamplingStepToRespectBudget() {
        Set<VisualizationService.BlockPosition> points = VisualizationService.CuboidWireframe.sample(
                BlockVector3.at(-10_000, -64, -10_000),
                BlockVector3.at(10_000, 320, 10_000),
                1,
                64
        );

        assertTrue(points.size() <= 64);
        assertTrue(points.contains(new VisualizationService.BlockPosition(-10_000, -64, -10_000)));
        assertTrue(points.contains(new VisualizationService.BlockPosition(10_000, 320, 10_000)));
    }
}
