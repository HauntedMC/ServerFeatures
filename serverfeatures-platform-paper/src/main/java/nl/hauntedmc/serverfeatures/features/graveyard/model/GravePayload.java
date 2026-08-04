package nl.hauntedmc.serverfeatures.features.graveyard.model;

import java.util.List;
import java.util.Objects;

public record GravePayload(long revision, List<GraveItemEntry> entries, int remainingExperience) {
    public GravePayload {
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
        if (remainingExperience < 0) {
            throw new IllegalArgumentException("remainingExperience must be non-negative");
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty() && remainingExperience == 0;
    }

    public GravePayload next(List<GraveItemEntry> remainingEntries, int remainingXp) {
        return new GravePayload(revision + 1L, remainingEntries, remainingXp);
    }
}
