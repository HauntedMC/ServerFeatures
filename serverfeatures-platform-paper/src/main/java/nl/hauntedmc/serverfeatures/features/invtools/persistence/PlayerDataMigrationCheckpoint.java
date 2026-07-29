package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@FunctionalInterface
interface PlayerDataMigrationCheckpoint {

    PlayerDataMigrationCheckpoint NONE = (playerId, stage) -> {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(stage, "stage");
    };

    void reached(UUID playerId, Stage stage) throws IOException;

    enum Stage {
        AFTER_BACKUP,
        BEFORE_REPLACE,
        AFTER_REPLACE
    }
}
