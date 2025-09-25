package nl.hauntedmc.serverfeatures.features.nametags.internal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
}
