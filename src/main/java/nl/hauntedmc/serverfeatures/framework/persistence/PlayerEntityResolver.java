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

    public PlayerRepository.PlayerIdentity getOrCreateActiveIdentity(UUID uuid, String username) {
        Objects.requireNonNull(uuid, "uuid");
        return playerRepository.getOrCreateActiveIdentity(uuid.toString(), requireUsername(username));
    }

    public PlayerEntity resolveManaged(Session session, UUID uuid, String usernameHint) {
        if (session == null || uuid == null) {
            return null;
        }
        String normalizedUsername = normalizeUsername(usernameHint);
        Optional<PlayerRepository.PlayerIdentity> identity = normalizedUsername == null
                ? playerRepository.findIdentityByUUID(uuid.toString())
                : Optional.of(playerRepository.getOrCreateActiveIdentity(uuid.toString(), normalizedUsername));
        return identity.map(PlayerRepository.PlayerIdentity::id)
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

    private static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String requireUsername(String username) {
        String normalized = normalizeUsername(username);
        if (normalized == null) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return normalized;
    }
}
