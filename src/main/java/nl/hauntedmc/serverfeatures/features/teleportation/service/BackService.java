package nl.hauntedmc.serverfeatures.features.teleportation.service;

import nl.hauntedmc.serverfeatures.features.teleportation.integration.EssentialsHook;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records a player's last location for /back.
 * Uses Essentials when available, but also keeps a local fallback.
 */
public interface BackService {
    void recordBackLocation(UUID playerId, Location from);

    static BackService createWithEssentialsFallback() {
        return new BackService() {
            private final Map<UUID, Location> local = new ConcurrentHashMap<>();
            private final EssentialsHook essentials = new EssentialsHook();

            @Override
            public void recordBackLocation(UUID playerId, Location from) {
                // Essentials is triggered separately in TeleportService.
                if (from != null) local.put(playerId, from.clone());
            }

            // Future: expose getBackLocation(...)
        };
    }
}
