package nl.hauntedmc.serverfeatures.features.invtools.service;

import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.NbtOfflinePlayerDataStore;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PlayerDataMigrationObserver;

import java.time.Duration;
import java.util.Objects;

public final class InvToolsServiceFactory {

    private static final int MAX_LOGIN_BARRIER_SECONDS = 30;

    private InvToolsServiceFactory() {
    }

    public static InvToolsService create(
            InvTools feature,
            PlayerDataMigrationObserver migrationObserver
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(migrationObserver, "migrationObserver");
        return new InvToolsService(
                feature,
                new NbtOfflinePlayerDataStore(
                        feature.getPlugin().getServer().getLevelDirectory(),
                        feature.getPlugin().getDataFolder().toPath(),
                        migrationObserver
                ),
                Duration.ofSeconds(Math.clamp(feature.getConfigHandler().get(
                        "offline_io_timeout_seconds",
                        Integer.class,
                        10
                ), 1, MAX_LOGIN_BARRIER_SECONDS)),
                feature.getConfigHandler().get("audit_edits", Boolean.class, true),
                Math.max(1, feature.getConfigHandler().get(
                        "max_offline_sessions",
                        Integer.class,
                        4
                ))
        );
    }
}
