package nl.hauntedmc.serverfeatures.features.nametags.internal;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe registry for storing and retrieving active nametags.
 */
public class NametagRegistry {
    private final Map<UUID, Nametag> registry = new ConcurrentHashMap<>();

    public void register(Nametag tag) {
        registry.put(tag.getNametagOwnerId(), tag);
    }

    public Optional<Nametag> getNametag(UUID ownerId) {
        return Optional.ofNullable(registry.get(ownerId));
    }

    public Collection<Nametag> getAllNametags() {
        return registry.values();
    }

    public void unregister(UUID ownerId) {
        registry.remove(ownerId);
    }

    public void unregister(Nametag tag) {
        unregister(tag.getNametagOwnerId());
    }

    public Optional<Nametag> getNametagByEntityId(int entityId) {
        return registry.values().stream()
                .filter(nametag -> nametag.getEntityId() == entityId)
                .findFirst();
    }

}
