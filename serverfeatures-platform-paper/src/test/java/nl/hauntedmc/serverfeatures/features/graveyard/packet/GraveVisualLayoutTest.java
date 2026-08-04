package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveVisualLayoutTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void graveBedHasHumanReadableProportionsAndStaysCentered() {
        GraveVisualLayout.Part base = GraveVisualLayout.BASE;

        assertTrue(base.depth() >= base.width() * 2.0f);
        assertEquals(-base.maximumX(), base.x(), EPSILON);
        assertEquals(-base.maximumZ(), base.z(), EPSILON);
    }

    @Test
    void memorialFormsAReadableCrossAtTheHeadOfTheBed() {
        GraveVisualLayout.Part base = GraveVisualLayout.BASE;
        GraveVisualLayout.Part stem = GraveVisualLayout.HEADSTONE_STEM;
        GraveVisualLayout.Part crossbar = GraveVisualLayout.HEADSTONE_CROSSBAR;

        assertTrue(stem.height() >= 1.5f);
        assertTrue(crossbar.width() >= stem.width() * 3.0f);
        assertTrue(crossbar.x() < stem.x());
        assertTrue(crossbar.maximumX() > stem.maximumX());
        assertTrue(crossbar.y() > stem.y() + stem.height() / 2.0f);
        assertTrue(crossbar.maximumY() < stem.maximumY());
        assertTrue(stem.z() > 0.0f);
        assertTrue(stem.maximumZ() <= base.maximumZ());
    }

    @Test
    void labelAndInteractionBoundsCoverTheCompleteDesign() {
        float memorialTop = GraveVisualLayout.HEADSTONE_STEM.maximumY();
        float bedLength = GraveVisualLayout.BASE.depth();

        assertTrue(GraveVisualLayout.TEXT_OFFSET_Y > memorialTop);
        assertTrue(GraveVisualLayout.INTERACTION_WIDTH > bedLength);
        assertTrue(GraveVisualLayout.INTERACTION_HEIGHT > memorialTop);
    }
}
