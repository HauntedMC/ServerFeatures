package nl.hauntedmc.serverfeatures.features.combattag.service;

import nl.hauntedmc.serverfeatures.api.capability.combat.CombatOpponent;
import nl.hauntedmc.serverfeatures.api.capability.combat.CombatTagApi;
import nl.hauntedmc.serverfeatures.api.capability.combat.CombatTagReason;
import nl.hauntedmc.serverfeatures.api.capability.combat.CombatTagSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Paper adapter exposing the internal CombatTag service through the platform-neutral public API. */
public final class CombatTagCapability implements CombatTagApi {

    private final CombatTagService service;

    public CombatTagCapability(CombatTagService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public boolean isTagged(UUID playerId) {
        return service.isTagged(playerId);
    }

    @Override
    public Optional<CombatTagSnapshot> getTag(UUID playerId) {
        return service.getTag(playerId).map(CombatTagCapability::snapshot);
    }

    private static CombatTagSnapshot snapshot(
            nl.hauntedmc.serverfeatures.api.combat.CombatTagSnapshot source
    ) {
        var opponent = source.opponent();
        return new CombatTagSnapshot(
                source.playerId(),
                new CombatOpponent(
                        opponent.uniqueId(),
                        opponent.entityType().getKey().toString(),
                        opponent.displayName(),
                        opponent.player()
                ),
                CombatTagReason.valueOf(source.reason().name()),
                source.taggedAt(),
                source.expiresAt(),
                source.remaining()
        );
    }
}
