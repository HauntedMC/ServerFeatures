package nl.hauntedmc.serverfeatures.features.worldeditvisualizer.internal;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

record RenderState(UUID worldId, Map<VisualKey, VirtualEntity> entities) {

    RenderState {
        Objects.requireNonNull(worldId, "worldId");
        entities = Map.copyOf(entities);
    }

    static RenderState empty(UUID worldId) {
        return new RenderState(worldId, Map.of());
    }
}
