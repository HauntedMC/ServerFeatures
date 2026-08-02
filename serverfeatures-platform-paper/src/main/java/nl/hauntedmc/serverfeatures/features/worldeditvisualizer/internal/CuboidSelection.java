package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import java.util.Objects;
import java.util.UUID;

record CuboidSelection(
        UUID worldId,
        CuboidBounds bounds,
        BlockPoint pos1,
        BlockPoint pos2
) {

    CuboidSelection {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(pos1, "pos1");
        Objects.requireNonNull(pos2, "pos2");
    }
}
