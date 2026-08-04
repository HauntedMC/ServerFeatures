package nl.hauntedmc.serverfeatures.features.graveyard.packet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GravePacketIdentityTest {
    @Test
    void cleanupCollectionsContainEveryVisualAndInteractionEntity() {
        GravePacketIdentity identity = new GravePacketIdentity(
                7L,
                10,
                UUID.randomUUID(),
                20,
                UUID.randomUUID(),
                30,
                UUID.randomUUID(),
                40,
                UUID.randomUUID(),
                50,
                UUID.randomUUID()
        );

        assertArrayEquals(new int[] {10, 20, 30, 40, 50}, identity.entityIds());
        assertEquals(List.of(10, 20, 30, 40, 50), identity.entityIdList());
    }
}
