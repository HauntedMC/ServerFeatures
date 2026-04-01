package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextDisplayAlignmentTest {

    @Test
    void fromValueMatchesProtocolRules() {
        assertEquals(TextDisplayAlignment.CENTER, TextDisplayAlignment.fromValue(0));
        assertEquals(TextDisplayAlignment.LEFT, TextDisplayAlignment.fromValue(1));
        assertEquals(TextDisplayAlignment.RIGHT, TextDisplayAlignment.fromValue(2));
        assertEquals(TextDisplayAlignment.LEFT, TextDisplayAlignment.fromValue(3));
        assertEquals(TextDisplayAlignment.CENTER, TextDisplayAlignment.fromValue(99));
    }
}

