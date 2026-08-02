package nl.hauntedmc.serverfeatures.features.graveyard.persistence;

import nl.hauntedmc.serverfeatures.features.graveyard.model.GraveItemEntry;
import nl.hauntedmc.serverfeatures.features.graveyard.model.GravePayload;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static nl.hauntedmc.serverfeatures.features.graveyard.GraveyardTestItemStacks.mockBinaryDeserialization;
import static nl.hauntedmc.serverfeatures.features.graveyard.GraveyardTestItemStacks.stack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GravePayloadCodecTest {
    private final GravePayloadCodec codec = new GravePayloadCodec(64, 1_000_000, 4_000_000);

    @Test
    void roundTripsValidatedItemsAndExperience() throws Exception {
        try (MockedStatic<ItemStack> ignored = mockBinaryDeserialization()) {
            GraveItemEntry entry = codec.createEntry(UUID.randomUUID(), 4, stack(Material.DIAMOND, 32));
            GravePayload source = new GravePayload(7L, List.of(entry), 125);

            EncodedGravePayload encoded = codec.encode(source);
            GravePayload decoded = codec.decode(encoded.bytes(), encoded.checksum());

            assertEquals(source.revision(), decoded.revision());
            assertEquals(source.remainingExperience(), decoded.remainingExperience());
            assertEquals(32, codec.decodeItem(decoded.entries().getFirst()).getAmount());
        }
    }

    @Test
    void rejectsDuplicateEntryIdentifiersAndChecksumMismatch() throws Exception {
        UUID entryId = UUID.randomUUID();
        try (MockedStatic<ItemStack> ignored = mockBinaryDeserialization()) {
            GraveItemEntry first = codec.createEntry(entryId, 0, stack(Material.STONE, 1));
            GraveItemEntry second = codec.createEntry(entryId, 1, stack(Material.DIRT, 1));
            GravePayload duplicate = new GravePayload(0L, List.of(first, second), 0);

            assertThrows(IOException.class, () -> codec.encode(duplicate));

            EncodedGravePayload encoded = codec.encode(new GravePayload(0L, List.of(first), 0));
            assertThrows(IOException.class, () -> codec.decode(encoded.bytes(), "0".repeat(64)));
        }
    }

    @Test
    void rejectsInvalidPreferredSlotsAndEmptyItems() {
        assertThrows(IOException.class, () -> codec.createEntry(
                UUID.randomUUID(),
                41,
                stack(Material.STONE, 1)
        ));
        assertThrows(IOException.class, () -> codec.createEntry(
                UUID.randomUUID(),
                0,
                stack(Material.AIR, 0)
        ));
    }
}
