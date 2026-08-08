package nl.hauntedmc.serverfeatures.api.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global access point for the currently active CombatTag API.
 */
public final class CombatTags {

    private static final AtomicReference<CombatTagApi> SERVICE = new AtomicReference<>();

    private CombatTags() {
    }

    public static void bootstrap(@NotNull CombatTagApi service) {
        SERVICE.set(service);
    }

    public static void shutdown(@NotNull CombatTagApi service) {
        SERVICE.compareAndSet(service, null);
    }

    public static @NotNull CombatTagApi service() {
        CombatTagApi service = SERVICE.get();
        return service == null ? NoopCombatTagApi.INSTANCE : service;
    }

    private enum NoopCombatTagApi implements CombatTagApi {
        INSTANCE;

        @Override
        public boolean isTagged(UUID playerId) {
            return false;
        }

        @Override
        public Optional<CombatTagSnapshot> getTag(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public CombatTagResult tag(Player player, Entity opponent, CombatTagReason reason) {
            return CombatTagResult.INVALID;
        }

        @Override
        public boolean untag(Player player, CombatUntagReason reason) {
            return false;
        }
    }
}
