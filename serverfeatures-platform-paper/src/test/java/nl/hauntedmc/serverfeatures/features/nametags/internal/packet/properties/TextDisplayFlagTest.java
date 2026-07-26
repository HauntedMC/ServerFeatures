package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextDisplayFlagTest {

    @Test
    void bitValuesMatchProtocolMaskDefinitions() {
        assertEquals((byte) 0x01, TextDisplayFlag.HAS_SHADOW.getBit());
        assertEquals((byte) 0x02, TextDisplayFlag.IS_SEE_THROUGH.getBit());
        assertEquals((byte) 0x04, TextDisplayFlag.USE_DEFAULT_BG.getBit());
    }
}

