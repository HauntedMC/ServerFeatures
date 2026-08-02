package nl.hauntedmc.serverfeatures.features.graveyard.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record GraveItemEntry(UUID entryId, int preferredSlot, byte[] serializedItem) {
    public GraveItemEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(serializedItem, "serializedItem");
        serializedItem = Arrays.copyOf(serializedItem, serializedItem.length);
    }

    @Override
    public byte[] serializedItem() {
        return Arrays.copyOf(serializedItem, serializedItem.length);
    }
}
