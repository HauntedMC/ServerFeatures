package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import nl.hauntedmc.serverfeatures.features.invtools.model.InventoryKind;
import nl.hauntedmc.serverfeatures.features.invtools.model.InventorySnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Makes interrupted migration recovery reachable through the ordinary service preflight. */
public final class RecoverableOfflinePlayerDataStore implements OfflinePlayerDataStore {

    private static final String MIGRATION_BACKUP_SUFFIX = ".invtools-migration-backup";

    private final OfflinePlayerDataStore delegate;
    private final Path playerDataDirectory;

    public RecoverableOfflinePlayerDataStore(
            OfflinePlayerDataStore delegate,
            Path levelDirectory
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Path normalizedLevelDirectory = Objects.requireNonNull(levelDirectory, "levelDirectory")
                .toAbsolutePath()
                .normalize();
        this.playerDataDirectory = PaperPlayerDataLayout.playerDataDirectory(
                normalizedLevelDirectory
        );
    }

    @Override
    public boolean hasPlayerData(UUID playerId) throws IOException {
        Objects.requireNonNull(playerId, "playerId");
        if (delegate.hasPlayerData(playerId)) {
            return true;
        }
        Path backup = migrationBackup(playerId);
        return Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(backup);
    }

    @Override
    public OfflinePlayerData load(UUID playerId) throws IOException {
        return delegate.load(playerId);
    }

    @Override
    public Optional<UUID> resolvePlayerId(
            Optional<UUID> preferredPlayerId,
            String playerName
    ) throws IOException {
        return delegate.resolvePlayerId(preferredPlayerId, playerName);
    }

    @Override
    public void rememberPlayerIdentity(UUID playerId, String playerName) {
        delegate.rememberPlayerIdentity(playerId, playerName);
    }

    @Override
    public void save(
            OfflinePlayerData original,
            InventoryKind kind,
            InventorySnapshot changedSnapshot
    ) throws IOException {
        delegate.save(original, kind, changedSnapshot);
    }

    private Path migrationBackup(UUID playerId) throws IOException {
        Path backup = playerDataDirectory.resolve(
                playerId + ".dat" + MIGRATION_BACKUP_SUFFIX
        ).normalize();
        if (!backup.getParent().equals(playerDataDirectory)) {
            throw new IOException("Resolved migration backup escaped the playerdata directory");
        }
        return backup;
    }
}
