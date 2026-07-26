package nl.hauntedmc.serverfeatures.features.nametags.internal.packet.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillboardConstraintsTest {

    @Test
    void fromValueReturnsMatchingEnumOrFixedFallback() {
        assertEquals(BillboardConstraints.FIXED, BillboardConstraints.fromValue((byte) 0));
        assertEquals(BillboardConstraints.VERTICAL, BillboardConstraints.fromValue((byte) 1));
        assertEquals(BillboardConstraints.HORIZONTAL, BillboardConstraints.fromValue((byte) 2));
        assertEquals(BillboardConstraints.CENTER, BillboardConstraints.fromValue((byte) 3));
        assertEquals(BillboardConstraints.FIXED, BillboardConstraints.fromValue((byte) 127));
    }
}

