package nl.hauntedmc.serverfeatures.framework.persistence;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import org.hibernate.Session;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerEntityResolver {

    private final PlayerRepository playerRepository;

    public PlayerEntityResolver(DataRegistry dataRegistry) {
        this(Objects.requireNonNull(dataRegistry, "dataRegistry").getPlayerRepository());
    }

    public PlayerEntityResolver(PlayerRepository playerRepository) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
    }

    public Optional<PlayerEntity> findByUuid(String uuid) {
        return playerRepository.findByUUID(uuid);
    }

    public Optional<PlayerRepository.PlayerIdentity> findIdentityByUuid(String uuid) {
        return playerRepository.findIdentityByUUID(uuid);
    }

    public PlayerEntity resolveManaged(Session session, UUID uuid, String usernameHint) {
        if (session == null || uuid == null) {
            return null;
        }
        String playerUuid = uuid.toString();
        return playerRepository.getActiveIdentity(playerUuid)
                .or(() -> playerRepository.findIdentityByUUID(playerUuid))
                .map(PlayerRepository.PlayerIdentity::id)
                .filter(playerId -> playerId != null && playerId > 0)
                .map(playerId -> session.getReference(PlayerEntity.class, playerId))
                .orElse(null);
    }

    public PlayerEntity resolveManagedById(Session session, Long playerId) {
        if (session == null || playerId == null || playerId <= 0) {
            return null;
        }
        return session.getReference(PlayerEntity.class, playerId);
    }

}
