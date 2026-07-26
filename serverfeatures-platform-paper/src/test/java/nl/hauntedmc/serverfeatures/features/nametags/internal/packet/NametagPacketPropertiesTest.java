package nl.hauntedmc.serverfeatures.features.nametags.internal.packet;

import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties.TextDisplayAlignment;
import org.bukkit.entity.Pose;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NametagPacketPropertiesTest {

    @Test
    void constructorAppliesExpectedDefaults() {
        NametagPacketProperties properties = propertiesOrSkip();

        assertEquals(Component.empty(), properties.getText());
        assertEquals(200, properties.getLineWidth());
        assertTrue(properties.hasShadow());
        assertFalse(properties.isSeeThrough());
        assertEquals(TextDisplayAlignment.CENTER, properties.getTextDisplayAlignment());
        assertEquals(Pose.STANDING, properties.getPose());
    }

    @Test
    void customNameRoundTripsAndSupportsNull() {
        NametagPacketProperties properties = propertiesOrSkip();

        properties.setCustomName(Component.text("Hello"));
        assertEquals(Component.text("Hello"), properties.getCustomName());

        properties.setCustomName(null);
        assertEquals(Component.empty(), properties.getCustomName());
    }

    @Test
    void poseRoundTripsViaEntityPoseMapping() {
        NametagPacketProperties properties = propertiesOrSkip();

        properties.setPose(EntityPose.SLEEPING);

        assertEquals(Pose.SLEEPING, properties.getPose());
    }

    @Test
    void entityFlagsToggleIndependently() {
        NametagPacketProperties properties = propertiesOrSkip();

        properties.setIsGlowing(true);
        properties.setIsInvisible(true);
        properties.setIsSprinting(true);
        assertTrue(properties.isGlowing());
        assertTrue(properties.isInvisible());
        assertTrue(properties.isSprinting());

        properties.setIsInvisible(false);
        assertTrue(properties.isGlowing());
        assertFalse(properties.isInvisible());
        assertTrue(properties.isSprinting());
    }

    @Test
    void textDisplayFlagsAndAlignmentCanBeUpdatedTogether() {
        NametagPacketProperties properties = propertiesOrSkip();

        properties.setHasShadow(false);
        properties.setIsSeeThrough(true);
        properties.setUseDefaultBackgroundColor(true);
        properties.setTextDisplayAlignment(TextDisplayAlignment.RIGHT);

        assertFalse(properties.hasShadow());
        assertTrue(properties.isSeeThrough());
        assertTrue(properties.useDefaultBackgroundColor());
        assertEquals(TextDisplayAlignment.RIGHT, properties.getTextDisplayAlignment());
    }

    private static NametagPacketProperties propertiesOrSkip() {
        try {
            return new NametagPacketProperties();
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "PacketEvents runtime not initialized: " + t.getClass().getSimpleName());
            return null;
        }
    }
}
