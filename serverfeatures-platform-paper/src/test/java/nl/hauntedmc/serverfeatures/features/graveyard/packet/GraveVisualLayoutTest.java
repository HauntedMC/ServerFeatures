package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveVisualLayoutTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void graveBedHasHumanReadableProportionsAndStaysCentered() {
        GraveVisualLayout.Part base = GraveVisualLayout.BASE;

        assertTrue(base.scale().getZ() >= base.scale().getX() * 2.0f);
        assertEquals(-base.maximumX(), base.translation().getX(), EPSILON);
        assertEquals(-base.maximumZ(), base.translation().getZ(), EPSILON);
    }

    @Test
    void memorialFormsAReadableCrossAtTheHeadOfTheBed() {
        GraveVisualLayout.Part base = GraveVisualLayout.BASE;
        GraveVisualLayout.Part stem = GraveVisualLayout.HEADSTONE_STEM;
        GraveVisualLayout.Part crossbar = GraveVisualLayout.HEADSTONE_CROSSBAR;

        assertTrue(stem.scale().getY() >= 1.5f);
        assertTrue(crossbar.scale().getX() >= stem.scale().getX() * 3.0f);
        assertTrue(crossbar.translation().getX() < stem.translation().getX());
        assertTrue(crossbar.maximumX() > stem.maximumX());
        assertTrue(crossbar.translation().getY() > stem.translation().getY() + stem.scale().getY() / 2.0f);
        assertTrue(crossbar.maximumY() < stem.maximumY());
        assertTrue(stem.translation().getZ() > 0.0f);
        assertTrue(stem.maximumZ() <= base.maximumZ());
    }

    @Test
    void labelAndInteractionBoundsCoverTheCompleteDesign() {
        float memorialTop = GraveVisualLayout.HEADSTONE_STEM.maximumY();
        float bedLength = GraveVisualLayout.BASE.scale().getZ();

        assertTrue(GraveVisualLayout.TEXT_OFFSET_Y > memorialTop);
        assertTrue(GraveVisualLayout.INTERACTION_WIDTH > bedLength);
        assertTrue(GraveVisualLayout.INTERACTION_HEIGHT > memorialTop);
    }
}
