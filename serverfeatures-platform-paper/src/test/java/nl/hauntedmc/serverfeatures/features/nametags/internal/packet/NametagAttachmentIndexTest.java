package nl.hauntedmc.serverfeatures.features.nametags.internal.packet;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NametagAttachmentIndexTest {

    @Test
    void exposesCurrentPacketVisibilityForReconciliation() {
        NametagAttachmentIndex index = new NametagAttachmentIndex();
        UUID viewerId = UUID.randomUUID();
        index.register(10, 99);

        assertFalse(index.isVisible(10, 99, viewerId));
        index.markVisible(10, viewerId);
        assertTrue(index.isVisible(10, 99, viewerId));
        assertFalse(index.isVisible(10, 98, viewerId));

        index.appendDestroyedNametags(viewerId, new int[]{10});
        assertFalse(index.isVisible(10, 99, viewerId));
    }

    @Test
    void visibleNametagIsAppendedAfterRealPassengers() {
        NametagAttachmentIndex index = new NametagAttachmentIndex();
        UUID viewerId = UUID.randomUUID();
        index.register(10, 99);
        index.markVisible(10, viewerId);

        assertArrayEquals(
                new int[]{20, 21, 99},
                index.appendNametagPassenger(viewerId, 10, new int[]{20, 21})
        );
    }

    @Test
    void passengerListIsNotCopiedWhenNoChangeIsRequired() {
        NametagAttachmentIndex index = new NametagAttachmentIndex();
        UUID viewerId = UUID.randomUUID();
        int[] passengers = {20, 21};

        assertSame(passengers, index.appendNametagPassenger(viewerId, 10, passengers));

        index.register(10, 99);
        index.markVisible(10, viewerId);
        int[] alreadyGuarded = {20, 99};
        assertSame(alreadyGuarded, index.appendNametagPassenger(viewerId, 10, alreadyGuarded));
    }

    @Test
    void ownerDestroyAlsoDestroysVisibleFakeEntity() {
        NametagAttachmentIndex index = new NametagAttachmentIndex();
        UUID viewerId = UUID.randomUUID();
        index.register(10, 99);
        index.markVisible(10, viewerId);

        assertArrayEquals(
                new int[]{5, 10, 99},
                index.appendDestroyedNametags(viewerId, new int[]{5, 10})
        );
    }

    @Test
    void ownerDestroyMarksAttachmentHiddenForLaterPassengerPackets() {
        NametagAttachmentIndex index = new NametagAttachmentIndex();
        UUID viewerId = UUID.randomUUID();
        index.register(10, 99);
        index.markVisible(10, viewerId);

        index.appendDestroyedNametags(viewerId, new int[]{10});
        int[] passengers = {20};

        assertSame(passengers, index.appendNametagPassenger(viewerId, 10, passengers));
    }

    @Test
    void destroyListIsNotCopiedWhenFakeEntityIsAlreadyPresent() {
        NametagAttachmentIndex index = new NametagAttachmentIndex();
        UUID viewerId = UUID.randomUUID();
        index.register(10, 99);
        index.markVisible(10, viewerId);
        int[] ids = {10, 99};

        assertSame(ids, index.appendDestroyedNametags(viewerId, ids));
    }
}
