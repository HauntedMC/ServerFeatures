package nl.hauntedmc.serverfeatures.features.invtools.persistence;

import java.io.IOException;
import java.util.UUID;

@FunctionalInterface
interface PlayerDataMigrationCheckpoint {

    PlayerDataMigrationCheckpoint NONE = (playerId, stage) -> {
    };

    void reached(UUID playerId, Stage stage) throws IOException;

    enum Stage {
        AFTER_BACKUP,
        BEFORE_REPLACE,
        AFTER_REPLACE
    }
}
