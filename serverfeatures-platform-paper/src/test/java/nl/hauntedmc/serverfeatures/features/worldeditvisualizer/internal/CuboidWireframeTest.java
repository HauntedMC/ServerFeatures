package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidWireframeTest {

    @Test
    void oneBlockSelectionUsesOuterBlockBoundaries() {
        CuboidWireframe wireframe = CuboidWireframe.fromInclusive(4, 5, 6, 4, 5, 6);

        assertEquals(12, wireframe.edges().size());
        assertEquals(8, wireframe.corners().size());
        assertTrue(wireframe.corners().contains(new CuboidWireframe.Point(4.0d, 5.0d, 6.0d)));
        assertTrue(wireframe.corners().contains(new CuboidWireframe.Point(5.0d, 6.0d, 7.0d)));
        assertTrue(wireframe.edges().stream().allMatch(edge -> edge.length() == 1.0d));
    }

    @Test
    void rectangularSelectionAlwaysUsesTwelveEdges() {
        CuboidWireframe wireframe = CuboidWireframe.fromInclusive(-2, 10, 4, 7, 12, 8);
        Map<CuboidWireframe.Axis, Long> counts = new EnumMap<>(CuboidWireframe.Axis.class);
        for (CuboidWireframe.Axis axis : CuboidWireframe.Axis.values()) {
            counts.put(axis, wireframe.edges().stream().filter(edge -> edge.axis() == axis).count());
        }

        assertEquals(12, wireframe.edges().size());
        assertEquals(4L, counts.get(CuboidWireframe.Axis.X));
        assertEquals(4L, counts.get(CuboidWireframe.Axis.Y));
        assertEquals(4L, counts.get(CuboidWireframe.Axis.Z));
        assertTrue(wireframe.edges().stream()
                .filter(edge -> edge.axis() == CuboidWireframe.Axis.X)
                .allMatch(edge -> edge.length() == 10.0d));
        assertTrue(wireframe.edges().stream()
                .filter(edge -> edge.axis() == CuboidWireframe.Axis.Y)
                .allMatch(edge -> edge.length() == 3.0d));
        assertTrue(wireframe.edges().stream()
                .filter(edge -> edge.axis() == CuboidWireframe.Axis.Z)
                .allMatch(edge -> edge.length() == 5.0d));
    }

    @Test
    void inputOrderDoesNotChangeGeometry() {
        CuboidWireframe forward = CuboidWireframe.fromInclusive(-3, 1, 9, 5, 6, 11);
        CuboidWireframe reversed = CuboidWireframe.fromInclusive(5, 6, 11, -3, 1, 9);

        assertEquals(forward.edges(), reversed.edges());
        assertEquals(forward.corners(), reversed.corners());
    }

    @Test
    void rendererHasAConstantEntityCeiling() {
        assertEquals(24, PacketVisualizationRenderer.MAX_ENTITY_COUNT);
    }
}
