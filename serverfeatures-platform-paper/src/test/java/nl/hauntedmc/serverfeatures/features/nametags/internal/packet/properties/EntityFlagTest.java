package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityFlagTest {

    @Test
    void bitValuesMatchExpectedMasks() {
        assertEquals((byte) 0x01, EntityFlag.ON_FIRE.getBit());
        assertEquals((byte) 0x02, EntityFlag.PRESSING_SNEAK.getBit());
        assertEquals((byte) 0x08, EntityFlag.SPRINTING.getBit());
        assertEquals((byte) 0x10, EntityFlag.SWIMMING.getBit());
        assertEquals((byte) 0x20, EntityFlag.INVISIBLE.getBit());
        assertEquals((byte) 0x40, EntityFlag.GLOWING.getBit());
        assertEquals((byte) 0x80, EntityFlag.FLYING.getBit());
    }
}

