package nl.hauntedmc.serverfeatures.features.invtools.service;

import de.tr7zw.changeme.nbtapi.utils.DataFixerUtil;
import nl.hauntedmc.serverfeatures.features.invtools.InvTools;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.NbtOfflinePlayerDataStore;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PaperPlayerDataConverter;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.PlayerDataMigrationObserver;
import nl.hauntedmc.serverfeatures.features.invtools.persistence.RecoverableOfflinePlayerDataStore;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Path;
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
        verifyRuntimeCompatibility();

        Path levelDirectory = feature.getPlugin().getServer().getLevelDirectory();
        NbtOfflinePlayerDataStore playerDataStore = new NbtOfflinePlayerDataStore(
                levelDirectory,
                feature.getPlugin().getDataFolder().toPath(),
                migrationObserver
        );
        return new InvToolsService(
                feature,
                new RecoverableOfflinePlayerDataStore(playerDataStore, levelDirectory),
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

    private static void verifyRuntimeCompatibility() {
        verifyRuntimeDataVersion();
        try {
            new PaperPlayerDataConverter().verifyAvailable();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Paper does not expose the PLAYER data-fixer bridge required by InvTools",
                    exception
            );
        }
    }

    @SuppressWarnings("deprecation")
    private static void verifyRuntimeDataVersion() {
        int paperDataVersion = Bukkit.getUnsafe().getDataVersion();
        int nbtApiDataVersion = DataFixerUtil.getCurrentVersion();
        if (paperDataVersion <= 0) {
            throw new IllegalStateException("Paper reported an invalid runtime DataVersion");
        }
        if (paperDataVersion != nbtApiDataVersion) {
            throw new IllegalStateException(
                    "NBT-API DataVersion " + nbtApiDataVersion
                            + " does not match Paper runtime DataVersion " + paperDataVersion
            );
        }
    }
}
